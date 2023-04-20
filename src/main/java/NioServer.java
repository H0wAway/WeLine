
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.*;

import static java.lang.System.*;

public class NioServer {
    private final Charset charset = Charset.forName("UTF-8");
    private final ByteBuffer buffer = ByteBuffer.allocate(1024);
    private final Map<String, SocketChannel> onlineUsers = new HashMap<String, SocketChannel>();
    private final Map<String, String> userInfo = new HashMap<>();
    private final Map<String, List<GroupUsersInfo>> groupMap = new HashMap<>();
    private final List<Message> chatList = new ArrayList<>();
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
                    out.println("客户端：" + clientChannel.getRemoteAddress() + "，建立连接");
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
                        out.println(
                                "用户'" + key.attachment().toString() + "'退出连接，当前用户列表：" + onlineUsers.keySet().toString());
                        continue;
                    }
                    if (msgBuilder.length() > 0) {
                        String[] inst = msgBuilder.toString().split( "[|]" );
                        String s = "";
                        switch (inst[0]) {

                            // 注册 SIGNUP|userName|pwd
                            case "SIGNUP":
                                if (userInfo.containsKey ( inst[1] )) {
                                    s = "/signupFail|The username already exists.";
                                }else {
                                    userInfo.put ( inst[1] , inst[2] );
                                    s = "/Success|" + inst[1] + "|SIGNUP SUCCESS.";
                                    onlineUsers.put ( inst[1] , clientChannel );
                                }
                                clientChannel.write ( charset.encode (s));
                                break;

                            // 登陆 LOGIN|userName|pwd
                            case "LOGIN":
                                if (!userInfo.containsKey ( inst[1] )) {
                                    s= "/loginFail|Invalid username." ;
                                }else if (!inst[2].equals ( userInfo.get ( inst[1] ) )){
                                    s= "/loginFail|Wrong password." ;
                                }else{
                                    s= "/Success|"+inst[1]+"|LOGIN SUCCESS." ;
                                    key.attach(inst[1]);
                                    onlineUsers.put(inst[1], clientChannel);
                                    String welCome = "\t欢迎'" + inst[1] + "'上线，当前在线人数" + getOnLineNum() + "人。用户列表："
                                            + onlineUsers.keySet();
                                    broadCast(welCome + "|");
                                }
                                clientChannel.write ( charset.encode ( s ) );
                                break;

                            // LOGOFF|myName|
                            case "LOGOFF":
                                onlineUsers.remove ( inst[1] );
                                clientChannel.write ( charset.encode ( "/LogOff" ) );
                                break;

                            // EXIT|chaTarget|myName|
                            case "EXIT":
                                for(GroupUsersInfo groupUsersInfo:groupMap.get ( inst[1] )){
                                    if(groupUsersInfo.getUserName (  ).equals ( inst[2] )){
                                        groupUsersInfo.setUserInfo ( false, new Date (  ) );
                                    }
                                }
                                clientChannel.write ( charset.encode ( "成功退出当前聊天。" ) );
                                break;

                            // CHAT|chaTarget|msgTarget|myName|
                            case "CHAT":
                                String chaTarget = inst[1];
                                // 如果群聊存在，则放聊天记录。记得把自己状态改为Online.
                                if(groupMap.containsKey ( chaTarget )){
                                    for (GroupUsersInfo gUsers : groupMap.get ( chaTarget )) {
                                        if (gUsers.getUserName ().equals ( inst[3] )) {
                                            gUsers.setUserInfo ( true,new Date () );
                                        }
                                    }
                                    for (Message msgQuery : chatList) {
                                        if (msgQuery.getUserTarget ().equals(chaTarget)) {
                                            clientChannel.write(charset.encode(msgQuery.getMessage ()));
                                        }
                                    }
                                // 判断用户存不存在（yiHouXie）
                                } else{
                                    List<GroupUsersInfo> list = new ArrayList<> ();
                                    list.add(new GroupUsersInfo (  inst[3], true, new Date() ));
                                    list.add(new GroupUsersInfo (  inst[2], false, new Date() ));
                                    groupMap.put ( chaTarget, list );
                                    clientChannel.write ( charset.encode ( "Please input your message: " ) );
                                }
                                break;

                            case "GROUP":
                                if(groupMap.containsKey ( inst[1] )){
                                    for (Message msgQuery : chatList) {
                                        if (msgQuery.getUserTarget ().equals(inst[1])) {
                                            clientChannel.write(charset.encode(msgQuery.getMessage ()));
                                        }
                                    }
                                }else {
                                    groupMap.put ( inst[1],new ArrayList<> () );
                                    groupMap.get ( inst[1] ).add ( new GroupUsersInfo ( inst[2], true,new Date (  )) );
                                    clientChannel.write ( charset.encode ( "成功创建并加入群聊, 请用‘/add userName’添加成员。 " ) );
                                }
                                break;

                            // 只有群主有权限加人(yiHouXie）
                            case "ADD":
                                String[] players = inst[2].split( " " );
                                for (String player : players) {
                                    groupMap.get ( inst[1] ).add ( new GroupUsersInfo ( player, false,new Date (  )) );
                                }
                                break;
                            case "MESSAGE":
                                String groupName = inst[1];
                                String msg = inst[2];
                                SocketChannel channelTarget;
                                chatList.add(new Message(new Date(), key.attachment ().toString (), groupName, msg));
                                // groupMap.get ( groupName )为该组用户（含状况）列表List.
                                for (GroupUsersInfo gUsers : groupMap.get ( groupName )) {
                                    if (gUsers.isOnline ()) {
                                        channelTarget = onlineUsers.get ( gUsers.getUserName () );
                                        channelTarget.write ( charset.encode ( key.attachment ().toString ()+": "+msg ) );
                                    }
                                }
                                break;

                            // HISTORY|chaTarget
                            case "HISTORY":
                                for (Message msgQuery : chatList) {
                                    if (msgQuery.getUserTarget ().equals(inst[1])) {
                                        clientChannel.write(charset.encode(msgQuery.getMessage ()));
                                    }
                                }
                                break;
                            default:
                                throw new IllegalStateException ( "Unexpected value: " + inst[0] );
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
