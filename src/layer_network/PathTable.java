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
    
    public void updateBreakNode(int breakNode, int desAddress) {
        List<Integer> missingNodes = new ArrayList<Integer>();
        for (Path path: pathList) {
            if (path.getDesAddress() == desAddress) {
                for (Integer missingNode: path.getNextNodes(breakNode)) {
                    missingNodes.add(missingNode);
                }
            }
        }
        
        for (int missingNode: missingNodes) {
            removeMissingNode(missingNode);
        }
    }
    
    private void removeMissingNode(int missingNode) {
        for (Path path: pathList) {
            if (path.getDesAddress() == missingNode) {
                pathList.remove(path);
                break;
            }
        }
    }

    public int getNextNode(int srcAddress, int desAddress, int currentNode) {
        int result = -1;
        for (Path path: pathList) {
            if (path.getDesAddress() == desAddress && path.getSrcAddress() == srcAddress) {
                result = path.getNextNode(currentNode);
                break;
            }
        }
        return result;
    }
    
    public int getPreviousNode(int srcAddress, int desAddress, int currentNode) {
        for (Path path: pathList) {
            if (path.getSrcAddress() == myAddress && path.getDesAddress() == srcAddress) {
                return path.getPath().get(1);
            }
        }
        
        return -1;
    }
    
    public void updateTable(Path path) {
        int myIndexInPath = path.findIndex(myAddress);
        if (myIndexInPath == - 1) {
            return;
        }
        for (int i = 0;i < myIndexInPath;i++) {
            if (canGoTo(path.getPath().get(i))) {
                continue;
            }
            Path newPath = new Path(myAddress,path.getPath().get(i));
            for (int j = myIndexInPath - 1;j >= i;j--) {
                newPath.addNode(path.getPath().get(j));
            }
            pathList.add(newPath);
        }
        for (int i = myIndexInPath+1;i < path.getPath().size();i++) {
            if (canGoTo(path.getPath().get(i))) {
                continue;
            }
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
