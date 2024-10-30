import util.LoggingUtil;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class LoggingTest {

    private static final int NUM_LOGS = 10000;

    public static void main(String[] args) {
        System.out.println("Asynchronous Logging Test:");
        long asyncStartTime = System.currentTimeMillis();
        for (int i = 0; i < NUM_LOGS; i++) {
            LoggingUtil.logAsync(Level.INFO, "Asynchronous log message " + i);
        }
        LoggingUtil.shutdown();
        try {
            if (!LoggingUtil.getLogExecutor().awaitTermination(60, TimeUnit.SECONDS)) {
                System.out.println("Some log tasks did not finish within the time limit.");
            }
        } catch (InterruptedException e) {
            System.out.println("Some log tasks did not finish within the time limit.");
        }
        long asyncEndTime = System.currentTimeMillis();
        long timeMs = asyncEndTime - asyncStartTime;
        System.out.println("Asynchronous Logging Time: " + timeMs + " ms");
    }
}
