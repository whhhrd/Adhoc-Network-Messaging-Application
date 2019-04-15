package layer_network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import data.Packet;
import data.PacketConstant;
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
    private Map<Integer,Map<Integer, List<Integer>>> processedMap; // CLIENT - PACKET TYPE - UID
    private TimeoutThread[] timeoutThreadHolder;
    
    public NetworkLayer(Client client) {
        this.client = client;
        this.pathTable = new PathTable(client.getAddress());
        this.processedMap = new HashMap<Integer, Map<Integer, List<Integer>>>();
        this.timeoutThreadHolder = new TimeoutThread[8];
        processedMap.put(PacketConstant.TYPE_RREQ, new HashMap<Integer,List<Integer>>());
        processedMap.put(PacketConstant.TYPE_RREP, new HashMap<Integer,List<Integer>>());
        processedMap.put(PacketConstant.TYPE_DATA, new HashMap<Integer,List<Integer>>());
        processedMap.put(PacketConstant.TYPE_RERR, new HashMap<Integer,List<Integer>>());
        processedMap.put(PacketConstant.TYPE_UACK, new HashMap<Integer,List<Integer>>());
        processedMap.put(PacketConstant.TYPE_MACK, new HashMap<Integer,List<Integer>>());
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
        return makeRouteDiscovery(receiverAddress, upperLayer.getNewUID());
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
            timeoutThreadHolder[thisPacket.getUID()] = timeoutThread;
            timeoutThread.start();
        } else if (packet instanceof PacketUACK) {
            PacketUACK thisPacket = (PacketUACK) packet;
            thisPacket.setNextNode(pathTable.getNextNode(client.getAddress(), thisPacket.getOriginalSrcAddress(), client.getAddress()));
            lowerLayer.receiveFromUpperLayer(thisPacket);
        } 
    }
    
    private boolean makeRouteDiscovery(int desAddress, int UID) { // -- DONE
        Path emptyPath = new Path(client.getAddress(),desAddress);
        Packet routeDiscoveryPacket = new PacketRREQ(client.getAddress(),desAddress,UID,emptyPath); 
        int count = 0;
        Map<Integer,List<Integer>> RREQProcessedMap = processedMap.get(PacketConstant.TYPE_RREQ);
        if (RREQProcessedMap.containsKey(client.getAddress())) {
            RREQProcessedMap.get(client.getAddress()).add(UID);
        } else {
            List<Integer> processedList = new ArrayList<Integer>();
            processedList.add(UID);
            RREQProcessedMap.put(client.getAddress(), processedList);
        }
        while (count < 3) { // 5 - NUMBER OF MAXIMUM RESEND
            try {
                lowerLayer.receiveFromUpperLayer(routeDiscoveryPacket);
                Thread.sleep(5000); // 5000 ms - TIMEOUT TIME
                if (pathTable.canGoTo(desAddress)) {
                    return true;
                }
                count++;
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
            int nextNode = pathTable.getNextNode(client.getAddress(), packet.getDesAddress(), client.getAddress());
            if (nextNode != -1) {
                // Forward to next hop
                packet.setNextNode(nextNode);
                lowerLayer.receiveFromUpperLayer(packet);
            } else {
                // Send RRER
            }
        }
        
    }
    
    private void sendPacketMACK(PacketData packet) {
        int previousNode = pathTable.getPreviousNode(packet.getSrcAddress(),packet.getDesAddress(),packet.getNextNode());
        PacketMACK packetMACK = new PacketMACK(packet.getSrcAddress(),packet.getUID(),previousNode);
        saveToProcessedMap(packetMACK);
        lowerLayer.receiveFromUpperLayer(packetMACK);
    }
    
    private void handleReceivedPacketUACK(PacketUACK packet) {
        if (isSavedToProcessedMap(packet)) {
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
        if (isSavedToProcessedMap(packet)) {
            return;
        }
        if (packet.getNextNode() == client.getAddress()) {
            TimeoutThread timeoutThread = timeoutThreadHolder[packet.getOriginalUID()];
            if (timeoutThread != null) {
                timeoutThread.stopTimer();
                timeoutThreadHolder[packet.getOriginalUID()] = null;
            }
        } 
    }
    
    private void handleReceivedPacketRREP(PacketRREP packet) { // -- DONE
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
        Map<Integer,List<Integer>> RREPProcessedMap = processedMap.get(PacketConstant.TYPE_RREP);
        if (packet.getDesAddress() == client.getAddress()) {
            pathTable.updateTable(path);
            PacketRREP replyPacket = new PacketRREP(packet.getSrcAddress(),packet.getDesAddress(),packet.getUID(),path);
            if (RREPProcessedMap.containsKey(packet.getSrcAddress())) {
                RREPProcessedMap.get(packet.getSrcAddress()).add(packet.getUID());
            } else {
                List<Integer> processedList = new ArrayList<Integer>();
                processedList.add(packet.getUID());
                RREPProcessedMap.put(packet.getSrcAddress(), processedList);
            }
            lowerLayer.receiveFromUpperLayer(replyPacket);
        } else {
            PacketRREQ continueRREQPacket = new PacketRREQ(packet.getSrcAddress(),packet.getDesAddress(),packet.getUID(),path);
            lowerLayer.receiveFromUpperLayer(continueRREQPacket);
        }
    }
    
    private void handleReceivedPacketRRER(PacketRRER packet) {
        
    }
    
    private void saveToProcessedMap(Packet packet) {
        if (packet instanceof PacketData) {
            PacketData thisPacket = (PacketData) packet;
            Map<Integer,List<Integer>> DATAProcessedMap = processedMap.get(PacketConstant.TYPE_DATA);
            if (DATAProcessedMap.containsKey(thisPacket.getSrcAddress())) { 
                List<Integer> processedList = DATAProcessedMap.get(thisPacket.getSrcAddress());
                processedList.add(thisPacket.getUID());
                if (processedList.size() >=5) {
                    processedList.remove(0);
                }
            } else {
                List<Integer> processedList = new ArrayList<Integer>();
                processedList.add(thisPacket.getUID());
                DATAProcessedMap.put(thisPacket.getSrcAddress(), processedList);
            }
        } else if (packet instanceof PacketUACK) {
            PacketUACK thisPacket = (PacketUACK) packet;
            Map<Integer,List<Integer>> UACKProcessedMap = processedMap.get(PacketConstant.TYPE_UACK);
            if (UACKProcessedMap.containsKey(thisPacket.getSrcAddress())) { 
                List<Integer> processedList = UACKProcessedMap.get(thisPacket.getSrcAddress());
                processedList.add(thisPacket.getUID());
                if (processedList.size() >=5) {
                    processedList.remove(0);
                }
            } else {
                List<Integer> processedList = new ArrayList<Integer>();
                processedList.add(thisPacket.getUID());
                UACKProcessedMap.put(thisPacket.getSrcAddress(), processedList);
            }
        } else if (packet instanceof PacketMACK) {
            PacketMACK thisPacket = (PacketMACK) packet;
            Map<Integer,List<Integer>> MACKProcessedMap = processedMap.get(PacketConstant.TYPE_MACK);
            if (MACKProcessedMap.containsKey(thisPacket.getOriginalSrcAddress())) {
                List<Integer> processedList = MACKProcessedMap.get(thisPacket.getOriginalSrcAddress());
                processedList.add(thisPacket.getOriginalUID());
                if (processedList.size() >=5) {
                    processedList.remove(0);
                }
            } else {
                List<Integer> processedList = new ArrayList<Integer>();
                processedList.add(thisPacket.getOriginalSrcAddress());
                MACKProcessedMap.put(thisPacket.getOriginalSrcAddress(), processedList);
            }
        }
    }
    
    private boolean isSavedToProcessedMap(Packet packet) {
        if (packet instanceof PacketRREP) {
            PacketRREP thisPacket = (PacketRREP) packet;
            Map<Integer,List<Integer>> RREPProcessedMap = processedMap.get(PacketConstant.TYPE_RREP);
            if (RREPProcessedMap.containsKey(thisPacket.getSrcAddress())) { 
                List<Integer> processedList = RREPProcessedMap.get(thisPacket.getSrcAddress());
                if (processedList.contains(thisPacket.getUID())) {
                    return true;
                } else {
                    processedList.add(thisPacket.getUID());
                    if (processedList.size() >=5) {
                        processedList.remove(0);
                    }
                }
            } else {
                List<Integer> processedList = new ArrayList<Integer>();
                processedList.add(thisPacket.getUID());
                RREPProcessedMap.put(thisPacket.getSrcAddress(), processedList);
            }
        } else if (packet instanceof PacketRREQ) {
            PacketRREQ thisPacket = (PacketRREQ) packet;
            Map<Integer,List<Integer>> RREQProcessedMap = processedMap.get(PacketConstant.TYPE_RREQ);
            if (RREQProcessedMap.containsKey(thisPacket.getSrcAddress())) { 
                List<Integer> processedList = RREQProcessedMap.get(thisPacket.getSrcAddress());
                if (processedList.contains(thisPacket.getUID())) {
                    return true;
                } else {
                    processedList.add(thisPacket.getUID());
                    if (processedList.size() >=5) {
                        processedList.remove(0);
                    }
                }
            } else {
                List<Integer> processedList = new ArrayList<Integer>();
                processedList.add(thisPacket.getUID());
                RREQProcessedMap.put(thisPacket.getSrcAddress(), processedList);
            }
        } else if (packet instanceof PacketData) {
            PacketData thisPacket = (PacketData) packet;
            Map<Integer,List<Integer>> DATAProcessedMap = processedMap.get(PacketConstant.TYPE_DATA);
            if (DATAProcessedMap.containsKey(thisPacket.getSrcAddress())) { 
                List<Integer> processedList = DATAProcessedMap.get(thisPacket.getSrcAddress());
                if (processedList.contains(thisPacket.getUID())) {
                    return true;
                } else {
                    processedList.add(thisPacket.getUID());
                    if (processedList.size() >=5) {
                        processedList.remove(0);
                    }
                }
            } else {
                List<Integer> processedList = new ArrayList<Integer>();
                processedList.add(thisPacket.getUID());
                DATAProcessedMap.put(thisPacket.getSrcAddress(), processedList);
            }
        } else if (packet instanceof PacketUACK) {
            PacketUACK thisPacket = (PacketUACK) packet;
            Map<Integer,List<Integer>> UACKProcessedMap = processedMap.get(PacketConstant.TYPE_UACK);
            if (UACKProcessedMap.containsKey(thisPacket.getOriginalSrcAddress())) {
                List<Integer> processedList = UACKProcessedMap.get(thisPacket.getOriginalSrcAddress());
                if (processedList.contains(thisPacket.getUID())) {
                    return true;
                } else {
                    processedList.add(thisPacket.getUID());
                    if (processedList.size() >=5) {
                        processedList.remove(0);
                    }
                }
            } else {
                List<Integer> processedList = new ArrayList<Integer>();
                processedList.add(thisPacket.getUID());
                UACKProcessedMap.put(thisPacket.getOriginalSrcAddress(), processedList);
            }
        } else if (packet instanceof PacketMACK) {
            PacketMACK thisPacket = (PacketMACK) packet;
            Map<Integer,List<Integer>> MACKProcessedMap = processedMap.get(PacketConstant.TYPE_MACK);
            if (MACKProcessedMap.containsKey(thisPacket.getOriginalSrcAddress())) {
                List<Integer> processedList = MACKProcessedMap.get(thisPacket.getOriginalSrcAddress());
                if (processedList.contains(thisPacket.getOriginalUID())) {
                    return true;
                } else {
                    processedList.add(thisPacket.getOriginalUID());
                    if (processedList.size() >=5) {
                        processedList.remove(0);
                    }
                }
            } else {
                List<Integer> processedList = new ArrayList<Integer>();
                processedList.add(thisPacket.getOriginalUID());
                MACKProcessedMap.put(thisPacket.getOriginalSrcAddress(), processedList);
            }
        }
        
        return false;
    }
    
    private LinkLayer getLowerLayer() {
        return this.lowerLayer;
    }
    
    private class TimeoutThread extends Thread {
        private PacketData packet;
        private NetworkLayer networkLayer;
        private boolean running;
        
        private TimeoutThread(NetworkLayer networkLayer, Packet packet) {
            this.packet = (PacketData) packet;
            this.networkLayer = networkLayer;
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
                    networkLayer.getLowerLayer().receiveFromUpperLayer(packet); 
                    if (!running) {
                        break;
                    }
                    sleep(1500);
                    if (!running) {
                        break;
                    }
                    count++;
                    if (count == 2) { // 2
                        // send RRER
                        break;
                    }
                } catch (InterruptedException e) {}
            }
        }
    }

}
