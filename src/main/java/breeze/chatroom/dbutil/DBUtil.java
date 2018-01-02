package breeze.chatroom.dbutil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

/**
 * Created by Administrator on 2018/1/2.
 */
public class DBUtil {

    private static Connection connection;

    public static void connectionDB(String database, String username, String password) {
        if (database == null) {
            return;
        }
        StringBuffer url = new StringBuffer();
        url.append("jdbc:mysql://localhost:3306/");
        url.append(database);
        url.append("?useUnicode=true&characterEncoding=UTF8");
        try {
            Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager.getConnection(url.toString(), username, password);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void executeSql(String sql, Object... params) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(sql);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
            }
            ps.executeUpdate();
            DBUtil.close(ps);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() {
        return connection;
    }

    public static void close (PreparedStatement ps) {
        try {
            ps.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
