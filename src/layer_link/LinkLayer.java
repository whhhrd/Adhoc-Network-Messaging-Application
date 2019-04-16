package layer_link;

import java.nio.ByteBuffer;
import java.util.Vector;

import data.Message;
import data.MessageType;
import data.Packet;
import data.PacketConstant;
import data.PacketData;
import data.PacketMACK;
import data.PacketRREP;
import data.PacketRREQ;
import data.PacketRRER;
import data.PacketUACK;
import layer_network.NetworkLayer;
import layer_network.Path;
import main.Client;
import main.Protocol;

public class LinkLayer {
    private NetworkLayer upperLayer;
    private Protocol lowerLayer;
    private boolean mediumFree;
    private boolean sending;
    private QueueThread queueThread;
    private Client client;
    private Vector<Message> messages;
    
    public LinkLayer(Client client) { // -- DONE
        this.client = client;
        this.mediumFree = true;
        this.sending = false;
        messages = new Vector<Message>();
        queueThread = new QueueThread(this);
        queueThread.start();
    }
    
    public synchronized void receiveFromUpperLayer(Packet packet) { // -- DONE
        try {
            putMessage(turnPacketToMessage(packet));
        } catch (InterruptedException e) {}
    }
    
    public void setUpperLayer(NetworkLayer networkLayer) { // -- DONE
        this.upperLayer = networkLayer;
    }
    
    public void setLowerLayer(Protocol protocol) {
        this.lowerLayer = protocol;
    }
    
    public synchronized void sendToLowerLayer(Message message) {
        lowerLayer.receiveFromUpperLayer(message);
    }
    
    public void receiveMessage(Message message) { // -- DONE
        if (message.getType() == MessageType.BUSY) {
            synchronized (messages) {
                this.mediumFree = false;
            }
        } else if (message.getType() == MessageType.SENDING) {
            synchronized (messages) {
                this.sending = true;
            }
        } else if (message.getType() == MessageType.FREE) {
            synchronized (messages) {
                this.mediumFree = true;
            }
        } else if (message.getType() == MessageType.DONE_SENDING) {
            synchronized (messages) {
                this.sending = false;
            }
        } else if (message.getType() == MessageType.DATA || message.getType() == MessageType.DATA_SHORT) {
            upperLayer.receiveFromLowerLayer(turnMessageToPacket(message));
        }
    }
    
