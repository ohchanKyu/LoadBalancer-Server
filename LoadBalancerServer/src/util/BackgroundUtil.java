package util;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class BackgroundUtil {

    private static final Set<Integer> backgroundPorts = new HashSet<>();

    public static synchronized int getAvailableBackgroundPort() {
        int fakePort;
        do {
            fakePort = generateRandomPort();
        } while (backgroundPorts.contains(fakePort));
        backgroundPorts.add(fakePort);
        return fakePort;
    }
    private static int generateRandomPort() {
        return 10000 + new Random().nextInt(55535);
    }
}
