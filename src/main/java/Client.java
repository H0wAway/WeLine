import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Scanner;

public class Client {

    // private final int port = 8899;
    // 构造charset字符集，并提供encode()方法
    // public final ByteBuffer encode(String str): 将字符串编码为字节
    // Server接收字节后要解码decode
    private final Charset charset = Charset.forName("UTF-8");
    private ByteBuffer buffer = ByteBuffer.allocate(1024);
    private SocketChannel clientChannel;
    private Selector selector;
    private String myName = "";
    private boolean flag = true; // 服务端断开，客户端的读事件不会一直发生（与服务端不一样）

    Scanner scanner = new Scanner(System.in);

    public void startClient() throws IOException {

        // 客户端初始化固定流程
        // 1.打开Selector
        selector = Selector.open();
        // 2.连接服务端，这里默认本机的IP
        clientChannel = SocketChannel.open(new InetSocketAddress(8899));
        // 3.配置此channel非阻塞
        clientChannel.configureBlocking(false);
        // 4.将channel的读事件注册到选择器
        clientChannel.register(selector, SelectionKey.OP_READ);

        /*
         * 因为等待用户输入会导致主线程阻塞
         * 所以用主线程处理输入，新开一个线程异步读取数据
         */
        new Thread(new ClientReadThread()).start();
        String input = "";

        while (flag) {
            input = scanner.nextLine();
            // 输入标准格式为 目标|内容
            if ("".equals(input)) {
                System.out.println("输入为空白！");
                continue;
                // 名字没有初始化，且长度为1 --> 当前在设置姓名
            } else if ("".equals(myName) && input.split("[|]").length == 1) {
                // 名字已经初始化过了，且长度为2 --> 正确的发送格式
            } else if (!"".equals(myName) && input.split("[|]").length == 2) {
                input = input + "|" + myName;
            } else {
                System.out.println("输入不合法，请重新输入：");

                continue;
            }
            try {
                clientChannel.write(charset.encode(input));
            } catch (Exception e) {
                System.out.println(e.getMessage() + "客户端主线程退出连接！！");
            }
        }
    }

    private class ClientReadThread implements Runnable {

        @Override
        public void run() {
            Iterator<SelectionKey> ikeys;
            SelectionKey key;
            SocketChannel client;
            try {
                while (flag) {
                    selector.select(); // 调用此方法一直阻塞，直到有channel可用
                    ikeys = selector.selectedKeys().iterator();
                    while (ikeys.hasNext()) {
                        key = ikeys.next();
                        if (key.isReadable()) { // 处理读事件
                            client = (SocketChannel)key.channel();
                            // 这里的输出是true，从selector的key中获取的客户端channel，是同一个

                            buffer.clear();
                            StringBuilder msg = new StringBuilder();
                            try {
                                while (client.read(buffer) > 0) {
                                    buffer.flip(); // 将写模式转换为读模式
                                    msg.append(charset.decode(buffer));
                                }
                            } catch (IOException en) {
                                en.printStackTrace();
                                System.out.println(en.getMessage() + ",用户'" + key.attachment().toString() + "'线程退出！！");
                                stopMainThread();
                            }
                            String[] strArray = msg.toString().split("[|]");
                            for (String message : strArray) {
                                if (message == "")
                                    continue;
                                if (message.contains("成功登陆")) {
                                    String[] nameValid = message.split(" ");
                                    myName = nameValid[1];
                                    key.attach(myName);
                                }
                                System.out.println(message);
                            }
                        }
                        ikeys.remove();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void stopMainThread() {
        flag = false;
    }

    public static void main(String[] args) {
        try {
            new Client().startClient();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
