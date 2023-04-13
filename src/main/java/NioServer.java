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
    private final List<Map<String, SocketChannel>> groupList = new ArrayList<>();
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
        SocketChannel client;
        SelectionKey key;
        Iterator<SelectionKey> iKeys;

        while (true) {
            // 1.用select()方法阻塞，一直到有可用连接加入
            selector.select();
            // 2.到了这步，说明有可用连接到底，取出所有可用连接
            iKeys = selector.selectedKeys().iterator();
            while (iKeys.hasNext()) {
                // 3.遍历
                key = iKeys.next();
                // 4.对每个连接感兴趣的事做不同的处理
                if (key.isAcceptable()) {
                    // 对于客户端连接，注册到服务端
                    client = server.accept(); // 获取客户端首次连接
                    client.configureBlocking(false);
                    // 不用注册写，只有当写入量大，或写需要争用时，才考虑注册写事件
                    client.register(selector, SelectionKey.OP_READ);
                    System.out.println("客户端：" + client.getRemoteAddress() + "，建立连接");
                    client.write(charset.encode("请输入用户名和密码(用空格分开)："));
                }
                if (key.isReadable()) {
                    // 通过key取得客户端channel
                    client = (SocketChannel)key.channel();
                    StringBuilder msg = new StringBuilder();
                    // 多次使用的缓存，用前要先清空
                    buffer.clear();

                    try {
                        while (client.read(buffer) > 0) {
                            // 将写模式转换为读模式，否则读取错误数据
                            buffer.flip();
                            msg.append(charset.decode(buffer));
                        }
                    } catch (IOException e) {
                        // 如果client.read(buffer)抛出异常，说明此客户端主动断开连接。
                        // 关闭channel
                        client.close();
                        // 将channel对应的key置为不可用
                        key.cancel();
                        // 将问题连接从map中删除
                        onlineUsers.values().remove(client);
                        System.out.println(
                            "用户'" + key.attachment().toString() + "'退出连接，当前用户列表：" + onlineUsers.keySet().toString());
                        continue; // 跳出循环
                    }
                    if (msg.length() > 0) {

                        String message = msg.toString();

                        String[] msgArray = message.split("[|]");
                        if (msgArray.length == 1) {
                            String[] login = message.split(" ");
                            String user = login[0];
                            String pwd = login[1];
                            if (userInfo.containsKey(user) && userInfo.get(user).equals(pwd)) {
                                // |字符来作为消息之间的分割符,防止黏包
                                client.write(charset.encode("成功登陆 " + user + "|"));
                                String welCome = "\t欢迎'" + user + "'上线，当前在线人数" + getOnLineNum() + "人。用户列表："
                                    + onlineUsers.keySet().toString();
                                broadCast(welCome + "|"); // 给所用用户推送上线信息，包括自己
                            } else if (userInfo.containsKey(user) && !userInfo.get(user).equals(pwd)) {
                                client.write(charset.encode("用户名或密码错误，请重新输入用户名和密码："));
                            } else {
                                onlineUsers.put(user, client);
                                key.attach(user); // 给通道定义一个表示符
                                userInfo.put(user, pwd);

                                client.write(charset.encode("成功登陆 " + user + "|"));
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
                                client.write(charset.encode("   日期              目标      内容"));
                                for (Message msgQuery : chatList) {
                                    if (chatMsg.getUserName().equals(user_from)) {
                                        client.write(charset.encode(msgQuery.getMessage()));
                                    } else if (chatMsg.getUserTarget().equals(user_to)) {
                                        client.write(charset.encode(chatMsg.getMessage()));
                                    }
                                }
                            } else if (channel_to == null) {
                                client.write(
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
                iKeys.remove();
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
            // instanceof 的作用是测试它左边的 对象 是否是它右边的 类的实例
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
                SocketChannel client = (SocketChannel)channel;
                client.write(charset.encode(msg));
            }
        }
    }

    public void helpList(SocketChannel channel) throws IOException {

        channel.write(charset.encode("/help \n"));
    }

    public void group() {

    }

    public static void main(String[] args) {
        try {
            new NioServer().startServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
