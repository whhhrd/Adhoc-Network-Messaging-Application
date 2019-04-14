package data;

public class PacketUACK implements Packet {
    private int srcAddress;
    private int originalSrcAddress;
    private int UID;
    
    public PacketUACK(int srcAddress, int originalSrcAddress, int UID) {
        this.srcAddress = srcAddress;
        this.originalSrcAddress = originalSrcAddress;
        this.UID = UID;
    }

    public int getSrcAddress() {
        return srcAddress;
    }

    public int getOriginalSrcAddress() {
        return originalSrcAddress;
    }

    public int getUID() {
        return UID;
    }
}
