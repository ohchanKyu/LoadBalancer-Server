package server;

import util.BackgroundUtil;
import util.LoggingUtil;
import util.MessageUtil;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class APIServer extends TCPBaseServer{

    private final int port;
    private final int backgroundPort;

    private Selector selector;
    private SocketChannel loadBalancerSocket;
    private ServerSocketChannel serverSocketChannel;
    private DatagramChannel udpChannel;
    private volatile boolean isRunning = false;

    private APIServer(int port,int backgroundPort) {
        this.port = port;
        this.backgroundPort = backgroundPort;
    }

    public static class APIServerManager {
        public synchronized static APIServer getServerInstance(int port) {
            return (APIServer) serverInstance.get(port);
        }
        public synchronized static APIServer createServerInstance(int port){
            int backgroundPort = BackgroundUtil.getAvailableBackgroundPort();
            serverInstance.put(backgroundPort, new APIServer(port,backgroundPort));
            return (APIServer) serverInstance.get(backgroundPort);
        }
    }

    private void initialize(){
        try {
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(backgroundPort));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            udpChannel = DatagramChannel.open();
            udpChannel.bind(new InetSocketAddress(backgroundPort));
            udpChannel.configureBlocking(false);
            udpChannel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Error during server Start: " + e.getMessage());
        }
    }

    @Override
    public int getBackgroundPort(){
        return backgroundPort;
    }

    @Override
    public String getProtocol() {
        return "http";
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void start() {
        try {
            isRunning = true;
            while (isRunning) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    if (key.isAcceptable()) {
                        handleTcpConnection();
                    }
                    if (key.isReadable()) {
                        if (key.channel() instanceof DatagramChannel) {
                            handleHealthCheck(key);
                        } else if (key.channel() instanceof SocketChannel) {
                            handleTcpRequest(key);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Error during API " + port + "server run : " + e.getMessage());
        }finally {
            stopServer();
        }
    }

    private void handleTcpConnection() throws IOException {
        SocketChannel clientChannel = serverSocketChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
    }

    private void connectToLoadBalancer() throws IOException {
        if (loadBalancerSocket == null || !loadBalancerSocket.isConnected()) {
            loadBalancerSocket = SocketChannel.open();
            loadBalancerSocket.connect(new InetSocketAddress("localhost", 8080));
            loadBalancerSocket.configureBlocking(false);
        }
    }

    @Override
    public void registerLoadBalancer(){
        try{
            initialize();
            connectToLoadBalancer();
            loadBalancerSocket.register(selector, SelectionKey.OP_READ);
            sendToLoadBalancerMessage(MessageUtil.getServerRegisterMessage(getProtocol(), getPort(),getBackgroundPort()));
            LoggingUtil.logAsync(Level.INFO,"API Server connected to Load Balancer on port "+port);
            printAllState("register");
        }catch (IOException e){
            LoggingUtil.logAsync(Level.SEVERE,"Failed to connect to Load Balancer: " + e.getMessage());
        }
    }

    @Override
    public void unregisterLoadBalancer(){
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                sendToLoadBalancerMessage(MessageUtil.getServerUnRegisterMessage(getProtocol(), port, backgroundPort));
                LoggingUtil.logAsync(Level.INFO,"Unregister message sent to Load Balancer for port " + port);
            } catch (IOException e) {
                LoggingUtil.logAsync(Level.SEVERE,"Failed to send unregister message to Load Balancer: " + e.getMessage());
            }
        });
        future.thenRun(() -> {
            printAllState("unregister");
            isRunning = false;
            LoggingUtil.logAsync(Level.INFO,"API Server stopped on port " + port);
        }).exceptionally(ex -> {
            LoggingUtil.logAsync(Level.SEVERE,"An error occurred during unregistering: " + ex.getMessage());
            return null;
        });
    }
    private void stopServer() {
        try {
            if (selector != null && selector.isOpen()) {
                selector.wakeup();
                selector.close();
            }
            if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
                serverSocketChannel.close();
            }
            if (udpChannel != null && udpChannel.isOpen()){
                udpChannel.close();
            }
            LoggingUtil.logAsync(Level.INFO,"API Server closed successfully on background port: " + backgroundPort);
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Error while closing API Server: " + e.getMessage());
        }
    }

    @Override
    protected void sendToLoadBalancerMessage(String message) throws IOException {
        if (loadBalancerSocket != null && loadBalancerSocket.isConnected()) {
            ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
            loadBalancerSocket.write(buffer);
        } else {
            LoggingUtil.logAsync(Level.SEVERE,"Not connected to Load Balancer, unable to send message");
        }
    }
}
