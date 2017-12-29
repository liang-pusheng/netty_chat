package breeze.chatroom.action;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Administrator on 2017/12/26.
 */
public class EchoServerHandler {

    public static void main(String[] args) {
        List<String> list = new ArrayList<String>();
        list.add("one");
        list.add("two");
        list.add("three");
        list.add("four");

        for (String s : list) {
            System.out.println(s + "\n");
        }
        String a = "2";
        double v = Double.parseDouble(a);
    }
}