    private Packet turnMessageToPacket(Message message) { // -- DONE
        assert message.getType() == MessageType.DATA || message.getType() == MessageType.DATA_SHORT;
        byte[] messageData = message.getData().array();

        String bitString = String.format("%8s", Integer.toBinaryString(messageData[0] & 0xFF)).replace(' ', '0') + 
                String.format("%8s", Integer.toBinaryString(messageData[1] & 0xFF)).replace(' ', '0');
        switch (bitString.substring(0, 3)) {
        case PacketConstant.BITSTRING_RREQ: 
            int RREQSrcAddress = turnBitStringToNumber(bitString.substring(6,8));
            int RREQDesAddress = turnBitStringToNumber(bitString.substring(8,10));
            int RREQUID = turnBitStringToNumber(bitString.substring(3,6));
            Path RREQPath = new Path(RREQSrcAddress,RREQDesAddress);
            for (int i = 10;i < 16;i += 2) {
                int nextNode = turnBitStringToNumber(bitString.substring(i,i+2));
                if (nextNode != RREQSrcAddress) {
                    RREQPath.addNode(nextNode);
                } else {
                    break;
                }
            }
            return new PacketRREQ(RREQSrcAddress,RREQDesAddress,RREQUID,RREQPath);
        case PacketConstant.BITSTRING_RREP: 
            int RREPSrcAddress = turnBitStringToNumber(bitString.substring(6,8));
            int RREPDesAddress = turnBitStringToNumber(bitString.substring(8,10));
            int RREPUID = turnBitStringToNumber(bitString.substring(3,6));
            Path RREPPath = new Path(RREPSrcAddress,RREPDesAddress);
            for (int i = 10;i < 16;i += 2) {
                int nextNode = turnBitStringToNumber(bitString.substring(i,i+2));
                if (nextNode != RREPSrcAddress) {
                    RREPPath.addNode(nextNode);
                } else {
                    break;
                }
            }
            return new PacketRREP(RREPSrcAddress,RREPDesAddress,RREPUID,RREPPath);
        case PacketConstant.BITSTRING_DATA: 
            if (messageData.length != 16) {
                System.out.println("LINK LAYER - FAIL TO TAKE DATA PACKET");
            } else {
                int dataSrcAddress = turnBitStringToNumber(bitString.substring(6,8));
                int dataDesAddress = turnBitStringToNumber(bitString.substring(8,10));
                int dataUID = turnBitStringToNumber(bitString.substring(3,6));
                boolean dataIsFin = bitString.charAt(10) == '1';
                int nextHop = turnBitStringToNumber(bitString.substring(11,13));
                String textMessage = "";
                for(int i = 0; i < 14; i++) {
                    textMessage += (char) messageData[2 + i];
                }
                return new PacketData(dataSrcAddress,dataDesAddress,dataUID,textMessage,dataIsFin,nextHop);
            }
            break;
        case PacketConstant.BITSTRING_RERR: // RRER
            int RRERUID = turnBitStringToNumber(bitString.substring(3,6));
            int RRERSrcAddress = turnBitStringToNumber(bitString.substring(6,8));
            int RRERDesAddress = turnBitStringToNumber(bitString.substring(8,10));
            int RRERBreakNode = turnBitStringToNumber(bitString.substring(10,12));
            int RRERReceivedNode = turnBitStringToNumber(bitString.substring(12,14));
            return new PacketRRER(RRERSrcAddress,RRERDesAddress,RRERUID,RRERBreakNode,RRERReceivedNode);
        case PacketConstant.BITSTRING_UACK: // UACK
            int UACKOriginalUID = turnBitStringToNumber(bitString.substring(3,6));
            int UACKOriginalSrcAddress = turnBitStringToNumber(bitString.substring(6,8));
            int UACKSrcAddress = turnBitStringToNumber(bitString.substring(8,10));
            int nextNode = turnBitStringToNumber(bitString.substring(10,12));
            return new PacketUACK(UACKSrcAddress,UACKOriginalSrcAddress,UACKOriginalUID,nextNode);
        case PacketConstant.BITSTRING_MACK: // MACK
            int MACKOriginalUID = turnBitStringToNumber(bitString.substring(3,6));
            int MACKOriginalSrcAddress = turnBitStringToNumber(bitString.substring(6,8));
            int senderAddress = turnBitStringToNumber(bitString.substring(8,10));
            int receiverAddress = turnBitStringToNumber(bitString.substring(10,12));
            return new PacketMACK(MACKOriginalSrcAddress,MACKOriginalUID,senderAddress,receiverAddress);
        }
        return null;
    }
    
    private Message turnPacketToMessage(Packet packet) { // -- DONE
        if (packet instanceof PacketData) {
            return turnPacketDataToMessage((PacketData) packet); // DATA
        } else if (packet instanceof PacketMACK) {
            return turnPacketMACKToMessage((PacketMACK) packet); // DATA SHORT
        } else if (packet instanceof PacketRREP) {
            return turnPacketRREPToMessage((PacketRREP) packet); // DATA SHORT
        } else if (packet instanceof PacketRREQ) {
            return turnPacketRREQToMessage((PacketRREQ) packet); // DATA SHORT
        } else if (packet instanceof PacketRRER) {
            return turnPacketRRERToMessage((PacketRRER) packet); // DATA SHORT
        } else if (packet instanceof PacketUACK) {
            return turnPacketUACKToMessage((PacketUACK) packet); // DATA SHORT
        }
        
        return null;
    }
    
    private boolean canSendMessage() { // -- DONE
        boolean canSend;
        synchronized (messages) {
            canSend = mediumFree && !sending; 
        }
        return canSend;
    }
    
