import org.apache.commons.lang.StringUtils;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.*;

public class NioServer {
    private final Charset charset = Charset.forName("UTF-8");
    private final ByteBuffer buffer = ByteBuffer.allocate(1024);
    private final Map<String, SocketChannel> onlineUsers = new HashMap<String, SocketChannel>();
    private final Map<String, String> userInfo = new HashMap<>();
    private final Map<String, Map<String,Date>> groupMap = new HashMap<>();
    private final List<Message> chatList = new ArrayList<Message>();
    private Selector selector;

    public void startServer() throws IOException {
        // NIO server初始化固定流程：5步
        selector = Selector.open();
        ServerSocketChannel server = ServerSocketChannel.open ( );
        server.bind(new InetSocketAddress(8888));
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);

        // NIO server处理数据固定流程:实例化
        SocketChannel clientChannel;
        SelectionKey key;
        Iterator<SelectionKey> keyIterator;

        while (true) {
            // 1.用select()方法阻塞，一直到有可用连接加入
            selector.select();
            // 2.到了这步，说明有可用连接到底，取出所有可用连接
            keyIterator = selector.selectedKeys().iterator();
            while (keyIterator.hasNext()) {
                // 3.遍历
                key = keyIterator.next();
                // 4.对每个连接感兴趣的事做不同的处理
                if (key.isAcceptable()) {
                    // 对于客户端连接，注册到服务端
                    clientChannel = server.accept();
                    clientChannel.configureBlocking(false);
                    // Server给Client发消息,设置SelectionKey属性为可读
                    clientChannel.register(selector, SelectionKey.OP_READ);
                    System.out.println("客户端：" + clientChannel.getRemoteAddress() + "，建立连接");
                    clientChannel.write(charset.encode("请登录您的账号。(/signup注册 /login登录)"));
                }
                if (key.isReadable()) {
                    clientChannel = (SocketChannel)key.channel();
                    StringBuilder msgBuilder = new StringBuilder();
                    buffer.clear();

                    try {
                        while (clientChannel.read(buffer) > 0) {
                            buffer.flip();
                            msgBuilder.append(charset.decode(buffer));
                        }
                    } catch (IOException e) {
                        clientChannel.close();
                        key.cancel();
                        onlineUsers.values().remove(clientChannel);
                        System.out.println(
                                "用户'" + key.attachment().toString() + "'退出连接，当前用户列表：" + onlineUsers.keySet().toString());
                        continue;
                    }
                    if (msgBuilder.length() > 0) {
                        String[] inst = msgBuilder.toString().split( "[|]" );
                        switch (inst[0]) {
                            // 注册
                            case "SIGNUP":
                                if (userInfo.containsKey ( inst[1] )) {
                                    clientChannel.write ( charset.encode ( "/UserName|The username already exists." ) );
                                }else{
                                    userInfo.put ( inst[1],inst[1] );
                                    clientChannel.write ( charset.encode ( "/Pwd|"+inst[1]+"|Next step." ) );
                                }
                                break;
                            case "PWD":
                                userInfo.put ( inst[1],inst[2] );
                                clientChannel.write ( charset.encode ( "/Success|"+inst[1]+"|SIGNUP SUCCESS." ) );
                                onlineUsers.put(inst[1], clientChannel);
                                break;
                                // 登陆
                            case "LOGIN":
                                String s = "";
                                if (!userInfo.containsKey ( inst[1] )) {
                                    s= "/loginFail|Invalid username." ;
                                }else if (!inst[2].equals ( userInfo.get ( inst[1] ) )){
                                    s= "/loginFail|Wrong password." ;
                                }else{
                                    s= "/Success|"+inst[1]+"|LOGIN SUCCESS." ;
                                    key.attach(inst[1]);
                                    onlineUsers.put(inst[1], clientChannel);
                                    String welCome = "\t欢迎'" + inst[1] + "'上线，当前在线人数" + getOnLineNum() + "人。用户列表："
                                            + onlineUsers.keySet().toString();
                                    broadCast(welCome + "|");
                                }
                                clientChannel.write ( charset.encode ( s ) );
                                break;
                            case "LOGOFF":
                                onlineUsers.remove ( inst[1] );
                                break;

                            case "EXIT":
                                HashMap<String,Date> chat1 =new HashMap<> (  );
                                chat1.put ( inst[1], new Date() );
                                groupMap.replace ( inst[2], chat1 );
                                clientChannel.write ( charset.encode ( "/exit|" ) );
                                break;
                            case "CHAT":

                                HashMap<String,Date> chats =new HashMap<> (  );
                                chats.put ( key.attachment ().toString (), new Date() );
                                chats.put ( inst[2], new Date() );
                                groupMap.put ( inst[1],chats );
                                clientChannel.write ( charset.encode ( "Please input your message:" ) );
                                break;

                            case "GROUP":
                                groupMap.put(inst[1],new HashMap<> ());
                                break;
                            case "ADD":
                                String[] players = inst[2].split( " " );
                                HashMap<String,Date> groups =new HashMap<> (  );
                                for (String player : players) {
                                    groups.put ( player , new Date ( ) );
                                }
                                groupMap.put ( inst[1],groups );
                                break;
                            case "MESSAGE":
                                String groupName = inst[1];
                                String msg = inst[2];
                                String user_from = key.attachment ().toString ();
                                SocketChannel channel_to;
                                Date date = new Date();
                                Message chatMsg = new Message(date, user_from, groupName, msg);
                                chatList.add(chatMsg);
                                // 默认类型为Map
                                Map<String,Date> gps = groupMap.get ( groupName );
                                for (String value:onlineUsers.keySet()) {
                                    if(gps.containsKey ( value )){
                                        channel_to = onlineUsers.get ( value );
                                        channel_to.write ( charset.encode ( msg ) );
                                    }
                                }
                                break;
                            case "HISTORY":
                                String Target = inst[1];
                                for (Message msgQuery : chatList) {
                                    if (msgQuery.getUserTarget ().equals(Target)) {
                                        clientChannel.write(charset.encode(msgQuery.getMessage ()));
                                    }
                                }
                                break;
                        }
                    }
                }
                // 5.处理完一次事件后，要显式的移除
                keyIterator.remove();
            }
        }
    }

    // map中的有效数量已被很好的控制，可以从map中获取，也可以用下面的方法取
    private int getOnLineNum() {
        int count = 0;
        Channel channel;
        for (SelectionKey k : selector.keys()) {
            channel = k.channel();
            // 排除ServerSocketChannel
            if (channel instanceof SocketChannel) {
                count++;
            }
        }
        return count;
    }

    // 广播上线消息
    private void broadCast(String msg) throws IOException {
        Channel channel;
        for (SelectionKey k : selector.keys()) {
            channel = k.channel();
            if (channel instanceof SocketChannel) {
                SocketChannel clientChannel = (SocketChannel)channel;
                clientChannel.write(charset.encode(msg));
            }
        }
    }

    public static void main(String[] args) {
        try {
            new NioServer().startServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
