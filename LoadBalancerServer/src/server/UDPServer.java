package server;

import com.google.gson.JsonObject;
import util.BackgroundUtil;
import util.LoggingUtil;
import util.MessageUtil;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class UDPServer extends Server{

    private final int port;
    private final int backgroundPort;
    private DatagramChannel serverSocketChannel;
    private Selector selector;
    private volatile boolean isRunning = true;

    public static class UDPServerManager {
        public synchronized static UDPServer getServerInstance(int port) {
            return (UDPServer) serverInstance.get(port);
        }
        public synchronized static UDPServer createServerInstance(int port){
            int backgroundPort = BackgroundUtil.getAvailableBackgroundPort();
            serverInstance.put(backgroundPort, new UDPServer(port,backgroundPort));
            return (UDPServer) serverInstance.get(backgroundPort);
        }
    }

    private UDPServer(int port,int backgroundPort) {
        this.port = port;
        this.backgroundPort = backgroundPort;
    }

    @Override
    public int getBackgroundPort(){
        return backgroundPort;
    }

    @Override
    public String getProtocol() {
        return "udp";
    }

    @Override
    public int getPort() {
        return port;
    }

    private void initialize(){
        try {
            selector = Selector.open();
            serverSocketChannel = DatagramChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(backgroundPort));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Error during server Start: " + e.getMessage());
        }
    }

    @Override
    public void start() {
        try {
            isRunning = true;
            while(isRunning){
                selector.select();
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    if (key.isReadable()) {
                        handleRequest((DatagramChannel) key.channel());
                    }
                }
            }
        }catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Error during Load Balancer operation: " + e.getMessage());
        }finally {
            stopServer();
        }
    }

    private void handleRequest(DatagramChannel datagramChannel){
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            SocketAddress address = datagramChannel.receive(buffer);
            buffer.flip();

            String message = new String(buffer.array(), 0, buffer.limit());
            if (message.isEmpty()) {
                LoggingUtil.logAsync(Level.SEVERE,"Received empty message from Load Balancer or Client.");
                return;
            }
            LoggingUtil.logAsync(Level.INFO,"Received message from Load Balancer or Client :"+message);
            JsonObject responseMessageJson = handleJsonRequest(message);
            handleResponse(datagramChannel,address,responseMessageJson);

        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"UDP Server Not Received on port " + port + " : " + e.getMessage());
        }
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
            LoggingUtil.logAsync(Level.INFO,"UDP Server closed successfully on background port: " + backgroundPort);
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Error while closing UDP Server: " + e.getMessage());
        }
    }

    private void handleResponse(
            DatagramChannel datagramChannel,SocketAddress address,JsonObject responseMessage) throws IOException {
        String type = responseMessage.get("type").getAsString();
        String msg = responseMessage.get("msg").getAsString();
        ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes());
        if (type.equals("lb")){
            if (!msg.equals("not send")){
                datagramChannel.send(buffer,address);
            }
        }else if (type.equals("client")){
            datagramChannel.send(buffer,address);
        }else{
            String errorMessage = responseMessage.get("msg").getAsString();
            LoggingUtil.logAsync(Level.SEVERE,"Failed to Make Response Message: " + errorMessage);
        }
    }

    @Override
    public void registerLoadBalancer(){
        initialize();
        try {
            createUdpRequest(MessageUtil.getServerRegisterMessage(getProtocol(),getPort(),getBackgroundPort()));
            LoggingUtil.logAsync(Level.INFO,"UDP Server connected to Load Balancer on port "+port);
            printAllState("register");
        }catch (IOException e){
            LoggingUtil.logAsync(Level.SEVERE,"Failed to connect to Load Balancer: " + e.getMessage());
        }
    }
    @Override
    public void unregisterLoadBalancer(){
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                createUdpRequest(MessageUtil.getServerUnRegisterMessage(getProtocol(), port, backgroundPort));
                LoggingUtil.logAsync(Level.INFO,"Unregister message sent to Load Balancer for port " + port);
            } catch (IOException e) {
                LoggingUtil.logAsync(Level.SEVERE,"Failed to send unregister message to Load Balancer: " + e.getMessage());
            }
        });
        future.thenRun(() -> {
            printAllState("unregister");
            isRunning = false;
            LoggingUtil.logAsync(Level.INFO,"UDP Server stopped on port " + port);
        }).exceptionally(ex -> {
            LoggingUtil.logAsync(Level.SEVERE,"An error occurred during unregistering: " + ex.getMessage());
            return null;
        });
    }

    private void createUdpRequest(String message) throws IOException{
        DatagramChannel udpChannel = DatagramChannel.open();
        InetSocketAddress serverAddress = new InetSocketAddress("localhost",8080);
        byte[] sendData = message.getBytes();
        ByteBuffer sendBuffer = ByteBuffer.wrap(sendData);
        udpChannel.send(sendBuffer,serverAddress);
        handleRequest(udpChannel);
    }
}
