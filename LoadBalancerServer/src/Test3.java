import server.APIServer;
import server.TCPServer;
import server.UDPServer;
import util.LoggingUtil;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class Test3 {

    public static void main(String[] args) {

        ExecutorService executor = Executors.newFixedThreadPool(4);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        System.out.println("Start Test3 and start LoadBalancer");
        LoggingUtil.logAsync(Level.INFO,"Start Application");

        executor.submit(() -> {
            LoadBalancer loadBalancer = new LoadBalancer();
            try {
                loadBalancer.initialize();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        executor.submit(() -> {
            try {
                Thread.sleep(2000);
                TCPServer tcpServer = TCPServer.TCPServerManager.createServerInstance(80);
                tcpServer.registerLoadBalancer();
                Thread tcpThread = new Thread(tcpServer::start);
                tcpThread.start();
                scheduler.schedule(() -> {
                    try {
                        tcpServer.unregisterLoadBalancer();
                    } catch (Exception e) {
                        System.out.println("Fail unregister Thread");
                    }
                }, 10, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.out.println("Create Fail Thread");
            }
        });
        executor.submit(() -> {
            try {
                Thread.sleep(2000);
                UDPServer udpServer = UDPServer.UDPServerManager.createServerInstance(61);
                udpServer.registerLoadBalancer();
                Thread udiThread = new Thread(udpServer::start);
                udiThread.start();
                scheduler.schedule(() -> {
                    try {
                        udpServer.unregisterLoadBalancer();
                    } catch (Exception e) {
                        System.out.println("Fail unregister Thread");
                    }
                }, 20, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.out.println("Create Fail Thread");
            }
        });
        executor.submit(() -> {
            try {
                Thread.sleep(2000);
                APIServer apiServer = APIServer.APIServerManager.createServerInstance(9000);
                apiServer.registerLoadBalancer();
                Thread apiThread = new Thread(apiServer::start);
                apiThread.start();
                scheduler.schedule(() -> {
                    try {
                        apiServer.unregisterLoadBalancer();
                    } catch (Exception e) {
                        System.out.println("Fail unregister Thread");
                    }
                }, 30, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.out.println("Create Fail Thread");
            }
        });
        executor.shutdown();
        scheduler.schedule(() -> {
            if (executor.isTerminated() && scheduler.isTerminated()) {
                System.exit(0);
            }
        }, 1, TimeUnit.SECONDS);
    }
}
