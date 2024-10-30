package server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import util.LoggingUtil;
import util.MessageUtil;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public abstract class Server {

    protected static final Map<Integer, Server> serverInstance = new HashMap<>();

    public abstract String getProtocol();
    public abstract int getBackgroundPort();
    public abstract int getPort();
    public abstract void start();
    public abstract void registerLoadBalancer();
    public abstract void unregisterLoadBalancer();

    protected void printAllState(String type){

        String protocol = getProtocol().equals("http") ? "API" : getProtocol();

        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append("====================\n");
        output.append("Server State Print\n");
        output.append("This is ").append(protocol).append(" Server\n");
        output.append("Running on Client port : ").append(getPort()).append("\n");
        output.append("Running on Background port : ").append(getBackgroundPort()).append("\n");
        if (type.equals("register")){
            output.append("Registering load balancer\n");
        }else if (type.equals("unregister")){
            output.append("Unregistering load balancer and stop running server\n");
        }else{
            output.append("Health Check Request from load balancer\n");
        }
        output.append("====================\n");
        output.append("\n");
        System.out.print(output);
    }

    protected JsonObject handleJsonRequest(String request) {
        JsonObject jsonObject = new JsonObject();
        try {
            JsonReader reader = new JsonReader(new StringReader(request));
            reader.setLenient(true);
            JsonObject jsonRequest = JsonParser.parseReader(reader).getAsJsonObject();
            if (jsonRequest.has("cmd")) {
                String cmd = jsonRequest.get("cmd").getAsString();
                if ("hello".equals(cmd)) {
                    LoggingUtil.logAsync(Level.INFO,"Health check request from Load Balancer.");
                    jsonObject.addProperty("type","lb");
                    jsonObject.addProperty("msg", MessageUtil.getHealthCheckAwkMessage());
                    return jsonObject;
                }
            }else if (jsonRequest.has("awk")){
                String awk = jsonRequest.get("awk").getAsString();
                if (awk.equals("successful")){
                    LoggingUtil.logAsync(Level.INFO,"Awk is successful.");
                }else{
                    String msg = jsonRequest.get("msg").getAsString();
                    LoggingUtil.logAsync(Level.INFO,"Awk is failed. - "+msg);
                }
                jsonObject.addProperty("type","lb");
                jsonObject.addProperty("msg","not send");
                return jsonObject;
            }
            return jsonObject;
        } catch (JsonParseException | IllegalStateException e) {
            jsonObject.addProperty("type","client");
            if (getProtocol().equals("tcp")){
                jsonObject.addProperty("msg",MessageUtil.getTCPServerData(getPort(),request));
            }else if (getProtocol().equals("udp")){
                jsonObject.addProperty("msg",MessageUtil.getUDPServerData(getPort(),request));
            }else{
                jsonObject.addProperty("msg",MessageUtil.getAPIServerData(getPort(),request));
            }
            return jsonObject;
        }
    }
}
