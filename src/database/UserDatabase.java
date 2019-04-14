package database;

import java.util.HashMap;
import java.util.Map;

public class UserDatabase {
    public final static int SYSTEM_ID = -1;
    public final static int DUC_ID = 0;
    public final static int IVO_ID = 1;
    public final static int RAOUL_ID = 2;
    public final static int ZIWEI_ID = 3;
    
    public final static String DUC_USERNAME = "DUC";
    public final static String RAOUL_USERNAME = "RAOUL";
    public final static String IVO_USERNAME = "IVO";
    public final static String ZIWEI_USERNAME = "ZIWEI";
   
    public static User getUser(int userId) {
        switch (userId) {
        case DUC_ID:
            return new User(DUC_ID,DUC_USERNAME);
        case RAOUL_ID:
            return new User(RAOUL_ID,RAOUL_USERNAME);
        case IVO_ID:
            return new User(IVO_ID,IVO_USERNAME);
        case ZIWEI_ID:
            return new User(ZIWEI_ID,ZIWEI_USERNAME);
        }
        return null;
    }
    
    public static Map<Integer, String> getUsersMap() {
        Map<Integer, String> users = new HashMap<Integer,String>();
        users.put(DUC_ID, DUC_USERNAME);
        users.put(RAOUL_ID, RAOUL_USERNAME);
        users.put(IVO_ID, IVO_USERNAME);
        users.put(ZIWEI_ID, ZIWEI_USERNAME);
        return users;
    }
    
    public static int findIdByUserName(String username) {
        switch (username) {
        case DUC_USERNAME: return DUC_ID;
        case RAOUL_USERNAME: return RAOUL_ID;
        case IVO_USERNAME: return IVO_ID;
        case ZIWEI_USERNAME: return ZIWEI_ID;
        default: return -1;
        }
    }
}
