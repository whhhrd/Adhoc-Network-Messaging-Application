package layer_network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import data.Packet;
import data.PacketData;
import data.PacketMACK;
import data.PacketRREP;
import data.PacketRREQ;
import data.PacketRRER;
import data.PacketUACK;
import layer_link.LinkLayer;
import layer_transport.TransportLayer;
import main.Client;

public class NetworkLayer {
    private Client client;
    private TransportLayer upperLayer;
    private LinkLayer lowerLayer;
    private PathTable pathTable;
    private Map<Integer, List<TimeoutThread>> timeoutThreadHolderMap;
    private Map<Integer, List<Integer>> processedUACKMap;
    private Map<Integer, List<Integer[]>> processedMACKMap;
    private Map<Integer, List<Integer>> processedRREQMap;
    private Map<Integer, List<Integer>> processedRREPMap;
    private Map<Integer, List<Integer>> processedRRERMap;

    public NetworkLayer(Client client) {
        this.client = client;
        this.pathTable = new PathTable(client.getAddress());
        this.timeoutThreadHolderMap = new HashMap<Integer,List<TimeoutThread>>();
        processedUACKMap = new HashMap<Integer,List<Integer>>();
        processedMACKMap = new HashMap<Integer,List<Integer[]>>();
        processedRREQMap = new HashMap<Integer,List<Integer>>();
        processedRREPMap = new HashMap<Integer,List<Integer>>();
        processedRRERMap = new HashMap<Integer,List<Integer>>();
    }
    
    public void setUpperLayer(TransportLayer transportLayer) {
        this.upperLayer = transportLayer;
    }
    
    public void setLowerLayer(LinkLayer linkLayer) {
        this.lowerLayer = linkLayer;
    }
    
    public boolean candSendTo(int receiverAddress) {
        if (pathTable.canGoTo(receiverAddress)) {
            return true;
        } 
        return makeRouteDiscovery(receiverAddress);
    }
    
    public synchronized void receiveFromLowerLayer(Packet packet) {
        if (packet instanceof PacketData) {
            handleReceivedPacketData((PacketData) packet);
        } else if (packet instanceof PacketMACK) {
            handleReceivedPacketMACK((PacketMACK) packet);
        } else if (packet instanceof PacketRREP) {
            handleReceivedPacketRREP((PacketRREP) packet);
        } else if (packet instanceof PacketRREQ) {
            handleReceivedPacketRREQ((PacketRREQ) packet);
        } else if (packet instanceof PacketRRER) {
            handleReceivedPacketRRER((PacketRRER) packet);
        } else if (packet instanceof PacketUACK) {
            handleReceivedPacketUACK((PacketUACK) packet);
        }
    }
    
    public synchronized void receiveFromUpperLayer(Packet packet) {
        isSavedToProcessedMap(packet);
        if (packet instanceof PacketData) {
            PacketData thisPacket = (PacketData) packet;
            int nextNode = pathTable.getNextNode(client.getAddress(), thisPacket.getDesAddress(), client.getAddress());
            thisPacket.setNextNode(nextNode);
            TimeoutThread timeoutThread = new TimeoutThread(this,thisPacket);
            this.putToTimeoutThreadHolderMap(thisPacket.getSrcAddress(), timeoutThread); // ADD
            
            timeoutThread.start();
        } else if (packet instanceof PacketUACK) {
            PacketUACK thisPacket = (PacketUACK) packet;
            thisPacket.setNextNode(pathTable.getNextNode(client.getAddress(), thisPacket.getOriginalSrcAddress(), client.getAddress()));
            lowerLayer.receiveFromUpperLayer(thisPacket);
        } 
    }
    
