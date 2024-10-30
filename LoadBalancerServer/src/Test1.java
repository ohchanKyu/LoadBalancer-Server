import server.APIServer;
import server.TCPServer;
import server.UDPServer;
import util.LoggingUtil;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class Test1 {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(6);

        System.out.println("Start Test1 and start LoadBalancer");
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
                tcpServer.start();
            } catch (Exception e) {
                System.out.println("Create Fail Thread");
            }
        });
        executor.submit(() -> {
            try {
                Thread.sleep(2000);
                TCPServer tcpServer = TCPServer.TCPServerManager.createServerInstance(81);
                tcpServer.registerLoadBalancer();
                tcpServer.start();
            } catch (Exception e) {
                System.out.println("Create Fail Thread");
            }
        });
        executor.submit(() -> {
            try {
                Thread.sleep(2000);
                UDPServer udpServer = UDPServer.UDPServerManager.createServerInstance(60);
                udpServer.registerLoadBalancer();
                udpServer.start();
            } catch (Exception e) {
                System.out.println("Create Fail Thread");
            }
        });
        executor.submit(() -> {
            try {
                Thread.sleep(2000);
                APIServer apiServer = APIServer.APIServerManager.createServerInstance(8081);
                apiServer.registerLoadBalancer();
                apiServer.start();
            } catch (Exception e) {
                System.out.println("Create Fail Thread");
            }
        });
        executor.submit(() -> {
            try {
                Thread.sleep(2000);
                APIServer apiServer = APIServer.APIServerManager.createServerInstance(8082);
                apiServer.registerLoadBalancer();
                apiServer.start();
            } catch (Exception e) {
                System.out.println("Create Fail Thread");
            }
        });
        executor.shutdown();
    }
}
