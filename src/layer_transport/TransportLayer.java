package layer_transport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import data.Packet;
import data.PacketData;
import data.PacketUACK;
import database.UserDatabase;
import layer_application.ApplicationLayer;
import layer_network.NetworkLayer;
import main.Client;

public class TransportLayer {
    private final static boolean IS_FIN = true;
    private final static boolean IS_NOT_FIN = false;
    
    private Client client;
    private ApplicationLayer upperLayer;
    private NetworkLayer lowerLayer;
    private int UID;
    private TimeoutThread[] timeoutThreadHolder;
    private PacketData[] savedSendingPacketData;
    private Map<Integer,List<PacketData>> savedReceivingPacketData;
    private Map<Integer, List<Integer>> processedDataMap;
    
    public TransportLayer(Client client) {
        this.client = client;
        this.UID = 0;
        this.timeoutThreadHolder = new TimeoutThread[8];
        this.savedSendingPacketData = new PacketData[8];
        this.savedReceivingPacketData = new HashMap<Integer,List<PacketData>>();
        this.processedDataMap = new HashMap<Integer,List<Integer>>();
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
            if (!processedDataMap.containsKey(thisPacket.getSrcAddress()) 
                    || !processedDataMap.get(thisPacket.getSrcAddress()).contains(thisPacket.getUID())) {
                saveToReceivingPacketData(thisPacket);
                putToProcessedDataMap(thisPacket);
                if (thisPacket.isFinPacket()) {
                    List<PacketData> savedReceivedPacketDataList = this.savedReceivingPacketData.get(thisPacket.getSrcAddress());
                    String textMessage = combineReceivedPacketDataToTextMessage(savedReceivedPacketDataList);
                    this.savedReceivingPacketData.remove(thisPacket.getSrcAddress());
                    upperLayer.receiveFromLowerLayer(textMessage, thisPacket.getSrcAddress());
                }
            }

            PacketUACK UACKPacket = new PacketUACK(client.getAddress(),thisPacket.getSrcAddress(),thisPacket.getUID());
            lowerLayer.receiveFromUpperLayer(UACKPacket);
        } else if (packet instanceof PacketUACK) {
            PacketUACK thisPacket = (PacketUACK) packet;
            TimeoutThread timeoutThread = this.timeoutThreadHolder[thisPacket.getUID()];
            if (timeoutThread != null) {
                PacketData sentPacketData = timeoutThread.getPacket();
                timeoutThread.stopTimer();
                this.savedSendingPacketData[sentPacketData.getUID()] = null;
                if (sentPacketData.isFinPacket()) {
                    upperLayer.receiveFromLowerLayer("Message to " + UserDatabase.getUser(sentPacketData.getDesAddress()).getUsername() +
                            " sent successfully", UserDatabase.SYSTEM_ID);
                } else {
                    int nextUID = getNextUID(sentPacketData.getUID());
                    PacketData nextPacketData = this.savedSendingPacketData[nextUID];
                    if (nextPacketData == null) {
                        System.out.println("NEXT PACKET DATA IS NULL WITH UID: " + nextUID);
                    }
                    TimeoutThread nextTimeoutThread = new TimeoutThread(this,nextPacketData);
                    timeoutThreadHolder[nextPacketData.getUID()] = nextTimeoutThread;
                    updateUID();
                    nextTimeoutThread.start();
                }
            }
        } 
    }
    
    public synchronized void receiveFromUpperLayer(String textMessage, int receiverAddress) {
        if (lowerLayer.candSendTo(receiverAddress)) {
            updateUID();
            saveMessageToSendingArray(textMessage,receiverAddress); //
            PacketData packet = this.savedSendingPacketData[UID]; //
            TimeoutThread timeoutThread = new TimeoutThread(this,packet);
            timeoutThreadHolder[packet.getUID()] = timeoutThread;
            if (timeoutThread != null) {
                timeoutThread.start();
            }
        } else {
            upperLayer.receiveFromLowerLayer( "Cannot send the message to " 
        + UserDatabase.getUser(receiverAddress).getUsername(),UserDatabase.SYSTEM_ID);
        }
    }
    
    public int getNextUID(int currentUID) {
        if (currentUID == 7) {
            return 0;
        }
        return currentUID+1;
    }
    
    public int getNewUID() {
        updateUID();
        return this.UID;
    }
    
    private String combineReceivedPacketDataToTextMessage(List<PacketData> packetDataList) {
        String result = "";
        for (PacketData packet: packetDataList) {
            result += packet.getMessage();
        }
        return result;
    }
    
    private void putToProcessedDataMap(PacketData packet) {
        List<Integer> processedList;
        if (processedDataMap.containsKey(packet.getSrcAddress())) {
            processedList = processedDataMap.get(packet.getSrcAddress());
        } else {
            processedList = new ArrayList<Integer>();
        }
        processedList.add(packet.getUID());
        if (processedList.size() >= 3) {
            processedList.remove(0);
        }
        processedDataMap.put(packet.getSrcAddress(), processedList);
    }
    
    private void saveToReceivingPacketData(PacketData packet) {
        List<PacketData> packetDataList;
        if (this.savedReceivingPacketData.containsKey(packet.getSrcAddress())) {
            packetDataList = this.savedReceivingPacketData.get(packet.getSrcAddress());
        } else {
            packetDataList = new ArrayList<PacketData>();
        }
        packetDataList.add(packet);
        this.savedReceivingPacketData.put(packet.getSrcAddress(),packetDataList);
    }
    
    private void saveMessageToSendingArray(String textMessage, int receiverAddress) {
        int nextUID = UID;
        while (textMessage.length() > 14) {
            String textPart = textMessage.substring(0, 14);
            PacketData packet = new PacketData(client.getAddress(),receiverAddress,nextUID,textPart,IS_NOT_FIN);
            this.savedSendingPacketData[nextUID] = packet;
            nextUID = getNextUID(nextUID);
            textMessage = textMessage.substring(14);
        }
        PacketData finalPacket = new PacketData(client.getAddress(),receiverAddress,nextUID,textMessage,IS_FIN);
        this.savedSendingPacketData[nextUID] = finalPacket;
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
        
        private TimeoutThread(TransportLayer transportLayer, PacketData packet) {
            this.setName("TIMEOUT-THREAD-TRANSPORT LAYER");
            this.packet = (PacketData) packet;
            this.transportLayer = transportLayer;
        }
        
        private PacketData getPacket() {
            return this.packet;
        }
        
        private void stopTimer() {
            this.interrupt();
        }
        
        @Override
        public void run() {
            int count = 0;
            while (true) {
                try {
                    if (transportLayer.getUID() == packet.getUID()) {
                        transportLayer.getLowerLayer().receiveFromUpperLayer(packet);
                    } else {
                        break;
                    }
                    sleep(30000);
                    count++;
                    if (count == 3) {
                        transportLayer.getUpperLayer().receiveFromLowerLayer( "Cannot send the message to " 
                                + UserDatabase.getUser(packet.getDesAddress()).getUsername(),UserDatabase.SYSTEM_ID);
                        transportLayer.getTimeoutThreadHolder()[packet.getUID()] = null;
                        break;
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
}