    private boolean makeRouteDiscovery(int desAddress) { 
        int count = 0;
        while (count < 3) {
            int UID = upperLayer.getNewUID(desAddress);
            Path emptyPath = new Path(client.getAddress(),desAddress);
            Packet routeDiscoveryPacket = new PacketRREQ(client.getAddress(),desAddress,UID,emptyPath); 
            
            List<Integer> processedList;
            if (processedRREQMap.containsKey(client.getAddress())) {
                processedList = processedRREQMap.get(client.getAddress());
                int oldUIDIndex = processedList.indexOf(UID);
                if (oldUIDIndex != -1) {
                    processedList.remove(oldUIDIndex);
                }
            } else {
                processedList = new ArrayList<Integer>();
            }
            processedList.add(UID);
            processedRREQMap.put(client.getAddress(), processedList);
        
            try {
                count++;
                lowerLayer.receiveFromUpperLayer(routeDiscoveryPacket);
                Thread.sleep(7500); // 
                if (pathTable.canGoTo(desAddress)) {
                    return true;
                }
            } catch (InterruptedException e) {
                return false;
            } // Sleeping time
        }
        return false;
    }
    
    private void handleReceivedPacketData(PacketData packet) {
        if (packet.getNextNode() != client.getAddress()) {
            return;
        }
        if (isSavedToProcessedMap(packet)) {
            return;
        }
        if (packet.getDesAddress() == client.getAddress()) {
            sendPacketMACK(packet);
            upperLayer.receiveFromLowerLayer(packet);
        } else if (packet.getNextNode() == client.getAddress()) {
            sendPacketMACK(packet);
            int nextNode = pathTable.getNextNode(client.getAddress(), packet.getDesAddress(), client.getAddress());
            if (nextNode != -1) {
                packet.setNextNode(nextNode);
                TimeoutThread timeoutThread = new TimeoutThread(this,packet);
                this.putToTimeoutThreadHolderMap(packet.getSrcAddress(), timeoutThread);
                timeoutThread.start();
            } else {
                int receiveNode = pathTable.getPreviousNode(packet.getSrcAddress(), packet.getDesAddress(), client.getAddress());
                PacketRRER packetRRER = new PacketRRER(packet.getSrcAddress(),packet.getDesAddress(),packet.getUID(),client.getAddress(),receiveNode);
                lowerLayer.receiveFromUpperLayer(packetRRER);
            }
        }
        
    }
    
    private void sendPacketMACK(PacketData packet) {
        int previousNode = pathTable.getPreviousNode(packet.getSrcAddress(),packet.getDesAddress(),client.getAddress());
        PacketMACK packetMACK = new PacketMACK(packet.getSrcAddress(),packet.getUID(),client.getAddress(),previousNode);
        putToProcessedMACKMap(packetMACK);
        lowerLayer.receiveFromUpperLayer(packetMACK);
    }
    
    private void handleReceivedPacketUACK(PacketUACK packet) {
        if (packet.getOriginalSrcAddress() == client.getAddress() && isSavedToProcessedMap(packet)) {
            return;
        }
        
        if (packet.getOriginalSrcAddress() == client.getAddress()) {
            upperLayer.receiveFromLowerLayer(packet);
        } else if (packet.getNextNode() == client.getAddress()) {
            packet.setNextNode(pathTable.getNextNode(client.getAddress(), packet.getOriginalSrcAddress(), client.getAddress()));
            lowerLayer.receiveFromUpperLayer(packet);
        }
    }
   
    private void handleReceivedPacketMACK(PacketMACK packet) {
        if (packet.getReceiverNode() != client.getAddress()) {
            return;
        }
        
        if (isSavedToProcessedMap(packet)) {
            return;
        }

        if (packet.getReceiverNode() == client.getAddress()) {
            List<TimeoutThread> timeoutThreadList = this.timeoutThreadHolderMap.get(packet.getOriginalSrcAddress());
            if (timeoutThreadList != null) {
                for (int i = 0;i < timeoutThreadList.size();i++) {
                    TimeoutThread timeoutThread = timeoutThreadList.get(i);
                    if (timeoutThread.getPacket().getUID() == packet.getOriginalUID()) {
                        timeoutThread.stopTimer();
                        timeoutThreadList.remove(i);
                        timeoutThreadHolderMap.put(packet.getOriginalSrcAddress(), timeoutThreadList);
                        break;
                    }
                }
            }
        }
    }
    
