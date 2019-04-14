package data;

public class PacketMACK implements Packet {
    private int originalSrcAddress;
    private int originalUID;
    private int nextNode;
    
    public PacketMACK(int originalSrcAddress, int originalUID, int nextNode) {
        this.nextNode = nextNode;
        this.originalSrcAddress = originalSrcAddress;
        this.originalUID = originalUID;
    }
    
    public void setNextNode(int nextNode) {
        this.nextNode = nextNode;
    }
    
    public int getNextNode() {
        return this.nextNode;
    }
    
    public int getOriginalSrcAddress() {
        return originalSrcAddress;
    }

    public int getOriginalUID() {
        return originalUID;
    }
}
