package util;

import com.google.gson.JsonObject;

public class MessageUtil {

    public static String getServerRegisterMessage(String protocol,int port,int backgroundPort){
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("cmd","register");
        jsonObject.addProperty("protocol", protocol);
        jsonObject.addProperty("port", port);
        jsonObject.addProperty("bp", backgroundPort);
        return jsonObject.toString();
    }

    public static String getServerUnRegisterMessage(String protocol,int port,int backgroundPort){
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("cmd","unregister");
        jsonObject.addProperty("protocol", protocol);
        jsonObject.addProperty("port", port);
        jsonObject.addProperty("bp", backgroundPort);
        return jsonObject.toString();
    }

    public static String getSuccessAwkMessage(){
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("awk","successful");
        return jsonObject.toString();
    }

    public static String getFailedAwkMessage(String error){
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("awk","failed");
        jsonObject.addProperty("msg",error);
        return jsonObject.toString();
    }

    public static String getHealthCheckMessage(){
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("cmd","hello");
        return jsonObject.toString();
    }

    public static String getHealthCheckAwkMessage(){
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("awk","hello");
        return jsonObject.toString();
    }

    public static String getTCPServerData(int port,String request){
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("protocol", "tcp");
        jsonObject.addProperty("port", port);
        jsonObject.addProperty("message", "Hello from TCP Server on port " + port);
        jsonObject.addProperty("Echo Client Message", request);
        return jsonObject.toString()
                .replace(",", ",\n")
                .replace("{", "{\n")
                .replace("}", "\n}");
    }
    public static String getUDPServerData(int port,String request){
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("protocol", "udp");
        jsonObject.addProperty("port", port);
        jsonObject.addProperty("message", "Hello from UDP Server on port " + port);
        jsonObject.addProperty("Echo Client Message", request);
        return jsonObject.toString()
                .replace(",", ",\n")
                .replace("{", "{\n")
                .replace("}", "\n}");
    }
    public static String getAPIServerData(int port,String request){
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("protocol", "tcp");
        jsonObject.addProperty("port", port);
        jsonObject.addProperty("message", "Hello from API Server on port " + port);
        jsonObject.addProperty("Echo Client Message", request);
        return jsonObject.toString()
                .replace(",", ",\n")
                .replace("{", "{\n")
                .replace("}", "\n}");
    }
}