    private void handleReceivedPacketRREP(PacketRREP packet) { 
        if (isSavedToProcessedMap(packet)) {
            return;
        }
        
        Path path = packet.getPath();
        if (path.containNode(client.getAddress())) {
            pathTable.updateTable(path);
            if (client.getAddress() != packet.getSrcAddress()) {
                lowerLayer.receiveFromUpperLayer(packet);
            }
        } 
    }
    
    private void handleReceivedPacketRREQ(PacketRREQ packet) {
        if (isSavedToProcessedMap(packet)) {
            return;
        }

        Path path = packet.getPath();
        path.addNode(client.getAddress());
        if (packet.getDesAddress() == client.getAddress()) {
            pathTable.updateTable(path);
            PacketRREP replyPacket = new PacketRREP(packet.getSrcAddress(),packet.getDesAddress(),packet.getUID(),path);
            putToProcessedRREPMap(replyPacket);
            lowerLayer.receiveFromUpperLayer(replyPacket);
        } else {
            PacketRREQ continueRREQPacket = new PacketRREQ(packet.getSrcAddress(),packet.getDesAddress(),packet.getUID(),path);
            lowerLayer.receiveFromUpperLayer(continueRREQPacket);
        }
    }
    
    private void handleReceivedPacketRRER(PacketRRER packet) {
        if (packet.getReceivingNode() != client.getAddress()) {
            return;
        }
        if (isSavedToProcessedMap(packet)) {
            return;
        }
        
        pathTable.updateBreakNode(packet.getBreakNode(),packet.getDesAddress());
        if (packet.getSrcAddress() != client.getAddress()) {
            int previousNode = pathTable.getPreviousNode(packet.getSrcAddress(), packet.getDesAddress(), client.getAddress());
            packet.setReceivingNode(previousNode);
            lowerLayer.receiveFromUpperLayer(packet);
        }
    }
    
    private boolean isSavedToProcessedMap(Packet packet) {
        if (packet instanceof PacketRREP) {
            PacketRREP thisPacket = (PacketRREP) packet;
            if (processedRREPMap.containsKey(thisPacket.getSrcAddress())) {
                if (processedRREPMap.get(thisPacket.getSrcAddress()).contains(thisPacket.getUID())) {
                    return true;
                }
            }
            putToProcessedRREPMap(thisPacket);
        } else if (packet instanceof PacketRREQ) {
            PacketRREQ thisPacket = (PacketRREQ) packet;
            if (processedRREQMap.containsKey(thisPacket.getSrcAddress())) {
                if (processedRREQMap.get(thisPacket.getSrcAddress()).contains(thisPacket.getUID())) {
                    return true;
                }
            }
            putToProcessedRREQMap(thisPacket);
        } else if (packet instanceof PacketUACK) {
            PacketUACK thisPacket = (PacketUACK) packet;
            if (processedUACKMap.containsKey(thisPacket.getOriginalSrcAddress())) {
                if (processedUACKMap.get(thisPacket.getOriginalSrcAddress()).contains(thisPacket.getUID())) {
                    return true;
                }
            }
            putToProcessedUACKMap(thisPacket);
        } else if (packet instanceof PacketMACK) {
            PacketMACK thisPacket = (PacketMACK) packet;
            if (processedMACKMap.containsKey(thisPacket.getSenderNode())) {
                Integer[] srcAndUID = new Integer[2];
                srcAndUID[0] = thisPacket.getOriginalSrcAddress();
                srcAndUID[1] = thisPacket.getOriginalUID();
                if (processedMACKMap.get(thisPacket.getSenderNode()).contains(srcAndUID)) {
                    return true;
                }
            }
            putToProcessedMACKMap(thisPacket);
        } else if (packet instanceof PacketRRER) {
            PacketRRER thisPacket = (PacketRRER) packet;
            if (processedRRERMap.containsKey(thisPacket.getSrcAddress())) {
                if (processedRRERMap.get(thisPacket.getSrcAddress()).contains(thisPacket.getUID())) {
                    return true;
                }
            }
            putToProcessedRRERMap(thisPacket);
        }
        
        return false;
    }
    