    private Message turnPacketDataToMessage(PacketData packet) { // -- DONE
        String bitString = "";
        bitString += PacketConstant.BITSTRING_DATA;
        bitString += turnNumberToBitString(packet.getUID(),3);
        bitString += turnNumberToBitString(packet.getSrcAddress(),2);
        bitString += turnNumberToBitString(packet.getDesAddress(),2);
        bitString += (packet.isFinPacket()) ? "1":"0";
        bitString += turnNumberToBitString(packet.getNextNode(),2);
        bitString += "000";
        byte[] messageData = new byte[16];
        messageData[0] = (byte) Integer.parseInt(bitString.substring(0, 8), 2);
        messageData[1] = (byte) Integer.parseInt(bitString.substring(8,16), 2);
        for(int i = 0; i < packet.getMessage().length(); i++) {
            messageData[2 + i] = (byte) packet.getMessage().charAt(i);
        }
        return new Message(MessageType.DATA,ByteBuffer.wrap(messageData));
    }
    
    private Message turnPacketRREPToMessage(PacketRREP packet) { // -- DONE
        String bitString = "";
        bitString += PacketConstant.BITSTRING_RREP;
        bitString += turnNumberToBitString(packet.getUID(),3);
        bitString += turnNumberToBitString(packet.getSrcAddress(),2);
        bitString += turnNumberToBitString(packet.getDesAddress(),2);
        Path path = packet.getPath();
        for (int i = 1;i < 4;i++) {
            if (path.getPath().size() > i) {
                bitString += turnNumberToBitString(path.getPath().get(i),2);
            } else {
                bitString += turnNumberToBitString(packet.getSrcAddress(),2);
            }
        }
        byte[] messageData = new byte[2];
        messageData[0] = (byte) Integer.parseInt(bitString.substring(0, 8), 2);
        messageData[1] = (byte) Integer.parseInt(bitString.substring(8,16), 2);
        return new Message(MessageType.DATA_SHORT,ByteBuffer.wrap(messageData));
    }
    
    private Message turnPacketMACKToMessage(PacketMACK packet) { // -- DONE
        String bitString = "";
        bitString += PacketConstant.BITSTRING_MACK;
        bitString += turnNumberToBitString(packet.getOriginalUID(),3);
        bitString += turnNumberToBitString(packet.getOriginalSrcAddress(),2);
        bitString += turnNumberToBitString(packet.getSenderNode(),2);
        bitString += turnNumberToBitString(packet.getReceiverNode(),2);
        bitString += "0000";
        byte[] messageData = new byte[2];
        messageData[0] = (byte) Integer.parseInt(bitString.substring(0, 8), 2);
        messageData[1] = (byte) Integer.parseInt(bitString.substring(8,16), 2); 
        return new Message(MessageType.DATA_SHORT,ByteBuffer.wrap(messageData));
    }
    private Message turnPacketRREQToMessage(PacketRREQ packet) { // -- DONE
        String bitString = "";
        bitString += PacketConstant.BITSTRING_RREQ;
        bitString += turnNumberToBitString(packet.getUID(),3);
        bitString += turnNumberToBitString(packet.getSrcAddress(),2);
        bitString += turnNumberToBitString(packet.getDesAddress(),2);
        Path path = packet.getPath();
        for (int i = 1;i < 4;i++) {
            if (path.getPath().size() > i) {
                bitString += turnNumberToBitString(path.getPath().get(i),2);
            } else {
                bitString += turnNumberToBitString(packet.getSrcAddress(),2);
            }
        }
        byte[] messageData = new byte[2];
        messageData[0] = (byte) Integer.parseInt(bitString.substring(0, 8), 2);
        messageData[1] = (byte) Integer.parseInt(bitString.substring(8,16), 2);
        return new Message(MessageType.DATA_SHORT,ByteBuffer.wrap(messageData));
    }
    private Message turnPacketRRERToMessage(PacketRRER packet) { // -- DONE
        String bitString = "";
        bitString += PacketConstant.BITSTRING_RERR;
        bitString += turnNumberToBitString(packet.getUID(),3);
        bitString += turnNumberToBitString(packet.getSrcAddress(),2);
        bitString += turnNumberToBitString(packet.getDesAddress(),2);
        bitString += turnNumberToBitString(packet.getBreakNode(),2);
        bitString += turnNumberToBitString(packet.getReceivingNode(),2);
        bitString += "00";
        byte[] messageData = new byte[2];
        messageData[0] = (byte) Integer.parseInt(bitString.substring(0, 8), 2);
        messageData[1] = (byte) Integer.parseInt(bitString.substring(8,16), 2);
        
        return new Message(MessageType.DATA_SHORT,ByteBuffer.wrap(messageData));
    }
    private Message turnPacketUACKToMessage(PacketUACK packet) { // -- DONE
        String bitString = "";
        bitString += PacketConstant.BITSTRING_UACK;
        bitString += turnNumberToBitString(packet.getUID(),3);
        bitString += turnNumberToBitString(packet.getOriginalSrcAddress(),2);
        bitString += turnNumberToBitString(packet.getSrcAddress(),2);
        bitString += turnNumberToBitString(packet.getNextNode(),2);
        bitString += "0000";
        byte[] messageData = new byte[2];
        messageData[0] = (byte) Integer.parseInt(bitString.substring(0, 8), 2);
        messageData[1] = (byte) Integer.parseInt(bitString.substring(8,16), 2);
        return new Message(MessageType.DATA_SHORT,ByteBuffer.wrap(messageData));
    }
    
    
    
