
import java.io.Console;

class PasswordTest {
    public static void main(String[] args) {
        try {
            Console console = System.console();
            String username = console.readLine("Please input your username:");

            char[] pwd1 = console.readPassword("Please input your password:");
            System.out.println(username + "---" + String.valueOf(pwd1));

            char[] pwd2 = console.readPassword("Please confirm your password:");
            System.out.println(String.valueOf(pwd2));
            System.out.println("isValid ?= " + String.valueOf(pwd1).equals(String.valueOf(pwd2)));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}