package data;

public class PacketData implements Packet {
    private int srcAddress;
    private int desAddress;
    private int UID;
    private String message;
    private boolean finPacket;
    
    public PacketData(int srcAddress, int desAddress, int UID, String message, boolean isFinPacket) { 
        this.srcAddress = srcAddress;
        this.desAddress = desAddress;
        this.UID = UID;
        this.message = message;
        this.finPacket = isFinPacket;
    }

    public int getSrcAddress() {
        return srcAddress;
    }

    public int getDesAddress() {
        return desAddress;
    }

    public int getUID() {
        return UID;
    }
    
    public String getMessage() {
        return this.message;
    }
    
    public boolean isFinPacket() {
        return this.finPacket;
    }
}
