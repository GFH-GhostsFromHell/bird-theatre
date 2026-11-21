# Bird Theatre — Windows MSI installer and automatic updates

This is the Bird Theatre **desktop** installer/update channel. It is independent of Electrical Workbench, while using the same Java 21 + JavaFX + Maven + `jpackage` approach. This source repository does not include a precompiled Windows `.msi` file; it must be built on **Windows** or by the Windows GitHub Actions runner.

## What the end user gets

- A self-contained **Bird Theatre** Windows `.msi` with the Java runtime bundled (no separate Java installation needed).
- Shortcuts in the Windows Start menu and on the desktop.
- A fixed upgrade UUID (`a97ee0d9-a576-4ab4-a03e-afc40d4aac13`) and consistent application name, so future versions upgrade in place rather than creating unrelated installed apps.
- A per-user installation, generally not requiring administrator access.
- A check for new published, *signed* updates about 8 seconds after startup, then every **4 hours** while the app remains open, plus a **Check for updates** button.
- Background download and SHA-256 verification of a newer MSI.
- Once downloaded, the status shows **Ready — installs automatically when you close the app**, with an optional **Install update now** button.
- After exit, a separate PowerShell helper waits for the app to stop, calls `msiexec /i ... /qn /norestart` (silent, no forced reboot), and restarts Bird Theatre if Windows Installer succeeds.
- If update metadata is unavailable, invalid, or has a bad signature/hash, the installed application continues working unchanged.

**Important:** Automatic installation is enabled only for installed Windows releases containing a trusted Ed25519 release-verification public key. CI artifacts without the signing configuration do not auto-install updates. The MSI itself is *not* Authenticode-signed by this template and may prompt Windows SmartScreen on first install; use a Windows code-signing certificate in production.

## Direct download (not ZIP)

