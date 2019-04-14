package layer_network;

import java.util.ArrayList;
import java.util.List;

public class PathTable {
    private List<Path> pathList;
    private int myAddress;
    
    public PathTable(int myAddress) {
        pathList = new ArrayList<Path>();
        this.myAddress = myAddress;
    }
    
    public boolean canGoTo(int address) {
        for (Path path: pathList) {
            if (path.getDesAddress() == address) {
                return true;
            }
        }
        return false;
    }
    
    public void updateTable(Path path) {
        int myIndexInPath = path.findIndex(myAddress);
        if (myIndexInPath == - 1) {
            return;
        }
        for (int i = 0;i < myIndexInPath;i++) {
            Path newPath = new Path(myAddress,path.getPath().get(i));
            for (int j = myIndexInPath - 1;j >= i;j--) {
                newPath.addNode(path.getPath().get(j));
            }
            pathList.add(newPath);
        }
        for (int i = myIndexInPath+1;i < path.getPath().size();i++) {
            Path newPath = new Path(myAddress,path.getPath().get(i));
            for (int j = myIndexInPath + 1;j <= i;j++) {
                newPath.addNode(path.getPath().get(j));
            }
            pathList.add(newPath);
        }
        
//        System.out.println("->> [UPDATE] " + this);
    }
    
    @Override
    public String toString() {
        return "CURRENT TABLE: " + pathList;
    }
}
