import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.*;

public class NioServer {
    private final Charset charset = Charset.forName("UTF-8"); // 字符集
    private final ByteBuffer buffer = ByteBuffer.allocate(1024); // 缓存
    private final Map<String, SocketChannel> onlineUsers = new HashMap<String, SocketChannel>();// 将用户对应的channel对应起来
    private final Map<String, String> userInfo = new HashMap<>();
    private final List<Message> chatList = new ArrayList<Message>();
    private Selector selector;
    private ServerSocketChannel server;

    public void startServer() throws IOException {
        // NIO server初始化固定流程：5步
        selector = Selector.open(); // 1.selector open
        server = ServerSocketChannel.open(); // 2.ServerSocketChannel open
        server.bind(new InetSocketAddress(8888)); // 3.serverChannel绑定端口
        server.configureBlocking(false); // 4.设置NIO为非阻塞模式
        server.register(selector, SelectionKey.OP_ACCEPT);// 5.将channel注册在选择器上

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
                    clientChannel = server.accept(); // 获取客户端首次连接
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

                        if (msgBuilder.charAt(0) == '/') {
                            String[] command = msgBuilder.toString().split(" ");
                            if (command.length == 1) {
                                switch (command[0]) {
                                    case "help":
                                        clientChannel.write(charset.encode(
                                            "Available Instructions:\n/help                    --Query instruction set\n/login \n/signup\n/chat <userName>"
                                                + "         --Create private chat\n/group <groupName>       --Create new group chat\n/add <user1> <user2>...  "
                                                + "--(only)Manager invite users\n/history                 --Query chat records\n/cancel <groupName>      "
                                                + "--(only)Manager dissolve group chat \n/exit                    --Close current chat"));
                                    case "signup":
                                        clientChannel.write(charset.encode("请输入用户名和密码："));
                                    case "login":
                                        clientChannel.write(charset.encode("请输入用户名和密码："));
                                }
                            }
                        }
                        String message = msgBuilder.toString();

                        String[] msgArray = message.split("[|]");
                        if (msgArray.length == 1) {
                            String[] login = message.split(" ");
                            String user = login[0];
                            String pwd = login[1];
                            if (userInfo.containsKey(user) && userInfo.get(user).equals(pwd)) {
                                // |字符来作为消息之间的分割符,防止黏包
                                clientChannel.write(charset.encode("成功登陆 " + user + "|"));
                                String welCome = "\t欢迎'" + user + "'上线，当前在线人数" + getOnLineNum() + "人。用户列表："
                                    + onlineUsers.keySet().toString();
                                broadCast(welCome + "|"); // 给所用用户推送上线信息，包括自己
                            } else if (userInfo.containsKey(user) && !userInfo.get(user).equals(pwd)) {
                                clientChannel.write(charset.encode("用户名或密码错误，请重新输入用户名和密码："));
                            } else {
                                onlineUsers.put(user, clientChannel);
                                key.attach(user); // 给通道定义一个表示符
                                userInfo.put(user, pwd);

                                clientChannel.write(charset.encode("成功登陆 " + user + "|"));
                                String welCome = "\t欢迎'" + user + "'上线，当前在线人数" + getOnLineNum() + "人。用户列表："
                                    + onlineUsers.keySet().toString();
                                broadCast(welCome + "|");
                            }
                        } else if (msgArray.length == 3) {
                            String user_to = msgArray[0];
                            String msg_body = msgArray[1];
                            String user_from = msgArray[2];
                            // 此处用try-with-resource会自动关闭channel，导致程序错误!!
                            SocketChannel channel_to = onlineUsers.get(user_to);
                            Date date = new Date();
                            Message chatMsg = new Message(date, user_from, user_to, msg_body);
                            if (user_to.equals("ALL")) {
                                this.broadCast(new Date() + "---来自'" + user_from + "'的消息： " + msg_body);
                                chatList.add(chatMsg);
                            } else if (user_to.equals("Server")) {
                                clientChannel.write(charset.encode("   日期              目标      内容"));
                                for (Message msgQuery : chatList) {
                                    if (chatMsg.getUserName().equals(user_from)
                                        || chatMsg.getUserTarget().equals(user_to)) {
                                        clientChannel.write(charset.encode(msgQuery.getMessage()));
                                    }
                                }
                            } else if (channel_to == null) {
                                clientChannel.write(
                                    charset.encode("用户'" + user_to + "'不存在，当前用户列表：" + onlineUsers.keySet().toString()));
                            } else {
                                chatList.add(chatMsg);
                                System.out.println(chatMsg.getMessage());
                                channel_to.write(charset.encode(date + "---来自'" + user_from + "'的消息： " + msg_body));
                            }
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
