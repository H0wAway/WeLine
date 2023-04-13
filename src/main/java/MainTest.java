public class MainTest {
    public static void main(String[] args) {
        for (String arg : args) {
            if ("/help".equals(arg)) {
                System.out.printf("%-10s", "/version");
                break;
            }
            if ("/version".equals(arg)) {
                System.out.println("v-0.1");
                break;
            }
        }
    }
}
