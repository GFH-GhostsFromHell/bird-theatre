package ca.gfh.birdtheatre;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stable-channel Windows update mechanism. Only a manifest signed by our pinned
 * Ed25519 key can authorize execution of a downloaded MSI.
 *
 * Builds lacking a configured publisher key or a jpackage launcher fail closed.
 */
public final class UpdateService {
    static final String MANIFEST_URL =
        "https://github.com/GFH-GhostsFromHell/bird-theatre/releases/latest/download/BirdTheatre-update.json";
    static final String RELEASE_PREFIX =
        "https://github.com/GFH-GhostsFromHell/bird-theatre/releases/download/windows-v";
    static final String ASSET_NAME = "BirdTheatre-Windows-Setup.msi";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final long MAX_MSI_BYTES = 250L * 1024 * 1024;
    private static final long UPDATE_INTERVAL_HOURS = 4;

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "bird-theatre-update-check");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean checking = new AtomicBoolean(false);
    private volatile Path stagedInstaller;
    private volatile String stagedVersion;
    private volatile String stagedSha256;
    private volatile Consumer<String> onStatus = ignored -> { };
    private volatile Runnable onStaged = () -> { };

    public String currentVersion() {
        return System.getProperty("birdtheatre.version", "0.1.0");
    }

    public boolean available() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows")
            && System.getProperty("jpackage.app-path", "").toLowerCase().endsWith(".exe")
            && !System.getProperty("birdtheatre.updatePublicKey", "").isBlank();
    }

    public void start(Consumer<String> status, Runnable staged) {
        onStatus = Objects.requireNonNull(status);
        onStaged = Objects.requireNonNull(staged);
        if (!available()) {
            status.accept("Updates: installer publishing/signing not configured");
            return;
        }
        status.accept("Automatic updates enabled · v" + currentVersion());
        executor.scheduleWithFixedDelay(this::safeCheck, 8, UPDATE_INTERVAL_HOURS, TimeUnit.HOURS);
    }

    public void checkNow() {
        if (!available()) {
            onStatus.accept("Updates unavailable until this app is installed with a trusted release key");
            return;
        }
        executor.execute(this::safeCheck);
    }

    public boolean isStaged() { return stagedInstaller != null; }
    public String stagedVersion() { return stagedVersion; }

    private void safeCheck() {
        if (!checking.compareAndSet(false, true) || stagedInstaller != null) return;
        try {
            onStatus.accept("Checking for Bird Theatre updates…");
            byte[] manifestBytes = fetchSmall(URI.create(MANIFEST_URL), 8_192);
            byte[] signatureBase64 = fetchSmall(URI.create(MANIFEST_URL + ".sig"), 4_096);
            PublicKey key = readPublicKey(System.getProperty("birdtheatre.updatePublicKey"));
            Manifest manifest = verifyManifest(manifestBytes, signatureBase64, key);
            if (compareVersions(manifest.version(), currentVersion()) <= 0) {
                onStatus.accept("Bird Theatre is up to date · v" + currentVersion());
                return;
            }
            onStatus.accept("Downloading verified update " + manifest.version() + "…");
            Path installer = downloadAndVerify(manifest);
            stagedSha256 = manifest.sha256();
            stagedInstaller = installer;
            stagedVersion = manifest.version();
            onStatus.accept("Update " + stagedVersion + " ready · installs automatically when you close the app");
            onStaged.run();
        } catch (NoReleaseException missing) {
            onStatus.accept("No signed desktop release published yet · v" + currentVersion());
        } catch (Exception error) {
            log("Update check failed: " + error.getMessage());
            onStatus.accept("Update check unavailable · app continues normally");
        } finally {
            checking.set(false);
        }
    }

    private byte[] fetchSmall(URI uri, int maxBytes) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT)
            .header("User-Agent", "BirdTheatreDesktop/" + currentVersion())
            .header("Accept", "application/octet-stream")
            .GET().build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 404) throw new NoReleaseException();
        if (response.statusCode() != 200) throw new IOException("Update endpoint HTTP " + response.statusCode());
        if (!"https".equalsIgnoreCase(response.uri().getScheme()))
            throw new SecurityException("Update response was not HTTPS");
        if (response.body().length > maxBytes) throw new SecurityException("Update metadata exceeded size limit");
        return response.body();
    }

    static PublicKey readPublicKey(String b64) throws Exception {
        byte[] der = Base64.getDecoder().decode(b64.trim());
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
    }

    static Manifest verifyManifest(byte[] json, byte[] encodedSignature, PublicKey key) throws Exception {
        byte[] signature = Base64.getDecoder().decode(new String(encodedSignature, StandardCharsets.US_ASCII).trim());
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key);
        verifier.update(json);
        if (!verifier.verify(signature)) throw new SecurityException("Release manifest signature is invalid");
        String payload = new String(json, StandardCharsets.UTF_8);
        String version = required(payload, "version", "[0-9]+\\.[0-9]+\\.[0-9]+", 20);
        String url = required(payload, "url", "https://[^\"\\\\\\s]+", 500);
        String sha = required(payload, "sha256", "[a-fA-F0-9]{64}", 64).toLowerCase();
        URI address = URI.create(url);
        if (!url.equals(RELEASE_PREFIX + version + "/" + ASSET_NAME)
            || !"https".equalsIgnoreCase(address.getScheme())
            || address.getRawQuery() != null || address.getFragment() != null) {
            throw new SecurityException("Manifest contains an unexpected download destination");
        }
        return new Manifest(version, url, sha);
    }

    private static String required(String payload, String key, String valueRegex, int limit) throws IOException {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"(" + valueRegex + ")\\\"");
        Matcher matcher = pattern.matcher(payload);
        if (!matcher.find()) throw new IOException("Signed manifest missing valid " + key);
        String result = matcher.group(1);
        if (result.length() > limit) throw new IOException("Manifest field too long: " + key);
        return result;
    }

    private Path downloadAndVerify(Manifest manifest) throws Exception {
        Path destination = updateDirectory();
        Path part = destination.resolve("BirdTheatre-" + manifest.version() + ".part");
        Path installer = destination.resolve("BirdTheatre-" + manifest.version() + ".msi");
        Files.deleteIfExists(part);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(manifest.url()))
                .timeout(Duration.ofMinutes(7))
                .header("User-Agent", "BirdTheatreDesktop/" + currentVersion())
                .GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200 || !"https".equalsIgnoreCase(response.uri().getScheme())) {
                response.body().close();
                throw new IOException("MSI download failed: HTTP " + response.statusCode());
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long count = 0;
            try (InputStream stream = response.body(); var output = Files.newOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    count += read;
                    if (count > MAX_MSI_BYTES) throw new SecurityException("Installer exceeds maximum size");
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            if (count < 1024) throw new SecurityException("Downloaded MSI is unexpectedly small");
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                    manifest.sha256().getBytes(StandardCharsets.US_ASCII)))
                throw new SecurityException("Installer SHA-256 does not match signed release manifest");
            byte[] magic = new byte[8];
            try (InputStream stream = Files.newInputStream(part)) {
                if (stream.read(magic) != 8) throw new SecurityException("Invalid MSI header");
            }
            if (!MessageDigest.isEqual(magic,
                new byte[]{(byte)0xd0,(byte)0xcf,0x11,(byte)0xe0,(byte)0xa1,(byte)0xb1,0x1a,(byte)0xe1}))
                throw new SecurityException("Downloaded file is not an MSI container");
            Files.move(part, installer, StandardCopyOption.REPLACE_EXISTING);
            log("Verified installer for " + manifest.version() + ": " + installer);
            return installer;
        } catch (Exception failure) {
            Files.deleteIfExists(part);
            throw failure;
        }
    }

    /** Spawn outside the installed application directory. The helper waits for this JVM to exit. */
    public void installStagedAfterExit() {
        Path msi = stagedInstaller;
        String launcher = System.getProperty("jpackage.app-path", "");
        if (msi == null || !Files.isRegularFile(msi) || launcher.isBlank()) return;
        try {
            Path folder = updateDirectory();
            Path script = folder.resolve("apply-verified-update.ps1");
            Path log = folder.resolve("installer.log");
            // Never interpret a filename as PowerShell source; only decode inert UTF-8 literals.
            String content = """
                $ErrorActionPreference = 'Stop'
                $installer = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('%s'))
                $launcher = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('%s'))
                $installLog = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('%s'))
                $expectedSha256 = '%s'
                $parentPid = %d
                try {
                  for ($i=0; $i -lt 180; $i++) {
                    if (-not (Get-Process -Id $parentPid -ErrorAction SilentlyContinue)) { break }
                    Start-Sleep -Seconds 1
                  }
                  if (Get-Process -Id $parentPid -ErrorAction SilentlyContinue) { throw 'Previous app process did not exit' }
                  if ((Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedSha256) {
                    throw 'Downloaded installer changed after verification'
                  }
                  # Per-user install: no elevation; /qn installs silently and /norestart avoids forced reboot.
                  $arguments = '/i "' + $installer + '" /qn /norestart /L*v "' + $installLog + '"'
                  $process = Start-Process -FilePath "$env:SystemRoot\\System32\\msiexec.exe" -ArgumentList $arguments -Wait -PassThru
                  if ($process.ExitCode -eq 0 -or $process.ExitCode -eq 3010) {
                    if (Test-Path -LiteralPath $launcher) { Start-Process -FilePath $launcher }
                    Remove-Item -LiteralPath $installer -Force -ErrorAction SilentlyContinue
                  } else { throw "Windows Installer returned $($process.ExitCode)" }
                } catch {
                  Add-Content -LiteralPath $installLog -Value ((Get-Date).ToString('o') + ' Updater: ' + $_.Exception.Message)
                }
                """.formatted(b64(msi.toString()), b64(launcher), b64(log.toString()), stagedSha256, ProcessHandle.current().pid());
            Files.writeString(script, content, StandardCharsets.UTF_8);
            new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", script.toString()).start();
            log("Updater scheduled for app exit: " + stagedVersion);
        } catch (Exception error) {
            log("Could not start update installer: " + error.getMessage());
        }
    }

    private static String b64(String string) {
        return Base64.getEncoder().encodeToString(string.getBytes(StandardCharsets.UTF_8));
    }

    private Path updateDirectory() throws IOException {
        String local = System.getenv("LOCALAPPDATA");
        Path root = local == null || local.isBlank()
            ? Path.of(System.getProperty("user.home"), ".bird-theatre")
            : Path.of(local, "BirdTheatre");
        Path folder = root.resolve("updates");
        Files.createDirectories(folder);
        return folder;
    }

    public void stop() { executor.shutdownNow(); }

    static int compareVersions(String a, String b) {
        String[] x = a.split("\\.", -1), y = b.split("\\.", -1);
        for (int i=0; i<Math.max(x.length,y.length); i++) {
            int xx = i<x.length ? Integer.parseInt(x[i]) : 0;
            int yy = i<y.length ? Integer.parseInt(y[i]) : 0;
            if (xx != yy) return Integer.compare(xx,yy);
        }
        return 0;
    }

    private static void log(String message) {
        try {
            String folder = System.getenv("LOCALAPPDATA");
            Path directory = folder == null || folder.isBlank()
                ? Path.of(System.getProperty("user.home"), ".bird-theatre") : Path.of(folder, "BirdTheatre");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("update.log"), Instant.now() + " " + message + System.lineSeparator(),
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }

    record Manifest(String version, String url, String sha256) { }
    private static final class NoReleaseException extends Exception { }
}
