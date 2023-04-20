import java.util.Date;

public class GroupUsers {
    private String userName;
    private boolean isOnline;
    private Date lastOnline;


    public GroupUsers(String userName , boolean isOnline , Date lastOnline) {
        this.userName = userName;
        this.isOnline = isOnline;
        this.lastOnline = lastOnline;
    }

    // GET 方法
    public String getUserName() {
        return userName;
    }
    public boolean isOnline() {
        return isOnline;
    }
    public Date getLastOnline() {
        return lastOnline;
    }

    // SET 方法
    public void setUserName(String userName) {
        this.userName = userName;
    }
    public void setIsOnline(boolean isOnline) {
        this.isOnline = isOnline;
    }
    public void setLastOnline(Date lastOnline) {
        this.lastOnline = lastOnline;
    }
}
