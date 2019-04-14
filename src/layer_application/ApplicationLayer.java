package layer_application;

import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;

import database.User;
import database.UserDatabase;
import layer_transport.TransportLayer;
import main.Client;

public class ApplicationLayer {
    private Client client;
    private TransportLayer lowerLayer;
    private boolean running;
    private Scanner scanner;
    private Map<Integer,String> usersMap;
    public static void main(String[] args) {
    }
    
    public ApplicationLayer(Client client) {
        this.client = client;
        this.running = true;
        usersMap = UserDatabase.getUsersMap();
    }
    
    public void start() {
        User myUser = UserDatabase.getUser(client.getAddress());
        sys("Welcome " + myUser.getUsername());
        startTerminalHandler();
    }
    
    public void setLowerLayer(TransportLayer transportLayer) {
        this.lowerLayer = transportLayer;
    }
    
    public synchronized void receiveFromLowerLayer(String message, int address) { // -1 means from the system
        if (address == UserDatabase.SYSTEM_ID) {
            sys(message);
        } else {
            User sender = UserDatabase.getUser(address);
            print(sender.getUsername() + ": " + message);
        }
    }
    
    private void startTerminalHandler() {
        scanner = new Scanner(System.in);
        while (running) {
            if (scanner.hasNextLine()) {
                String userInput = scanner.nextLine();
                if (userInput.charAt(0) == '/') {
                    handleCommand(userInput);
                } else {
                    sys("Invalid command");
                }
            }
        }
    }
    
    private void handleCommand(String userInput) {
        String[] splits = userInput.split(" ");
        if (splits.length > 0) {
            switch(splits[0]) {
            case "/help":
                sys("/help, /msg, /exit");
                break;

            case "/msg":
                sendMessage(splits);
                break;

            case "/exit":
                sys("Exitting");
                stopProgram();
                break;

            default:
                sys("Invalid Command!");
            }
        }
    }
    
    private void sendMessage(String[] userInput) {
        String name = userInput[1];
        String[] messageParts = Arrays.copyOfRange(userInput, 2, userInput.length);
        String message = String.join(" ", messageParts);
        if (this.usersMap.containsValue(name)) {
            sys("Sending message...");
            this.lowerLayer.receiveFromUpperLayer(message, UserDatabase.findIdByUserName(name));
        } else {
            sys("Please enter a correct username! Use command as: /msg [username] [message...]");
        }
    }
    
    private void stopProgram() {
        running = false;
        scanner.close();
    }
    
    private void print(String message) {
        System.out.println(message);
    }
    
    private void sys(String message) {
        System.err.println(message);
    }
}