    private int turnBitStringToNumber(String bitString) { // -- DONE
        switch (bitString) {
        case "00":
        case "000":
            return 0;
        case "01":
        case "001":
            return 1;
        case "10":
        case "010":
            return 2;
        case "11":
        case "011":
            return 3;
        case "100": return 4;
        case "101": return 5;
        case "110": return 6;
        case "111": return 7;
        }
        
        return -1;
    }
    
    private String turnNumberToBitString(int number, int length) { // -- DONE
        switch (number) {
        case 0: 
            if (length == 2) {return "00";}
            else if (length == 3) {return "000";}
            else { break; }
        case 1:
            if (length == 2) {return "01";}
            else if (length == 3) {return "001";}
            else { break; }
        case 2:
            if (length == 2) {return "10";}
            else if (length == 3) {return "010";}
            else { break; }
        case 3:
            if (length == 2) {return "11";}
            else if (length == 3) {return "011";}
            else { break; }
        case 4:
            return "100";
        case 5:
            return "101";
        case 6:
            return "110";
        case 7:
            return "111";
        }
        
        return "";
    }
    
    private void putMessage(Message message) throws InterruptedException {
        synchronized (messages) {
            messages.addElement(message);
            messages.notify();
        }
    }
    
    private Message getMessage() throws InterruptedException {
        synchronized (messages) {
            while (messages.size() == 0) {
                messages.wait();
            }
        }
        
        synchronized (messages) {
            Message message = messages.firstElement();
            messages.removeElementAt(0);
            return message;
        }
    }
    
    private class QueueThread extends Thread { // -- DONE
        private LinkLayer linkLayer;
        
        private QueueThread(LinkLayer linkLayer) {
            this.linkLayer = linkLayer;
        }
        
        @Override 
        public void run() {
            while (true) {
                try {
                    Message currentMessage = linkLayer.getMessage();
                    boolean sent = false;
                    while (!sent) {
                        if (linkLayer.canSendMessage()) {
                            sleep((int) (Math.random()*1000));
                            if (linkLayer.canSendMessage()) {
                                linkLayer.sendToLowerLayer(currentMessage);
                                sent = true;
//                                sleep(100); // CHECK THIS
                            }
                        } else {
                            sleep((int) (Math.random()*100));
                        }
                    }
                } catch (InterruptedException e) {
                    System.out.println("LL - INTERRUPTED");
                }
            }
        }
    }
}
