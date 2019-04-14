package layer_network;

import java.util.ArrayList;
import java.util.List;

public class Path {
    private int srcAddress;
    private int desAddress;
    private List<Integer> path;
    
    public Path(int srcAddress, int desAddress) {
        this.srcAddress = srcAddress;
        this.desAddress = desAddress;
        this.path = new ArrayList<Integer>();
        path.add(srcAddress);
    }    
    
    public void addNode(int address) {
        path.add(address);
    }
    
    public int findIndex(int address) {
        return path.indexOf(address);
    }
    
    public List<Integer> getPath() {
        return this.path;
    }
    
    public int getDesAddress() {
        return this.desAddress;
    }
    
    public boolean containNode(int address) {
        return address == srcAddress || path.contains(address) || address == desAddress;
    }
    
    public void setDesAddress(int desAddress) {
        this.desAddress = desAddress;
    }
    
    @Override
    public String toString() {
        String path = "SOURCE: " + srcAddress + " | DESTINATION: " + desAddress + " | PATH";
        for (Integer node: this.path) {
            path += " - " + node;
        }
        return path;
    }
}
