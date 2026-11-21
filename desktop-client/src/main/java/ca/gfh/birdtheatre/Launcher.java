package ca.gfh.birdtheatre;

/** Separate main class for jpackage with startup logging and CI runtime verification. */
public final class Launcher {
    private Launcher() { }

    public static void main(String[] args) {
        DesktopDiagnostics.initialize();
        try {
            if (args.length == 1 && "--package-self-test".equals(args[0])) {
                ClassLoader loader = Launcher.class.getClassLoader();
                Class.forName("javafx.application.Application", false, loader);
                Class.forName("javafx.scene.web.WebView", false, loader);
                Class.forName("java.net.http.HttpClient", false, loader);
                DesktopDiagnostics.event("Packaged runtime self-test passed");
                return;
            }
            BirdTheatreDesktop.main(args);
        } catch (Throwable error) {
            DesktopDiagnostics.fatal("application launch", error);
            System.exit(1);
        }
    }
}