[**Download the latest Bird Theatre Windows installer — direct .msi**](https://github.com/GFH-GhostsFromHell/bird-theatre/releases/latest/download/BirdTheatre-Windows-Setup.msi)

GitHub Actions **Artifacts** are automatically ZIP-compressed by GitHub. This project publishes Windows installers instead as public **GitHub Release assets** from successful main-branch builds. Download the MSI directly from Releases; do not use a workflow artifact ZIP. Unsigned/manual installer releases do not contain `BirdTheatre-update.json` or its signature and cannot enable unattended updates. Configuring the signing secret and public key allows verified automatic updates in subsequent releases. Windows may show a publisher warning because the MSI is not Authenticode-signed.

## Build the MSI locally on Windows

1. Install **JDK 21** (with `jpackage`), Maven 3.9+, and a Windows `jpackage`-compatible **WiX Toolset**. Verify `java`, `mvn`, and `jpackage` resolve on PATH.
2. From the repository root, open PowerShell:

   ```powershell
   .\scripts\build-windows.ps1 -Version 0.1.1
   ```
3. Download/install `desktop-client\target\installer\BirdTheatre-Windows-Setup.msi`.

Use a higher three-part numeric version for every upgrade. Do not change the `--name` or `--win-upgrade-uuid` between builds. The unconfigured local installer launches and runs normally, but automatic updates stay off until the publisher public key is supplied.

## Build and publish in GitHub Actions

1. Create `GFH-GhostsFromHell/bird-theatre` and push this repository. The default auto-update URL refers to **public** releases under that exact repository; a private repository will not work for unauthenticated Windows clients.
2. Run Actions → **Build, test and release Bird Theatre Windows MSI** → **Run workflow**. Once the workflow succeeds, it publishes the installer **directly** to [GitHub Releases](https://github.com/GFH-GhostsFromHell/bird-theatre/releases/latest/download/BirdTheatre-Windows-Setup.msi) as `BirdTheatre-Windows-Setup.msi`. **No ZIP extraction is required.** With no signing keys, the release is for manual installation only; no automatic-update manifest is published.
3. Generate a fresh **Ed25519** update-signing keypair **locally** on a trusted computer with Java 21:

   ```powershell
   java .\scripts\GenerateUpdateKeys.java
   ```

   This prints two single-line values without saving the private key to disk. Keep the *private* value confidential; preserve a secure backup outside GitHub. Never commit it to source control. (Alternatively, use OpenSSL and export the PKCS#8 private key and X.509 public key as DER/base64.)
4. In the *Bird Theatre* GitHub repository Settings → Secrets and variables → Actions, configure:
   - Repository **secret** `UPDATE_SIGNING_PRIVATE_KEY_BASE64` = the generated **private** key value.
   - Repository **variable** `UPDATE_SIGNING_PUBLIC_KEY_BASE64` = the generated **public** key value.
5. Re-run the Windows workflow or push a change to `desktop-client/`. Successful `main` builds **always** publish a direct `.msi` release. When the signing credentials are configured, that same release also contains `BirdTheatre-update.json` and `BirdTheatre-update.json.sig` to enable verified automatic updates.
6. Install one of the newly signed-metadata-enabled builds on a Windows PC. Later published versions will be discovered and staged automatically.

GitHub Actions will refuse to publish an update when the private key does not match the embedded public key. The published manifest and MSI are delivered over HTTPS, the manifest must verify with the application’s pinned Ed25519 key, and the MSI's SHA-256 must match the signed manifest exactly. Only the versioned MSI asset at `GFH-GhostsFromHell/bird-theatre` is accepted.

Do **not** rotate the publisher public key casually: previously installed applications trust the key embedded when they were built. A key-rotation migration needs to be planned in advance. Revoke/disable releases immediately if a signing private key is exposed.

### Public versus private repositories

A private GitHub repository cannot serve the latest-release and MSI URLs to uncredentialed app installations. Do **not** embed GitHub tokens in the desktop application. If the repository must stay private, publish the signed manifest and versioned MSIs separately on a public download origin or authenticated update service; this source currently enforces the public GitHub release URL.

## Update behaviour and troubleshooting

- **Up to date:** nothing is installed and the app stays open.
- **New update downloaded:** safe to keep working. The updater installs after the app is closed, or immediately after selecting **Install update now**.
- **Failed download or verification:** nothing is installed; the app continues using its current version.
- **Installer error:** check `%LOCALAPPDATA%\BirdTheatre\updates\installer.log`; the verified MSI is retained for inspection. Update-service logs are `%LOCALAPPDATA%\BirdTheatre\update.log`.
- **Authentication/network failure:** if GitHub returns 404, the app reports no signed release; on other network failures it leaves the running version unchanged and checks again later.
- **Permissions:** builds use `--win-per-user-install`. An already installed machine-wide version created by a different MSI needs a separate migration and may request administrator rights.
- **Auto-update availability:** the key must be embedded in the packaged application and `jpackage.app-path` must identify a Windows executable. Running directly from an IDE does not auto-install packages.

### CI and security tests

The Windows build workflow runs `UpdateServiceSelfTest` against a locally generated Ed25519 key and verifies version comparisons, invalid signatures, tampering, and rejection of wrong release URLs. The JavaFX app is built with Maven before jpackage. **Actual install/upgrade testing on a Windows VM or physical Windows PC is still required.**

## Important limitations

- The app currently restarts after a successful update; it does **not** silently replace the running application while you are watching a stream. This avoids interrupting a live bird camera session or replacing in-use files.
- The initial download/installer have not been exercised in a live Windows MSI test on this development machine.
- Code signing the MSI with Authenticode and adding update rollback/repair testing are recommended before a public user launch.

## Desktop startup troubleshooting

If Windows reports an error when you open Bird Theatre, first install the newest MSI build rather than reusing an older artifact. The launcher now writes Java startup diagnostics to:

`%LOCALAPPDATA%\BirdTheatre\desktop-startup.log`

Use **Win+R** and paste that path to open it, then copy the last lines if the app still fails. Windows Installer log files for staged updates are separate: `%LOCALAPPDATA%\BirdTheatre\updates\installer.log`.

The desktop shell can now open even if the remote portal at `https://bird-theatre.onrender.com` is not available; in that case it shows a connection/retry screen. This does not mean the community livestream backend has been deployed. A working live community experience requires a separately deployed and configured portal.

The Windows workflow runs the packaged launcher using `--package-self-test` and checks that its JavaFX and HTTP classes load from the bundled runtime. It is **not** a substitute for opening the final MSI on a real Windows desktop, so please report any remaining Windows error text or screenshot.