    private void putToTimeoutThreadHolderMap(int srcAddress, TimeoutThread timeoutThread) {
        List<TimeoutThread> timeoutThreadList;
        if (timeoutThreadHolderMap.containsKey(srcAddress)) {
            timeoutThreadList = timeoutThreadHolderMap.get(srcAddress);
        } else {
            timeoutThreadList = new ArrayList<TimeoutThread>();
        }
        timeoutThreadList.add(timeoutThread);
        timeoutThreadHolderMap.put(srcAddress, timeoutThreadList);
    }
    
    private void putToProcessedRREPMap(PacketRREP packet) {
        List<Integer> processedList;
        if (processedRREPMap.containsKey(packet.getSrcAddress())) {
            processedList = processedRREPMap.get(packet.getSrcAddress());
        } else {
            processedList = new ArrayList<Integer>();
        }
        processedList.add(packet.getUID());
        if (processedList.size() >= 3) {
            processedList.remove(0);
        }
        processedRREPMap.put(packet.getSrcAddress(), processedList);
    }
    
    private void putToProcessedRRERMap(PacketRRER packet) {
        List<Integer> processedList;
        if (processedRRERMap.containsKey(packet.getSrcAddress())) {
            processedList = processedRRERMap.get(packet.getSrcAddress());
        } else {
            processedList = new ArrayList<Integer>();
        }
        processedList.add(packet.getUID());
        if (processedList.size() >= 3) {
            processedList.remove(0);
        }
        processedRRERMap.put(packet.getSrcAddress(), processedList);
    }
    
    private void putToProcessedUACKMap(PacketUACK packet) {
        List<Integer> processedList;
        if (processedUACKMap.containsKey(packet.getOriginalSrcAddress())) {
            processedList = processedUACKMap.get(packet.getOriginalSrcAddress());
        } else {
            processedList = new ArrayList<Integer>();
        }
        processedList.add(packet.getUID());
        if (processedList.size() >= 3) {
            processedList.remove(0);
        }
        processedUACKMap.put(packet.getOriginalSrcAddress(), processedList);
    }
    
    private void putToProcessedMACKMap(PacketMACK packet) {
        List<Integer[]> processedList;
        if (processedMACKMap.containsKey(packet.getSenderNode())) {
            processedList = processedMACKMap.get(packet.getSenderNode());
        } else {
            processedList = new ArrayList<Integer[]>();
        }
        Integer[] srcAndUID = new Integer[2];
        srcAndUID[0] = packet.getOriginalSrcAddress();
        srcAndUID[1] = packet.getOriginalUID();
        processedList.add(srcAndUID);
        if (processedList.size() >= 4) {
            processedList.remove(0);
        }
        processedMACKMap.put(packet.getOriginalSrcAddress(), processedList);
    }
    
    private void putToProcessedRREQMap(PacketRREQ packet) {
        List<Integer> processedList;
        if (processedRREQMap.containsKey(packet.getSrcAddress())) {
            processedList = processedRREQMap.get(packet.getSrcAddress());
        } else {
            processedList = new ArrayList<Integer>();
        }
        processedList.add(packet.getUID());
        if (processedList.size() >= 3) {
            processedList.remove(0);
        }
        processedRREQMap.put(packet.getSrcAddress(), processedList);
    }
    
    
    private LinkLayer getLowerLayer() {
        return this.lowerLayer;
    }
    
    private class TimeoutThread extends Thread {
        private PacketData packet;
        private NetworkLayer networkLayer;
        
        private TimeoutThread(NetworkLayer networkLayer, Packet packet) {
            this.packet = (PacketData) packet;
            this.networkLayer = networkLayer;
        }
        
        private void stopTimer() {
            this.interrupt();
        }
        
        private PacketData getPacket() {
            return this.packet;
        }
        
        @Override
        public void run() {
            int count = 0;
            while (true) {
                try {
                    networkLayer.getLowerLayer().receiveFromUpperLayer(packet); 
                    count++;
                    sleep(10000); 
                    if (count == 3) { 
                        break;
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

}
