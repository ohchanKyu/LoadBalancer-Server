import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

public class Main {

    private static SocketChannel mainChannel = null;
    private static SocketChannel clientChannel = null;
    private static boolean running = true;
    private static boolean isServerConnect = false;

    private static void printMenu() {
        System.out.println("=== Command Menu ===");
        System.out.println("1: Display the port for the load balancer");
        System.out.println("2: Select a protocol (http, tcp, udp) and connect to an available port");
        System.out.println("3: Send a message to the connected server");
        System.out.println("4: Print the Menu again");
        System.out.println("q: Quit the program");
        System.out.println("====================");
    }

    private static void getLoadBalancerOpenPort(){

        try {
            mainChannel = SocketChannel.open();
            mainChannel.configureBlocking(true);
            mainChannel.connect(new InetSocketAddress("localhost", 8080));
            ByteBuffer responseBuffer = ByteBuffer.wrap("case : 1".getBytes());
            mainChannel.write(responseBuffer);
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            int bytesRead = mainChannel.read(buffer);
            buffer.flip();
            String response = new String(buffer.array(), 0, bytesRead);
            System.out.println("--The port for the load balancer--");
            System.out.println(response);
        } catch (IOException e) {
            System.err.println("Failed to communicate with the LoadBalancer: " + e.getMessage());
        }
    }
    private static void getConnectionToServer(String protocol){

        try {
            clientChannel = SocketChannel.open();
            clientChannel.configureBlocking(true);
            clientChannel.connect(new InetSocketAddress("localhost", 8080));
            String requestMessage = "case : 2 - " + protocol;
            ByteBuffer responseBuffer = ByteBuffer.wrap(requestMessage.getBytes());
            clientChannel.write(responseBuffer);

            ByteBuffer buffer = ByteBuffer.allocate(1024);
            int bytesRead = clientChannel.read(buffer);
            buffer.flip();
            String response = new String(buffer.array(), 0, bytesRead);
            if (response.equals("No Available use Server")){
                System.out.println(response);
            }else{
                String[] parsedMessage = response.split(" - "); // 구분자로 파싱
                System.out.println("You can now connect the protocol (" + protocol + ") to an available port. - "+parsedMessage[1]);
                isServerConnect = true;
            }
        } catch (IOException e) {
            System.err.println("Failed to communicate with the LoadBalancer: " + e.getMessage());
        }
    }

    private static void sendMessageToServer(String message){

        try {
            String requestMessage = "case : 3 - " + message;
            ByteBuffer responseBuffer = ByteBuffer.wrap(requestMessage.getBytes());
            clientChannel.write(responseBuffer);

            ByteBuffer buffer = ByteBuffer.allocate(1024);
            int bytesRead = clientChannel.read(buffer);
            buffer.flip();
            String response = new String(buffer.array(), 0, bytesRead);
            if (response.equals("Fail connect to server")){
                isServerConnect = false;
                System.out.println("Connecting Server is down. please new connection");
            }else{
                System.out.println(response);
            }
        } catch (IOException e) {
            System.err.println("Failed to communicate with the server: " + e.getMessage());
        }
    }

    public static void main(String[] args)  {

        Scanner scanner = new Scanner(System.in);
        printMenu();
        while (running) {
            System.out.println();
            System.out.print("Input your command: ");
            String command = scanner.nextLine();

            switch (command) {
                case "1":
                    getLoadBalancerOpenPort();
                    break;
                case "2":
                    if (isServerConnect){
                        System.out.println("Already Connection to Server");
                        break;
                    }
                    System.out.print("Enter a protocol (http, tcp, udp): ");
                    String protocol = scanner.nextLine();
                    if (!protocol.equals("http") && !protocol.equals("tcp") && !protocol.equals("udp")) {
                        System.out.println("Invalid command. Please enter a valid protocol (http, tcp, udp).");
                        break;
                    }
                    getConnectionToServer(protocol);
                    break;
                case "3":
                    if (!isServerConnect){
                        System.out.println("Server is not connected yet. Please connect to the server first.");
                        break;
                    }
                    System.out.println("Enter the message you want to send to the server");
                    System.out.print("Message : ");
                    String message = scanner.nextLine();
                    if (message.length() > 500) {
                        System.out.println("The message is too long. Please enter a message within 500 characters.");
                        break;
                    }
                    sendMessageToServer(message);
                    break;
                case "4":
                    printMenu();
                    break;
                case "q":
                    System.out.println("Program terminated.");
                    try{
                        if (clientChannel != null && clientChannel.isOpen()) {
                            clientChannel.close();
                        }
                        if (mainChannel != null && mainChannel.isOpen()) {
                            mainChannel.close();
                        }
                    }catch (IOException e){
                        System.err.println("Error close channel - "+e.getMessage());
                    }
                    running = false;
                    break;
                default:
                    System.out.println("Invalid command. Please try again.");
            }
        }
        scanner.close();
    }
}