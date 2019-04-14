package main;

import java.util.concurrent.BlockingQueue;

import data.Message;
import layer_application.ApplicationLayer;
import layer_link.LinkLayer;
import layer_network.NetworkLayer;
import layer_transport.TransportLayer;


public class Protocol {
 // The host to connect to. Set this to localhost when using the audio interface tool.
    private static String SERVER_IP = "netsys2.ewi.utwente.nl"; //"127.0.0.1";
    // The port to connect to. 8954 for the simulation server.
    private static int SERVER_PORT = 8954;
    // The frequency to use.
    private static int frequency = 777;
    private Client client;
    
    private ApplicationLayer applicationLayer;
    private TransportLayer transportLayer;
    private NetworkLayer networkLayer;
    private LinkLayer linkLayer;

    public Protocol(String server_ip, int server_port, int frequency){
        client = new Client(SERVER_IP, SERVER_PORT, frequency); // Give the client the Queues to use
        applicationLayer = new ApplicationLayer(client);
        transportLayer = new TransportLayer(client);
        networkLayer = new NetworkLayer(client);
        linkLayer = new LinkLayer(client);
        
        applicationLayer.setLowerLayer(transportLayer);
        transportLayer.setUpperLayer(applicationLayer);
        transportLayer.setLowerLayer(networkLayer);
        networkLayer.setLowerLayer(linkLayer);
        networkLayer.setUpperLayer(transportLayer);
        linkLayer.setUpperLayer(networkLayer);
        linkLayer.setLowerLayer(this);
        
        new receiveThread(client.getReceivedQueue(), linkLayer).start(); // Start thread to handle received messages!

        // handle sending from stdin from this thread.
        applicationLayer.start();
    }

    public static void main(String args[]) {
        if(args.length > 0){
            frequency = Integer.parseInt(args[0]);
        }
        new Protocol(SERVER_IP, SERVER_PORT, frequency);        
    }
    
    public void receiveFromUpperLayer(Message message) {
        if (message != null) {
            try {
                client.getSendingQueue().put(message);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("PL - NULL MESSAGE");
        }
    }

    private class receiveThread extends Thread {
        private BlockingQueue<Message> receivedQueue;
        private LinkLayer linkLayer;

        public receiveThread(BlockingQueue<Message> receivedQueue, LinkLayer linkLayer){
            super();
            this.setName("Thread-Receiving");
            this.receivedQueue = receivedQueue;
            this.linkLayer = linkLayer;
        }

        public void run(){
            while(true) {
                try{
                    Message msg = receivedQueue.take();
                    linkLayer.receiveMessage(msg);
                } catch (InterruptedException e){
                    System.err.println("Failed to take from queue: "+e);
                }                
            }
        }
    }
}
