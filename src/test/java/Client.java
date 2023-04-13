import java.io.Console;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Scanner;

import org.apache.commons.lang.StringUtils;

public class Client {
    private final Charset charset = Charset.forName("UTF-8");
    private ByteBuffer buffer = ByteBuffer.allocate(1024);
    private SocketChannel clientChannel;
    private Selector selector;
    private String myName = "";
    private boolean flag = true;
    private final boolean isRegister = false;

    Scanner scanner = new Scanner(System.in);

    public void startClient() throws IOException {

        // 客户端初始化固定流程
        selector = Selector.open();
        clientChannel = SocketChannel.open(new InetSocketAddress(8899));
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);

        // 先注册登陆
        String input = "";
        input = scanner.nextLine();
        // 异步读取
        new Thread(new ClientReadThread()).start();
        if ("/signup".equals(input)) {
            Console console = System.console();
            String username = console.readLine("Please input your username:");
            char[] pwd1 = console.readPassword("Please input your password:");
            char[] pwd2 = console.readPassword("Please confirm your password:");
            if (!String.valueOf(pwd1).equals(String.valueOf(pwd2))) {
                System.out.println("The passwords entered twice do not match. Please input again.\n");
            }
            clientChannel.write(charset.encode(input));
        } else if (("/login".equals(input))) {
            Console console = System.console();
            String username = console.readLine("Please input your username:");
            char[] pwd1 = console.readPassword("Please input your password:");
            clientChannel.write(charset.encode(username + " " + pwd1.toString()));

        }

        while (flag) {
            input = scanner.nextLine();
            switch (input) {
                case "/help":
                    System.out.println(
                        "Available Instructions:\n/help                    --Query instruction set\n/login \n/signup\n/chat <userName>"
                            + "         --Create private chat\n/group <groupName>       --Create new group chat\n/add <user1> <user2>...  "
                            + "--(only)Manager invite users\n/history                 --Query chat records\n/cancel <groupName>      "
                            + "--(only)Manager dissolve group chat \n/exit                    --Close current chat");
                case "/chat":
                    continue;
                default:
                    System.out.println("正确指令格式:  /指令");
            }
            if (("/group".equals(input))) {
                continue;
            } else if ("".equals(input)) {
                System.out.println("输入为空白！");
                continue;
            } else if ("".equals(myName) && input.split("[|]").length == 1) {

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
            SocketChannel clientChannel;
            try {
                while (flag) {
                    selector.select(100); // 调用此方法一直阻塞，直到有channel可用
                    ikeys = selector.selectedKeys().iterator();
                    while (ikeys.hasNext()) {
                        key = ikeys.next();
                        if (key.isReadable()) { // 处理读事件
                            clientChannel = (SocketChannel)key.channel();
                            // 这里的输出是true，从selector的key中获取的客户端channel，是同一个

                            buffer.clear();
                            StringBuilder msgBuilder = new StringBuilder();
                            try {
                                while (clientChannel.read(buffer) > 0) {
                                    buffer.flip(); // 将写模式转换为读模式
                                    msgBuilder.append(charset.decode(buffer));
                                }
                            } catch (IOException en) {
                                en.printStackTrace();
                                System.out.println(en.getMessage() + ",用户'" + key.attachment().toString() + "'线程退出！！");
                                stopMainThread();
                            }
                            String str = StringUtils.substringBefore(msgBuilder.toString(), " ");
                            switch (str) {
                                case "/userName":

                            }
                            String[] strArray = msgBuilder.toString().split("[|]");
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
