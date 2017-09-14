package elk.elastic;

import java.sql.Connection;
import java.sql.*;

public class dbUtil {
	public static void conAndExeDB(String url, String user, String passwd, String cmd) {
		Connection con = null;// 创建一个数据库连接
	    PreparedStatement pre = null;// 创建预编译语句对象，一般都是用这个而不用Statement
	    ResultSet result = null;// 创建一个结果集对象
	    try
	    {
	        Class.forName("oracle.jdbc.driver.OracleDriver");// 加载Oracle驱动程序
	        System.out.println("Trying to connect DB");

	        con = DriverManager.getConnection(url, user, passwd);// 获取连接
	        System.out.println("Connection succeed");
	        pre = con.prepareStatement(cmd);// 实例化预编译语句
	        // pre.setString(1, "刘显安");// 设置参数，前面的1表示参数的索引，而不是表中列名的索引
	        result = pre.executeQuery();// 执行查询，注意括号中不需要再加参数
	        while (result.next())
	            // 当结果集不为空时
	            System.out.println(result.getInt("LOG_ID") + " " + result.getString("LOG_TEMP"));
	    }
	    catch (Exception e)
	    {
	        e.printStackTrace();
	    }
	    finally
	    {
	        try
	        {
	            // 逐一将上面的几个对象关闭，因为不关闭的话会影响性能、并且占用资源
	            // 注意关闭的顺序，最后使用的最先关闭
	            if (result != null)
	                result.close();
	            if (pre != null)
	                pre.close();
	            if (con != null)
	                con.close();
	            System.out.println("Connection closed");
	        }
	        catch (Exception e)
	        {
	            e.printStackTrace();
	        }
	    }
	    
	}
	public static Map<String, Integer> getMapFromDB (String url, String user, String passwd, String logType) {
    	return null;
    }
	public static void main( String[] args ) {
		// String url = "logtde/logtde@//etc1m-c2-scan.us.oracle.com:1521/keydb0906.us.oracle.com";
		String url = "jdbc:oracle:thin:@etc1m-c2-scan.us.oracle.com:1521/keydb0906.us.oracle.com";
		String user = "logtde";
		String passwd = "logtde";
		String cmd = "select * from LogTemplateBase";
		dbUtil.conAndExeDB(url, user, passwd, cmd);
	}
}
