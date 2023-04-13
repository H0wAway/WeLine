package chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class ChatClient {
    Selector selector;
    SocketChannel socketChannel;
    boolean running = true;

    MessageType messageType = MessageType.REG_CLIENT_ACK;
    String prompt = "User Name:";

    public void runClient() throws IOException {
        try {
            selector = Selector.open();
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress("127.0.0.1", ChatServer.SERVER_PORT));
            System.out.println("Client connecting to server.");

            socketChannel.register(selector, SelectionKey.OP_CONNECT);

            while (running) {
                int eventCount = selector.select(100);
                if (eventCount == 0)
                    continue;
                Set<SelectionKey> set = selector.selectedKeys();
                Iterator<SelectionKey> keyIterable = set.iterator();
                while (keyIterable.hasNext()) {
                    SelectionKey key = keyIterable.next();
                    keyIterable.remove();
                    // 处理事件
                    dealEvent(key);
                }
            }
        } finally {
            if (selector != null && selector.isOpen())
                selector.close();

            if (socketChannel != null && socketChannel.isConnected())
                socketChannel.close();
        }
    }

    private void dealEvent(SelectionKey key) throws IOException {
        //
        if (key.isConnectable()) {
            SocketChannel channel = (SocketChannel)key.channel();
            if (channel.isConnectionPending()) {
                channel.finishConnect();
            }
            channel.register(selector, SelectionKey.OP_READ);

            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    printMsgAndPrompt("Start to interconnect with server.");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                    while (running) {
                        System.out.print(prompt);
                        String msg = reader.readLine();
                        if (msg == null || msg.length() == 0)
                            continue;
                        // 客户端向服务端发送注册用户名
                        if (messageType == MessageType.REG_CLIENT_ACK) {
                            ByteBuffer bufferMsg = Message.encodeRegClientAck(msg);
                            channel.write(bufferMsg);
                        } else {
                            String[] msgArr = msg.split("#", 2);
                            ByteBuffer bufferMsg = Message.encodeSendMsg(msg);
                            if (msgArr.length == 2) {
                                bufferMsg = Message.encodeSendMsg(msgArr[0], msgArr[1]);
                            }

                            channel.write(bufferMsg);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {

                }
            }).start();
        } else if (key.isReadable()) {
            try {
                SocketChannel channel = (SocketChannel)key.channel();
                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                channel.read(byteBuffer);
                byteBuffer.flip();
                String msg = Message.CHARSET.decode(byteBuffer).toString();
                dealMsg(msg);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Server exit.");
                System.exit(0);
            }
        }
    }

    private void dealMsg(String msg) {
        Message message = Message.decode(msg);
        if (message == null)
            return;

        switch (message.getAction()) {
            // 服务端向客户端发送注册用户名提示
            case REG_SERVER_SYN:
                printMsgAndPrompt(message.getMessage());
                break;
            // 服务端接收聊天信息，进行单播或关闭
            case CHAT_MSG_RECEIVE:
                printMsgAndPrompt(String.format("MSG from %s: %s", message.getOption(), message.getMessage()));
                break;
            // 客户端向服务端发送聊天信息，指定toUser为单播，否则广播
            case REG_SERVER_ACK:
                messageType = MessageType.CHAT_MSG_SEND;
                prompt = "Input your message:";
                printMsgAndPrompt(message.getMessage());
                break;
            case BROADCAST_USER_LIST:
                printMsgAndPrompt(String.format("User list: %s", message.getMessage()));
                break;
            default:
        }
    }

    private void printMsgAndPrompt(String msg) {
        System.out.println(msg);
        System.out.print(prompt);
    }

    public static void main(String[] args) throws IOException {
        new ChatClient().runClient();
    }
}