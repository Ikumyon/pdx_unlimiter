package com.crschnick.pdx_unlimiter.app.installation;

import com.crschnick.pdx_unlimiter.app.gui.DialogHelper;
import io.sentry.Sentry;
import javafx.application.Platform;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.LoggerFactory;

public class ErrorHandler {

    private static boolean startupCompleted = false;

    public static void init() {
        startupCompleted = true;

        if (!PdxuInstallation.getInstance().isProduction()) {
            return;
        }

        System.setProperty("sentry.dsn", "https://cff56f4c1d624f46b64f51a8301d3543@o462618.ingest.sentry.io/5466262");
        System.setProperty("sentry.stacktrace.hidecommon", "false");
        System.setProperty("sentry.stacktrace.app.packages", "");
        System.setProperty("sentry.uncaught.handler.enabled", "true");
        System.setProperty("sentry.environment", "production");
        System.setProperty("sentry.release", PdxuInstallation.getInstance().getVersion()
                + ", " + System.getProperty("os.name"));
        Sentry.init();

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            handleException(e);
        });
    }

    private static void handleExcetionWithoutInit(Throwable ex) {
        ex.printStackTrace();
        LoggerFactory.getLogger(ErrorHandler.class).error("Error", ex);
        if (PdxuInstallation.getInstance().isProduction()) {
            Sentry.init("https://cff56f4c1d624f46b64f51a8301d3543@o462618.ingest.sentry.io/5466262");
        }
        Sentry.capture(ex);
    }

    public static void handleException(Throwable ex) {
        handleException(ex, "Error occured");
    }

    public static void handleException(Throwable ex, String msg) {
        if (!startupCompleted) {
            handleExcetionWithoutInit(ex);
        }

        Runnable run = () -> {
            LoggerFactory.getLogger(ErrorHandler.class).error(msg, ex);
            if (PdxuInstallation.getInstance().isProduction()) {
                if (DialogHelper.showException(ex)) {
                    Sentry.capture(ex);
                }
            }
        };
        if (Platform.isFxApplicationThread()) {
            run.run();
        } else {
            Platform.runLater(run);
        }
    }

    public static void handleTerminalException(Exception ex) {
        if (!startupCompleted) {
            handleExcetionWithoutInit(ex);
        }

        Runnable run = () -> {
            LoggerFactory.getLogger(ErrorHandler.class).error("Error", ex);
            if (DialogHelper.showException(ex)) {
                Sentry.capture(ex);
            }
            System.exit(1);
        };
        if (Platform.isFxApplicationThread()) {
            run.run();
        } else {
            Platform.runLater(run);
        }
    }
}
