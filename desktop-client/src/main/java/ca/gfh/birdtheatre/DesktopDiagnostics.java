package ca.gfh.birdtheatre;

import javax.swing.JOptionPane;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Captures failures hidden behind Windows GUI launchers. */
public final class DesktopDiagnostics {
    private DesktopDiagnostics() { }

    static Path logPath() {
        String local = System.getenv("LOCALAPPDATA");
        Path folder = (local == null || local.isBlank())
            ? Path.of(System.getProperty("user.home", "."), ".bird-theatre")
            : Path.of(local, "BirdTheatre");
        return folder.resolve("desktop-startup.log");
    }

    public static void initialize() {
        event("Launcher started; Java=" + System.getProperty("java.version", "unknown")
            + "; packaged=" + (System.getProperty("jpackage.app-path") != null));
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
            report("Uncaught error on " + thread.getName(), error));
    }

    public static synchronized void event(String message) {
        try {
            Path target = logPath();
            Files.createDirectories(target.getParent());
            Files.writeString(target, Instant.now() + " " + message + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }

    public static void report(String phase, Throwable error) {
        StringWriter stack = new StringWriter();
        error.printStackTrace(new PrintWriter(stack));
        event("FAILED during " + phase + ": " + stack);
        System.err.println("Bird Theatre " + phase + ": " + stack);
    }

    public static void fatal(String phase, Throwable error) {
        report(phase, error);
        try {
            String detail = error.getMessage() == null ? error.getClass().getSimpleName()
                : error.getClass().getSimpleName() + ": " + error.getMessage();
            JOptionPane.showMessageDialog(null,
                "Bird Theatre could not start.\n\n" + detail
                    + "\n\nTechnical log: " + logPath(),
                "Bird Theatre startup error", JOptionPane.ERROR_MESSAGE);
        } catch (Throwable ignored) { }
    }
}
