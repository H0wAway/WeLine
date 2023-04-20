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

import static java.lang.System.*;

/**
 * @author haowei.chu
 */
public class Client {
    private final Charset charset = Charset.forName("UTF-8");
    private final ByteBuffer buffer = ByteBuffer.allocate(124);
    private Selector selector;
    private String myName = "";
    private boolean flag = true;
    private int statusCode = 0;
    private boolean isChat = false;
    private String chaTarget = "";

    Scanner scanner = new Scanner(in);

    public void startClient() throws IOException {

        // 客户端初始化固定流程
        selector = Selector.open();
        SocketChannel clientChannel = SocketChannel.open ( new InetSocketAddress ( 8888 ) );
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);

        // 异步读取服务器信息
        new Thread(new ClientReadThread()).start();
        // 读取用户信息
        String pwd1 = null;
        String name = null;
        while (flag) {
            String input = scanner.nextLine();
            // 使input始终不能为空
            if ("".equals(input)) {
                out.println("输入为空白！");
                // continue直接进行下次while循环.
                continue;
            }
            if(input.charAt ( 0 )=='/'){
                String instruction = StringUtils.substringBefore(input," " );
                switch (instruction) {
                    case "/help":
                        out.println(
                                "Available Instructions:\n/help                    --Query instruction se" +
                                        "t\n/signup\n/login \n/logoff \n/chat <userName>         --Create private c" +
                                        "hat\n/group <groupName>       --Create new group chat\n/add <user1>" +
                                        " <user2>...  --(only)Manager invite users\n/history                 -" +
                                        "-Query chat records\n/cancel <groupName>      --(only)Manager dissolve" +
                                        " group chat \n/exit                    --Close current chat");
                        break;

                        // 登录
                    case "/login":

                        if(statusCode==2){
                            out.println ( "已登录, 用 '/logoff' 注销." );
                            break;
                        }else{
                            statusCode=1;
                            out.println ( "请输入用户名和密码(用空格隔开)：" );
                        }
                        break;

                        // 注册
                    case "/signup":
                        if(statusCode==2){
                            out.println ( "已登录, 用 '/logoff' 注销." );
                            break;
                        }else{
                            statusCode=3;
                            out.println ( "请输入用户名：" );
                        }
                        break;

                    case "/chat":
                        if (statusCode==0){
                            out.println ( "请先登录哦." );
                        }
                        else{
                            String c = input.substring ( 6 );
                            if (myName.compareTo ( c ) < 0) {
                                chaTarget = myName + c;
                            } else if (myName.compareTo ( c ) > 0) {
                                chaTarget = c + myName;
                            }
                            // c为私聊目标.
                            clientChannel.write(charset.encode("CHAT|"+chaTarget+"|"+c+"|"));
                        }
                        isChat = true;
                        break;
                    case "/group":
                        clientChannel.write(charset.encode("GROUP|"+input.substring ( 7 )));
                        isChat = true;
                        break;
//                    case "/add":
//                        input= "ADD|"+chaTarget+"|"+input;
//
//                        break;
                    case "/history":
                        clientChannel.write(charset.encode("HISTORY|"+chaTarget));
                        break;
//                    case "/cancel":
//                        input= "GROUP|CANCEL";
//                        break;
//                        // 退出后服务器更新LastOnline
//                    case "/exit":
//                        input ="EXIT|"+myName+"|"+chaTarget;
//                        isChat = false;
//                        break;
                    case "/logoff":
                        input ="LOGOFF|"+myName+"|";
                        statusCode = 0;
                        try {
                            clientChannel.write(charset.encode(input));
                        } catch (Exception e) {
                            out.println(e.getMessage() + "客户端主线程退出连接！！");
                        }
                        break;
                    default:
                        out.println ( "指令"+input+"无效，输入 /help 查看帮助指南." );
                }
            }else{
                switch(statusCode){
                    case 1:
                        String[] arr = input.split( " " );
                        clientChannel.write ( charset.encode ( "LOGIN|" +arr[0]+"|"+arr[1]+"|") );
                        break;
                    case 2:
                        if(!isChat){
                            out.println ( "Please use '/chat' or '/group' to join the chatroom before sending a message" );
                        }else{
                            clientChannel.write(charset.encode("MESSAGE|"+chaTarget+"|"+input));
                        }
                        break;
                    case 3:
                        name=input;
                        statusCode=4;
                        out.println ( "请输入密码：" );
                        break;
                    case 4:
                        pwd1=input;
                        statusCode=5;
                        out.println ( "请确认密码：" );
                        break;
                    case 5:
                        if(input.equals ( pwd1 )){
                            clientChannel.write ( charset.encode ( "SIGNUP|" +name+"|"+pwd1+"|" ) );
                        }else{
                            statusCode=4;
                            out.println ( "密码不匹配，请重新输入密码：" );
                        }
                        break;
                    default:
                        out.println ( "请先登录.(/signup注册 /login登录)" );
                }
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
                            // 从selector的key中获取的客户端channel，是同一个

                            buffer.clear();
                            StringBuilder msgBuilder = new StringBuilder();
                            try {
                                while (clientChannel.read(buffer) > 0) {
                                    buffer.flip(); // 将写模式转换为读模式
                                    msgBuilder.append(charset.decode(buffer));
                                }
                            } catch (IOException en) {
                                en.printStackTrace();
                                out.println(en.getMessage() + ",用户'" + key.attachment().toString() + "'线程退出！！");
                                stopMainThread();
                            }
                            String[] instruction = msgBuilder.toString().split( "[|]" );
                            if(msgBuilder.charAt ( 0 )=='/'){
                                switch (instruction[0]) {
                                    case "/signupFail":
                                        out.println ( instruction[1] );
                                        statusCode=3;
                                        out.println ( "请输入用户名：" );
                                        break;
                                    case "/loginFail":
                                        statusCode=1;
                                        out.println ( instruction[1] );
                                        clientChannel.write ( charset.encode (  "LOGIN|"+login ()) );
                                        break;
                                    case "/Success":
                                        statusCode=2;
                                        myName = instruction[1];
                                        key.attach(myName);
                                        out.println ( instruction[2]);
                                        break;
                                    default:
                                        out.println ( "Iron man." );
                                }
                            }else{
                                out.println ( msgBuilder );
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
        out.println ( "Please input your username:" );
        String pwd1 = scanner.nextLine ();
        out.println ( "Please input your password:" );
        String pwd2 = scanner.nextLine ();
        return pwd1+"|"+pwd2;
    }

    public String getUserName(){

        return scanner.nextLine ( );
    }

    public String getSignupPwd(){

        String pwd1 = scanner.nextLine ();
        out.println( "Please confirm your password:" );
        String pwd2 = scanner.nextLine ();
        if (!pwd1.equals(pwd2)) {
            out.println("The passwords entered twice do not match. Please input again.\n");
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
