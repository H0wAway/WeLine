package chat;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class Message {
    public static final String MSG_SPLIT = "#@@#";
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    private MessageType action;
    private String option;
    private String message;

    public Message(MessageType action, String option, String message) {
        this.action = action;
        this.option = option;
        this.message = message;
    }

    public Message(MessageType action, String message) {
        this.action = action;
        this.message = message;
    }

    public ByteBuffer encode() {
        StringBuilder builder = new StringBuilder(action.getAction());
        if (option != null && option.length() > 0) {
            builder.append(MSG_SPLIT);
            builder.append(option);
        }
        builder.append(MSG_SPLIT);
        builder.append(message);

        return CHARSET.encode(builder.toString());
    }

    public static Message decode(String message) {
        if (message == null || message.length() == 0)
            return null;
        String[] msgArr = message.split(MSG_SPLIT);
        MessageType messageType = msgArr.length > 1 ? MessageType.getActionType(msgArr[0]) : null;

        switch (msgArr.length) {
            case 2:
                return new Message(messageType, msgArr[1]);
            case 3:
                return new Message(messageType, msgArr[1], msgArr[2]);
            default:
                return null;
        }
    }

    public static ByteBuffer encodeRegSyn() {
        return encodeRegSyn(false);
    }

    public static ByteBuffer encodeRegSyn(boolean duplicate) {
        MessageType action = MessageType.REG_SERVER_SYN;
        String message = "Please input your name to register.";
        if (duplicate) {
            message = "This name is used, Please input another name.";
        }
        return new Message(action, message).encode();
    }

    public static ByteBuffer encodeSendMsg(String msg) {
        return encodeSendMsg(null, msg);
    }

    public static ByteBuffer encodeSendMsg(String toUser, String msg) {
        MessageType action = MessageType.CHAT_MSG_SEND;
        String option = toUser;
        String message = msg;
        return new Message(action, option, message).encode();
    }

    public static ByteBuffer encodeReceiveMsg(String fromUser, String msg) {
        MessageType action = MessageType.CHAT_MSG_RECEIVE;
        String option = fromUser;
        String message = msg;
        return new Message(action, option, message).encode();
    }

    public static ByteBuffer encodeRegClientAck(String username) {
        MessageType action = MessageType.REG_CLIENT_ACK;
        String message = username;
        return new Message(action, message).encode();
    }

    public static ByteBuffer encodeRegServerAck(String username) {
        MessageType action = MessageType.REG_SERVER_ACK;
        String message = username + ", Welcome to join the chat.";
        return new Message(action, message).encode();
    }

    public static ByteBuffer encodePublishUserList(List<String> userList) {
        MessageType action = MessageType.BROADCAST_USER_LIST;
        String message = Arrays.toString(userList.toArray());
        return new Message(action, message).encode();
    }

    public MessageType getAction() {
        return action;
    }

    public void setAction(MessageType action) {
        this.action = action;
    }

    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

enum MessageType {
    REG_SERVER_SYN("reg_server_syn"), CHAT_MSG_SEND("chat_send"), CHAT_MSG_RECEIVE("chat_receive"), UNKNOWN("unknown"),
    REG_SERVER_ACK("reg_server_ack"), REG_CLIENT_ACK("reg_client_ack"), BROADCAST_USER_LIST("broadcast_user_list");

    private String action;

    MessageType(String action) {
        this.action = action;
    }

    public String getAction() {
        return action;
    }

    public static MessageType getActionType(String action) {
        for (MessageType messageType : MessageType.values()) {
            if (messageType.getAction().equals(action)) {
                return messageType;
            }
        }
        return UNKNOWN;
    }
}