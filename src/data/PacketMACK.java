package data;

public class PacketMACK implements Packet {
    private int originalSrcAddress;
    private int originalUID;
    
    public PacketMACK(int originalSrcAddress, int originalUID) {
        this.originalSrcAddress = originalSrcAddress;
        this.originalUID = originalUID;
    }
    
    public int getOriginalSrcAddress() {
        return originalSrcAddress;
    }

    public int getOriginalUID() {
        return originalUID;
    }
}
