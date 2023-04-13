import java.util.Date;

public class Message {

    private final String userName;
    private final String userTarget;
    private final Date date;
    private final String data;

    public Message(Date date, String fullName, String fullName2, String data) {
        super();
        this.userName = fullName;
        this.userTarget = fullName2;
        this.date = date;
        this.data = data;
    }

    public String getMessage() {
        return date.toString() + "--" + userName + "--" + userTarget + "--" + data + "    ";
    }

    // 群聊 >=3人，一直getUserMsg
    public String getUserMsg() {
        return userName + ": " + data;
    }

    //
    public String getTargetMsg() {
        return userTarget + ": " + data;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserTarget() {
        return userTarget;
    }

}
