package chat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ChatServer {
    public static final int SERVER_PORT = 8080;

    Selector selector;
    ServerSocketChannel serverSocketChannel;
    boolean running = true;

    public void runServer() throws IOException {
        try {
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();

            serverSocketChannel.bind(new InetSocketAddress(SERVER_PORT));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Server started.");

            while (running) {
                int eventCount = selector.select(100);
                if (eventCount == 0)
                    continue;
                Set<SelectionKey> set = selector.selectedKeys();
                Iterator<SelectionKey> keyIterable = set.iterator();
                while (keyIterable.hasNext()) {
                    SelectionKey key = keyIterable.next();
                    keyIterable.remove();
                    // 根据key状态分情况处理
                    dealEvent(key);
                }
            }
        } finally {
            if (selector != null && selector.isOpen())
                selector.close();
            if (serverSocketChannel != null && serverSocketChannel.isOpen())
                serverSocketChannel.close();
        }
    }

    private void dealEvent(SelectionKey key) throws IOException {
        if (key.isAcceptable()) {
            System.out.println("Accept client connection.");
            SocketChannel socketChannel = ((ServerSocketChannel)key.channel()).accept();
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);
            socketChannel.write(Message.encodeRegSyn());
        }
        if (key.isReadable()) {
            SocketChannel socketChannel = null;
            try {
                System.out.println("Receive message from client.");
                socketChannel = (SocketChannel)key.channel();
                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                socketChannel.read(byteBuffer);
                byteBuffer.flip();
                String msg = Message.CHARSET.decode(byteBuffer).toString();
                // 消息处理
                dealMsg(msg, key);
            } catch (IOException e) {
                socketChannel.close();
                String username = (String)key.attachment();
                System.out.println(String.format("User %s disconnected", username));
                broadcastUserList();
            }
        }
    }

    private void dealMsg(String msg, SelectionKey key) throws IOException {
        System.out.println(String.format("Message info is: %s", msg));
        Message message = Message.decode(msg);
        if (message == null)
            return;

        SocketChannel currentChannel = (SocketChannel)key.channel();
        Set<SelectionKey> keySet = getConnectedChannel();
        switch (message.getAction()) {
            // 客户端向服务端发送注册用户名
            case REG_CLIENT_ACK:
                String username = message.getMessage();
                for (SelectionKey keyItem : keySet) {
                    String channelUser = (String)keyItem.attachment();
                    if (channelUser != null && channelUser.equals(username)) {
                        currentChannel.write(Message.encodeRegSyn(true));
                        return;
                    }
                }
                // 客户端注册后，用户名信息保存在服务端对应SelectionKey.attachment属性中。
                key.attach(username);
                currentChannel.write(Message.encodeRegServerAck(username));
                System.out.println(String.format("New user joined: %s,", username));
                broadcastUserList();
                break;
            // 客户端向服务端发送聊天信息，指定toUser为单播，否则广播
            case CHAT_MSG_SEND:
                String toUser = message.getOption();
                String msg2 = message.getMessage();
                String fromUser = (String)key.attachment();

                for (SelectionKey keyItem : keySet) {
                    if (keyItem == key) {
                        continue;
                    }
                    String channelUser = (String)keyItem.attachment();
                    SocketChannel channel = (SocketChannel)keyItem.channel();
                    if (toUser == null || toUser.equals(channelUser)) {
                        channel.write(Message.encodeReceiveMsg(fromUser, msg2));
                    }
                }
                break;
        }
    }

    // 通过Selector.keys可获取所有向Selector注册的客户端，获取客户端连接列表时，需要过滤掉ServerSocketChannel和关闭的Channel
    public void broadcastUserList() throws IOException {
        Set<SelectionKey> keySet = getConnectedChannel();
        List<String> uList = keySet.stream().filter(item -> item.attachment() != null).map(SelectionKey::attachment)
            .map(Object::toString).collect(Collectors.toList());
        for (SelectionKey keyItem : keySet) {
            SocketChannel channel = (SocketChannel)keyItem.channel();
            channel.write(Message.encodePublishUserList(uList));
        }
    }

    private Set<SelectionKey> getConnectedChannel() {
        return selector.keys().stream()
            .filter(item -> item.channel() instanceof SocketChannel && item.channel().isOpen())
            .collect(Collectors.toSet());
    }

    public static void main(String[] args) throws IOException {
        new ChatServer().runServer();
    }
}