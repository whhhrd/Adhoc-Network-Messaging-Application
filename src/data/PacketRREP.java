package data;

import layer_network.Path;

public class PacketRREP implements Packet {
    private int srcAddress;
    private int desAddress;
    private int UID;
    private Path path;
    
    public PacketRREP(int srcAddress, int desAddress, int UID,Path path) {
        this.srcAddress = srcAddress;
        this.desAddress = desAddress;
        this.UID = UID;
        this.path = path;
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
    public Path getPath() {
        return path;
    }
}
