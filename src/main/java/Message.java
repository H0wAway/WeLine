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
        return "日期：" + date.toString() + "|目标：" + userTarget + "|内容：" + data;
    }

    public String getUserName() {
        return userName;
    }

}
