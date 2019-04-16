package layer_application;

import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;

import database.User;
import database.UserDatabase;
import layer_transport.TransportLayer;
import main.Client;

public class ApplicationLayer {
    private final String INSTRUCTION = "INSTRUCTION: Type /help for support. \n"
            + "Type /msg [RECEIVER NAME] [MESSAGE] for private message. \n"
            + "Type @msg [MESSAGE] for group chat message.\n"
            + "Type /exit for exitting.";
    private final String GROUP_CHAT_KEY = "@@";
    
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
        sys(INSTRUCTION);
        startTerminalHandler();
    }
    
    public void setLowerLayer(TransportLayer transportLayer) {
        this.lowerLayer = transportLayer;
    }
    
    public synchronized void receiveFromLowerLayer(String message, int address) { // -1 means from the system
        if (address == UserDatabase.SYSTEM_ID) {
            sys(message);
        } else {
            if (message.substring(0, 2).equals(GROUP_CHAT_KEY)) {
                message = message.substring(2);
                User sender = UserDatabase.getUser(address);
                print("GROUP - " + sender.getUsername() +": " + message);
            } else {
                User sender = UserDatabase.getUser(address);
                print(sender.getUsername() + ": " + message);
            }
            
        }
    }
    
    private void startTerminalHandler() {
        scanner = new Scanner(System.in);
        while (running) {
            if (scanner.hasNextLine()) {
                String userInput = scanner.nextLine();
                if (userInput.charAt(0) == '/' || userInput.charAt(0) == '@') {
                    handleCommand(userInput);
                } else {
                    sys("Invalid command. Type /help for support.");
                }
            }
        }
    }
    
    private void handleCommand(String userInput) {
        String[] splits = userInput.split(" ");
        if (splits.length > 0) {
            switch(splits[0]) {
            case "/help":
                sys(INSTRUCTION);
                break;

            case "/msg":
                sendMessage(splits);
                break;

            case "/exit":
                sys("Exitting");
                stopProgram();
                break;
                
            case "@msg":
                sendGroupMessage(splits);
                break;
                
            default:
                sys("First part: " + splits[0]);
                sys("Command: " + String.join(" ", userInput));
                sys("Invalid Command! Type /help for support. 1");
                break;
            }
        }
    }
    
    private void sendGroupMessage(String[] userInput) {
        if (userInput.length <= 1) {
            sys("Please input correct command. Type /help for support.");
            return;
        }
        String[] messageParts = Arrays.copyOfRange(userInput, 1, userInput.length);
        String message = GROUP_CHAT_KEY + String.join(" ", messageParts);
        sys("Sending group message...");
        lowerLayer.receiveFromUpperLayer(message,UserDatabase.GROUP_CHAT);
    }
    
    private void sendMessage(String[] userInput) {
        if (userInput.length <= 1) {
            sys("Please input correct command. Type /help for support.");
            return;
        }
        String name = userInput[1];
        if (name.equals( UserDatabase.getUser(client.getAddress()).getUsername())) {
            sys("Cannot send a message to yourself.");
            return;
        }
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
