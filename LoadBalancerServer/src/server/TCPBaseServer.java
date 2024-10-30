package server;

import com.google.gson.JsonObject;
import util.LoggingUtil;
import util.MessageUtil;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;

public abstract class TCPBaseServer extends Server{

    protected abstract void sendToLoadBalancerMessage(String message) throws IOException;

    protected void handleHealthCheck(SelectionKey key) throws IOException{
        DatagramChannel datagramChannel = (DatagramChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        SocketAddress address = datagramChannel.receive(buffer);
        buffer.flip();

        String request = new String(buffer.array(), 0, buffer.limit());
        LoggingUtil.logAsync(Level.INFO,"Received UDP request: " + request);
        String awkMessage = MessageUtil.getHealthCheckAwkMessage();
        ByteBuffer responseBuffer = ByteBuffer.wrap(awkMessage.getBytes());
        datagramChannel.send(responseBuffer,address);
    }

    protected void handleTcpRequest(SelectionKey key){
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        try {
            SocketChannel loadBalancerChannel = (SocketChannel) key.channel();
            int bytesRead = loadBalancerChannel.read(buffer);

            if (bytesRead == -1) {
                loadBalancerChannel.close();
                return;
            }

            buffer.flip();
            String message = new String(buffer.array(), 0, bytesRead);
            if (message.isEmpty()) {
                LoggingUtil.logAsync(Level.SEVERE,"Received empty message from Load Balancer.");
                return;
            }
            LoggingUtil.logAsync(Level.INFO,"Received message from Load Balancer or Client: " + message);

            JsonObject responseMessageJson = handleJsonRequest(message);
            handleResponse(loadBalancerChannel,responseMessageJson);

        } catch (IOException e) {
            LoggingUtil.logAsync(Level.SEVERE,"Failed to read from Load Balancer: " + e.getMessage());
        } finally {
            buffer.clear();
        }
    }

    protected void handleResponse(SocketChannel channel, JsonObject responseMessage) throws IOException{
        String type = responseMessage.get("type").getAsString();
        String msg = responseMessage.get("msg").getAsString();
        if (type.equals("lb")){
            if (!msg.equals("not send")){
                if (msg.equals(MessageUtil.getHealthCheckAwkMessage())){
                    ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes());
                    channel.write(buffer);
                }else{
                    sendToLoadBalancerMessage(msg);
                }
            }
        }else if (type.equals("client")){
            ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes());
            channel.write(buffer);
        }else{
            String errorMessage = responseMessage.get("msg").getAsString();
            LoggingUtil.logAsync(Level.SEVERE,"Failed to Make Response Message: " + errorMessage);
        }
    }
}
