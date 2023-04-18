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

/**
 * @author haowei.chu
 */
public class Client {
    private final Charset charset = Charset.forName("UTF-8");
    private ByteBuffer buffer = ByteBuffer.allocate(124);
    private SocketChannel clientChannel;
    private Selector selector;
    private String myName = "";
    private boolean flag = true;
    private boolean isRegister = false;
    private boolean isChat = false;
    private String chaTarget = "";

    Scanner scanner = new Scanner(System.in);

    public void startClient() throws IOException {

        // 客户端初始化固定流程
        selector = Selector.open();
        clientChannel = SocketChannel.open(new InetSocketAddress(8888));
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);

        // 异步读取
        new Thread(new ClientReadThread()).start();
        while (flag) {
            String input = scanner.nextLine();
            // 使input始终不能为空
            if ("".equals(input)) {
                System.out.println("输入为空白！");
                continue;
            } else if(input.charAt ( 0 )=='/'){
                String instruction = StringUtils.substringBefore(input," " );
                switch (instruction) {
                    case "/help":
                        System.out.println(
                                "Available Instructions:\n/help                    --Query instruction se" +
                                        "t\n/signup\n/login \n/logoff \n/chat <userName>         --Create private c" +
                                        "hat\n/group <groupName>       --Create new group chat\n/add <user1>" +
                                        " <user2>...  --(only)Manager invite users\n/history                 -" +
                                        "-Query chat records\n/cancel <groupName>      --(only)Manager dissolve" +
                                        " group chat \n/exit                    --Close current chat");
                        break;
                        // 登录
                    case "/login":
                        if(!isRegister){
                            input="LOGIN|"+login ();
                        }else{
                            System.out.println ( "Already logged, input '/logoff' to log off." );
                        }
                        break;

                        // 注册
                    case "/signup":
                        if(!isRegister){
                            clientChannel.write ( charset.encode ( "SIGNUP|"+getUserName () ) );
                        }else{
                            System.out.println ( "Already logged, input '/logoff' to log off." );
                        }
                        break;
                    case "/chat":
                        if (!isRegister){
                            System.out.println ( "Please login." );
                        }
                        else{
                            String c = input.substring ( 6 );
                            switch(myName.compareTo ( c )){
                                case -1:
                                    chaTarget = myName + c;
                                    break;
                                case 1:
                                    chaTarget = c + myName;
                            }
                            // c为私聊目标.
                            input="CHAT|"+chaTarget+"|"+c;
                        }
                        isChat=true;
                        break;
                    case "/group":
                        chaTarget=input.substring ( 7 );
                        input= "GROUP|"+chaTarget;
                        isChat = true;
                        break;
                    case "/add":
                        input= "ADD|"+chaTarget+"|"+input;
                        break;
                    case "/history":
                        input= "HISTORY|"+chaTarget;
                        break;
                    case "/cancel":
                        input= "GROUP|CANCEL";
                        break;
                        // 退出后服务器更新LastOnline
                    case "/exit":
                        input ="EXIT|"+myName+"|"+chaTarget;
                        isChat = false;
                        break;
                    case "/logoff":
                        input ="LOGOFF|"+myName+"|";
                        isRegister = false;
                        break;
                    default:
                        System.out.println ( "指令"+input+"无效，输入 /help 查看帮助指南." );
                }
            } else if(!isChat){
                System.out.println ( "Please use '/chat' or '/group' to join the chatroom before sending a message" );
            } else{
                input =  "MESSAGE|"+chaTarget+"|"+input;
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
                            if(msgBuilder.charAt ( 0 )=='/'){
                                String[] instruction = msgBuilder.toString().split( "[|]" );
                                switch (instruction[0]) {
                                    case "/UserName":
                                        clientChannel.write (charset.encode("SIGNUP|"+getUserName()));
                                        break;
                                    case "/Pwd":
                                        String sc = getSignupPwd ();
                                        clientChannel.write ( charset.encode ("PWD|"+instruction[1]+"|"+sc));
                                        break;
                                    case "/loginFail":
                                        System.out.println ( instruction[1] );
                                        clientChannel.write ( charset.encode (  "LOGIN|"+login ()) );
                                        break;
                                    case "/Success":
                                        isRegister=true;
                                        myName = instruction[1];
                                        key.attach(myName);
                                        System.out.println ( instruction[2]);
                                        break;
                                    default:
                                        System.out.println ( msgBuilder.toString () );
                                }
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

    public void stopMainThread() {
        flag = false;
    }



    public String login(){
        System.out.println ( "Please input your username:" );
        String pwd1 = scanner.nextLine ();
        System.out.println ( "Please input your password:" );
        String pwd2 = scanner.nextLine ();
        return pwd1+"|"+pwd2;
    }

    public String getUserName(){
        System.out.println ( "Please input your username:" );
        String name;
        name = scanner.nextLine ( );
        return name;
    }

    public String getSignupPwd(){
        System.out.println ( "Please input your password:" );
        String pwd1 = scanner.nextLine ();
        System.out.println("Please confirm your password:" );
        String pwd2 = scanner.nextLine ();
        if (!pwd1.equals(pwd2)) {
            System.out.println("The passwords entered twice do not match. Please input again.\n");
            getSignupPwd ();
        }
        return pwd1;
    }



    public static void main(String[] args) {
        try {
            new Client().startClient();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
