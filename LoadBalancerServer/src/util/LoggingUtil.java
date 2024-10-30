package util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.*;

public class LoggingUtil {

    private static final Logger logger = Logger.getLogger("GlobalLogger");
    private static final ExecutorService logExecutor = Executors.newSingleThreadExecutor();

    static {
        try {
            Logger rootLogger = Logger.getLogger("");
            Handler[] handlers = rootLogger.getHandlers();
            for (Handler handler : handlers) {
                if (handler instanceof ConsoleHandler) {
                    rootLogger.removeHandler(handler);
                }
            }

            Path logDir = Paths.get("./logs");
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }

            FileHandler fileHandler = new FileHandler("./logs/app.log", false);
            SimpleFormatter formatter = new SimpleFormatter();
            fileHandler.setFormatter(formatter);

            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);
            fileHandler.setLevel(Level.ALL);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception creating file handler", e);
        }
    }

    public static void logAsync(Level level, String message) {
        if (!logExecutor.isShutdown()) {
            logExecutor.submit(() -> logger.log(level, message));
        } else {
            logger.log(level, "Logger is already shutdown. Unable to log: " + message);
        }
    }

    public static void shutdown() {
        logExecutor.shutdown();
    }

    public static ExecutorService getLogExecutor() {
        return logExecutor;
    }
}
