
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import server.APIServer;
import server.Server;
import server.TCPServer;
import server.UDPServer;
import util.LoggingUtil;
import util.MessageUtil;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class LoadBalancer {

    private final Map<String, List<Server>> serverMap = new ConcurrentHashMap<>();
    private final Map<SocketChannel, Server> sessionTable = new ConcurrentHashMap<>();
    private final Map<String, Integer> roundRobinIndex = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile boolean isRunning = false;

    private Selector selector;
    private ServerSocketChannel tcpChannel;
    private DatagramChannel udpChannel;

    public LoadBalancer(){
        try {
            startHealthCheck();
            LoggingUtil.logAsync(Level.INFO,"Load Balancer started and listening on port 8080 for both TCP and UDP");
            selector = Selector.open();
            initialize();
            startLoadBalancer();
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Error initializing Load Balancer: " + e.getMessage());
        }
    }

    public void initialize() throws IOException{

        tcpChannel = ServerSocketChannel.open();
        tcpChannel.bind(new InetSocketAddress(8080));
        tcpChannel.configureBlocking(false);
        tcpChannel.register(selector, SelectionKey.OP_ACCEPT);

        udpChannel = DatagramChannel.open();
        udpChannel.bind(new InetSocketAddress(8080));
        udpChannel.configureBlocking(false);
        udpChannel.register(selector, SelectionKey.OP_READ);
    }

    private void startLoadBalancer() {
        try {
            isRunning = true;
            while (isRunning) {
                selector.select();
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    if (key.isAcceptable()) {
                        handleTcpConnection();
                    }
                    if (key.isReadable()) {
                        if (key.channel() instanceof DatagramChannel) {
                            handleUdpRequest((DatagramChannel) key.channel());
                        } else if (key.channel() instanceof SocketChannel) {
                            handleTcpRequest((SocketChannel) key.channel());
                        }
                    }
                }
            }
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Error during Load Balancer operation: " + e.getMessage());
        }finally {
            closeLoadBalancer();
        }
    }

    private void handleTcpConnection() throws IOException {
        SocketChannel clientChannel = tcpChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
    }

    private synchronized void handleTcpRequest(SocketChannel clientChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead;
        try {
            bytesRead = clientChannel.read(buffer);
        } catch (IOException e) {
            if (e.getMessage().contains("Connection reset")) {
                LoggingUtil.logAsync(Level.INFO,"Client connection closed successfully.");
                clientChannel.close();
                return;
            } else {
                throw e;
            }
        }
        if (bytesRead == -1) {
            clientChannel.close();
            LoggingUtil.logAsync(Level.INFO,"Client connection closed.");
            return;
        }
        buffer.flip();
        String request = new String(buffer.array(), 0, bytesRead);
        LoggingUtil.logAsync(Level.INFO,"Received TCP request: " + request);
        if (isServerRequest(request)) {
            handleServerRequest(request, clientChannel);
        } else {
            handleClientRequest(request,clientChannel);
        }
    }

    private synchronized void handleUdpRequest(DatagramChannel udpChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        SocketAddress address = udpChannel.receive(buffer);  // UDP 메시지 수신
        buffer.flip();
        String request = new String(buffer.array(), 0, buffer.limit());
        LoggingUtil.logAsync(Level.INFO,"Received UDP request: " + request);
        handleServerRequest(request,address,udpChannel);
    }
    private String sendUdpMessage(Server server,byte[] sendData) throws IOException {

        DatagramChannel udpChannel = DatagramChannel.open();
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", server.getBackgroundPort());

        ByteBuffer sendBuffer = ByteBuffer.wrap(sendData);
        udpChannel.send(sendBuffer,serverAddress);
        ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);
        udpChannel.configureBlocking(true);
        udpChannel.receive(receiveBuffer);
        receiveBuffer.flip();
        return new String(receiveBuffer.array(), 0, receiveBuffer.limit());
    }

    private String sendTcpMessage(Server server,String message) throws IOException {

        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress("localhost", server.getBackgroundPort()));
        socketChannel.configureBlocking(true);

        ByteBuffer responseBuffer = ByteBuffer.wrap(message.getBytes());
        socketChannel.write(responseBuffer);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = socketChannel.read(buffer);
        buffer.flip();

        String response = new String(buffer.array(), 0, bytesRead);
        buffer.clear();
        socketChannel.close();
        return response;
    }

    private void handleClientRequest(String request,SocketChannel clientChannel) throws IOException {
        String[] parsedMessage = request.split(" - ");
        String responseMessage = "Fail connect to server";
        try {
            if (parsedMessage[0].equals("case : 1")){
                ByteBuffer responseBuffer = ByteBuffer.wrap(getAllPortState("client").getBytes());
                clientChannel.write(responseBuffer);
            }else if (parsedMessage[0].equals("case : 2")){
                Server targerServer = getServerForClient(parsedMessage[1],clientChannel);
                responseMessage = "No Available use Server";
                if (targerServer != null){
                    responseMessage = "connect - " + targerServer.getPort();
                }
                ByteBuffer responseBuffer = ByteBuffer.wrap(responseMessage.getBytes());
                clientChannel.write(responseBuffer);
            }else{
                Server targerServer = getServerForClient(parsedMessage[1],clientChannel);
                if (targerServer != null){
                    forwardTraffic(parsedMessage[1],clientChannel,targerServer);
                }else{
                    ByteBuffer responseBuffer = ByteBuffer.wrap(responseMessage.getBytes());
                    clientChannel.write(responseBuffer);
                }
            }
        } catch (IOException e) {
            ByteBuffer responseBuffer = ByteBuffer.wrap(responseMessage.getBytes());
            clientChannel.write(responseBuffer);
            LoggingUtil.logAsync(Level.SEVERE,"Failed to send response: " + e.getMessage());
        }
    }
    private void handleServerRequest(
            String request,SocketAddress address,DatagramChannel udpChannel){
        String responseMessage = handleJsonMessage(request);
        ByteBuffer responseBuffer = ByteBuffer.wrap(responseMessage.getBytes());
        try {
            udpChannel.send(responseBuffer,address);
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Failed to send response: " + e.getMessage());
        }
    }
    private void handleServerRequest(String request, SocketChannel serverChannel) {

        String responseMessage = handleJsonMessage(request);
        ByteBuffer responseBuffer = ByteBuffer.wrap(responseMessage.getBytes());
        try {
            serverChannel.write(responseBuffer);
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Failed to send response: " + e.getMessage());
        }
    }
    private boolean isServerRequest(String request) {
        try {
            JsonObject jsonRequest = JsonParser.parseString(request).getAsJsonObject();
            return jsonRequest.has("awk") || jsonRequest.has("cmd");
        } catch (Exception e) {
            return false;
        }
    }
    private void forwardTraffic(String message,SocketChannel clientChannel, Server server) throws IOException {

        if (server.getProtocol().equals("udp")){
            try {
                byte[] sendData = message.getBytes();
                String response = sendUdpMessage(server,sendData);
                ByteBuffer clientResponse = ByteBuffer.wrap(response.getBytes());
                clientChannel.write(clientResponse);

            } catch (IOException e) {
                LoggingUtil.logAsync(Level.SEVERE,"Failed to Forward request: " + e.getMessage());
            }
        }else{
            try {
                String response = sendTcpMessage(server,message);
                ByteBuffer clientResponse = ByteBuffer.wrap(response.getBytes());
                clientChannel.write(clientResponse);
            } catch (IOException e) {
                LoggingUtil.logAsync(Level.SEVERE,"Failed to Forward request: " + e.getMessage());
            }
        }
    }
    private synchronized Server getServerForClient(String protocol,SocketChannel clientChannel) {

        if (sessionTable.containsKey(clientChannel)) {
            return sessionTable.get(clientChannel);
        }
        List<Server> servers = serverMap.get(protocol);
        if (servers == null || servers.isEmpty()) {
            LoggingUtil.logAsync(Level.SEVERE,"No Available use Server");
            return null;
        }
        if (!roundRobinIndex.containsKey(protocol)) {
            roundRobinIndex.put(protocol, 0);
        }
        int index = roundRobinIndex.get(protocol);
        Server selectedServer = servers.get(index);
        roundRobinIndex.put(protocol, (index + 1) % servers.size());
        sessionTable.put(clientChannel, selectedServer);
        return selectedServer;
    }
    private void unregisterServer(String protocol,int backgroundPort){

        List<Server> servers = serverMap.get(protocol);
        servers.removeIf(server -> server.getBackgroundPort() == backgroundPort);
        if (getTotalServerCount() == 0){
            isRunning = false;
            return;
        }else{
            serverMap.put(protocol, new ArrayList<>(servers));
        }
        reassignClients(protocol,backgroundPort);
    }
    private void reassignClients(String protocol,int backgroundPort) {

        for (Map.Entry<SocketChannel, Server> entry : sessionTable.entrySet()) {

            SocketChannel clientChannel = entry.getKey();
            Server currentServer = entry.getValue();
            if (currentServer.getBackgroundPort() == backgroundPort) {
                Server newServer = findNewServer(protocol, currentServer.getPort());
                if (newServer != null) {
                    sessionTable.put(clientChannel, newServer);
                } else {
                    sessionTable.remove(clientChannel);
                }
            }
        }
    }
    private Server findNewServer(String protocol, int originalPort) {

        List<Server> servers = serverMap.getOrDefault(protocol, new ArrayList<>());
        for (Server server : servers) {
            if (server.getPort() == originalPort) {
                return server;
            }
        }
        if (!servers.isEmpty()) {
            return servers.getFirst();
        }
        return null;
    }

    private void closeLoadBalancer() {
        try {
            isRunning = false;
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
            if (tcpChannel != null && tcpChannel.isOpen()) {
                tcpChannel.close();
            }
            if (udpChannel != null && udpChannel.isOpen()) {
                udpChannel.close();
            }
            sessionTable.clear();
            roundRobinIndex.clear();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            String output = """
                    ====================
                    Load Balancer closed successfully.
                    ====================
                    """;
            System.out.println(output);
            LoggingUtil.logAsync(Level.INFO,"Load Balancer closed successfully.");
        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Error while closing Load Balancer: " + e.getMessage());
        }
    }
    private void registerServer(String protocol,int backgroundPost) {
        Server server = switch (protocol) {
            case "tcp" -> TCPServer.TCPServerManager.getServerInstance(backgroundPost);
            case "udp" -> UDPServer.UDPServerManager.getServerInstance(backgroundPost);
            case "http" -> APIServer.APIServerManager.getServerInstance(backgroundPost);
            default -> throw new IllegalArgumentException("Unknown protocol: " + protocol);
        };
        serverMap.computeIfAbsent(protocol, k -> new ArrayList<>()).add(server);
        roundRobinIndex.putIfAbsent(protocol, 0);
        String output = "\n" +
                "====================\n" +
                "Load Balancer State : New Register " + server.getProtocol() + " Server - " +
                " Client & Background port : " + server.getPort() + " / " + server.getBackgroundPort() +"\n" +
                "Remaining Server List\n" +
                getAllPortState("server") + "\n" +
                "====================\n" +
                "\n";
        System.out.print(output);

    }

    private void startHealthCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            long start = System.currentTimeMillis();
            System.out.println();
            System.out.println("====================");
            System.out.println("Running health check for servers");
            for (List<Server> servers : serverMap.values()) {
                servers.removeIf(server -> {
                    boolean isHealthy = checkServerHealth(server);
                    if (isHealthy) {
                        System.out.println(server.getProtocol()+" Server on Client & Background port : " + server.getPort() + " / " + server.getBackgroundPort() + " is healthy.");
                    } else {
                        System.out.println(server.getProtocol()+" Server on Client & Background port : " + server.getPort() + " / " + server.getBackgroundPort() + " is unhealthy. Removing the Server");
                    }
                    return !isHealthy;
                });
            }
            System.out.println("Remaining Server List");
            System.out.println(getAllPortState("server"));
            long finish = System.currentTimeMillis();
            long timeMs = finish - start;
            System.out.println("Time to perform health check : " + timeMs + "ms");
            System.out.println("====================");
            System.out.println();
            if (getTotalServerCount() == 0){
                sessionTable.clear();
                roundRobinIndex.clear();
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    private boolean checkServerHealth(Server server) {
        try{
            byte[] sendData = MessageUtil.getHealthCheckMessage().getBytes();
            String response = sendUdpMessage(server,sendData);
            return MessageUtil.getHealthCheckAwkMessage().equals(response);

        } catch (IOException e) {
            return false;
        }
    }
    private String handleJsonMessage(String request){
        try{
            JsonObject jsonRequest = JsonParser.parseString(request).getAsJsonObject();
            String cmd = jsonRequest.get("cmd").getAsString();
            String protocol = jsonRequest.get("protocol").getAsString();
            int bp = jsonRequest.get("bp").getAsInt();
            if (cmd.equals("register")){
                registerServer(protocol,bp);
            }else{
                unregisterServer(protocol,bp);
            }
            return MessageUtil.getSuccessAwkMessage();
        }catch(JsonParseException e){
            return MessageUtil.getFailedAwkMessage(e.getMessage());
        }
    }
    private int getTotalServerCount(){
        return serverMap.values().stream()
                .mapToInt(List::size)
                .sum();
    }
    private String getAllPortState(String type){
        StringBuilder result = new StringBuilder();
        Set<String> uniqueServers = new HashSet<>();

        for (Map.Entry<String, List<Server>> entry : serverMap.entrySet()) {
            List<Server> serverList = entry.getValue();

            for (Server server : serverList) {
                int targetPort = type.equals("client") ? server.getPort() : server.getBackgroundPort();
                String uniqueKey = server.getProtocol() + ":" + targetPort;
                if (uniqueServers.add(uniqueKey)) {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("Protocol", server.getProtocol());
                    jsonObject.addProperty("Port", server.getPort());
                    if (!type.equals("client")){
                        jsonObject.addProperty("Background Port", targetPort);
                    }
                    result.append(jsonObject).append("\n");
                }
            }
        }
        if (!result.isEmpty()) {
            result.setLength(result.length() - 1);
        }else{
            return "No Server Register";
        }
        return result.toString();
    }

}
