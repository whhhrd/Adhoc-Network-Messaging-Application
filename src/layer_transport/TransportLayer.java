package layer_transport;

import data.Packet;
import data.PacketData;
import data.PacketMACK;
import data.PacketUACK;
import database.UserDatabase;
import layer_application.ApplicationLayer;
import layer_network.NetworkLayer;
import main.Client;

public class TransportLayer {
    private Client client;
    private ApplicationLayer upperLayer;
    private NetworkLayer lowerLayer;
    private int UID;
    private TimeoutThread[] timeoutThreadHolder;
    
    public TransportLayer(Client client) {
        this.client = client;
        this.UID = 0;
        this.timeoutThreadHolder = new TimeoutThread[8];
    }
    
    public void setUpperLayer(ApplicationLayer applicationLayer) {
        this.upperLayer = applicationLayer;
    }
    
    public void setLowerLayer(NetworkLayer networkLayer) {
        this.lowerLayer = networkLayer;
    }
    
    public synchronized void receiveFromLowerLayer(Packet packet) {
        if (packet instanceof PacketData) {
            PacketData thisPacket = (PacketData) packet;
            if (thisPacket.isFinPacket()) {
                upperLayer.receiveFromLowerLayer(thisPacket.getMessage(), thisPacket.getSrcAddress());
                PacketUACK UACKPacket = new PacketUACK(client.getAddress(),thisPacket.getSrcAddress(),thisPacket.getUID());
                System.out.println("TL - SENDING UACK");
                lowerLayer.receiveFromUpperLayer(UACKPacket);
            }
        } else if (packet instanceof PacketUACK) {
            PacketUACK thisPacket = (PacketUACK) packet;
            TimeoutThread timeoutThread = this.timeoutThreadHolder[thisPacket.getUID()];
            if (timeoutThread != null) {
                PacketData sentPacket = timeoutThread.getPacket();
                timeoutThread.stopTimer();
                timeoutThread.interrupt();
                upperLayer.receiveFromLowerLayer("Message to " + UserDatabase.getUser(sentPacket.getDesAddress()).getUsername() +
                        " sent successfully", UserDatabase.SYSTEM_ID);
            }
        } 
    }
    
    public synchronized void receiveFromUpperLayer(String textMessage, int receiverAddress) {
        updateUID();
        if (lowerLayer.candSendTo(receiverAddress, UID)) {
            // LET ASSUME TEXT IS ALWAYS LESS THAN 14 CHARACTER
            updateUID();
            PacketData packet = new PacketData(client.getAddress(),receiverAddress, UID, textMessage,true);
            TimeoutThread timeoutThread = new TimeoutThread(this,packet);
            timeoutThreadHolder[UID] = timeoutThread;
            timeoutThread.start();
        } else {
            upperLayer.receiveFromLowerLayer( "Can't not send the message to " 
        + UserDatabase.getUser(receiverAddress).getUsername(),UserDatabase.SYSTEM_ID);
        }
    }
    
    private TimeoutThread[] getTimeoutThreadHolder() {
        return this.timeoutThreadHolder;
    }
    
    private void updateUID() {
        if (this.UID == 7) {
            this.UID = 0;
        } else {
            this.UID++;
        }
    }
    
    private int getUID() {
        return this.UID;
    }
    
    private NetworkLayer getLowerLayer() {
        return this.lowerLayer;
    }
    
    private ApplicationLayer getUpperLayer() {
        return this.upperLayer;
    }
    
    private class TimeoutThread extends Thread {
        private PacketData packet;
        private TransportLayer transportLayer;
        private boolean running;
        
        private TimeoutThread(TransportLayer transportLayer, PacketData packet) {
            this.packet = (PacketData) packet;
            this.transportLayer = transportLayer;
            this.running = true;
        }
        
        private PacketData getPacket() {
            return this.packet;
        }
        
        private void stopTimer() {
            running = false;
        }
        
        @Override
        public void run() {
            int count = 0;
            while (running) {
                try {
                    if (transportLayer.getUID() == packet.getUID()) {
                        System.out.println("TP - SENDING DATA");
                        transportLayer.getLowerLayer().receiveFromUpperLayer(packet);
                    } else {
                        break;
                    }
                    sleep(20000);
                    count++;
                    if (count == 3) {
                        transportLayer.getUpperLayer().receiveFromLowerLayer( "Can't not send the message to " 
                                + UserDatabase.getUser(packet.getDesAddress()).getUsername(),UserDatabase.SYSTEM_ID);
                        transportLayer.getTimeoutThreadHolder()[packet.getUID()] = null;
                        break;
                    }
                } catch (InterruptedException e) {}
            }
        }
    }
}
