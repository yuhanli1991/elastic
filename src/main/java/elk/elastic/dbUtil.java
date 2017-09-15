package elk.elastic;

import java.util.HashMap;
import java.util.Map;
import java.sql.*;

public class dbUtil {
	String url;
	String user;
	String passwd;
	ResultSet result = null;
	PreparedStatement pre = null;
	Connection con = null;
	public dbUtil (String url, String user, String passwd) {
		this.url = url;
		this.user = user;
		this.passwd = passwd;
	}
	public ResultSet conAndExeDB(String cmd, String param) {
	    try
	    {
	        Class.forName("oracle.jdbc.driver.OracleDriver");// 加载Oracle驱动程序
	        System.out.println("Trying to connect DB");

	        con = DriverManager.getConnection(url, user, passwd);// 获取连接
	        System.out.println("Connection succeed");
	        pre = con.prepareStatement(cmd);// 实例化预编译语句
	        if (!param.equals("")){
	        	pre.setString(1, param);// 设置参数，前面的1表示参数的索引，而不是表中列名的索引
	        }
	        result = pre.executeQuery();// 执行查询，注意括号中不需要再加参数
//	        while (result.next())
//	            // 当结果集不为空时
//	            System.out.println(result.getInt("LOG_ID") + " " + result.getString("LOG_TEMP"));
	        
	    }
	    catch (Exception e)
	    {
	        e.printStackTrace();
	    }
		return result;
	    
	}
	public void conClose() {
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
	
	
	public Map<String, Integer> getScoreFromDB (String logType) {
		Map<String, Integer> scoreMap = new HashMap<String, Integer>();
		String cmd = "select LOG_TEMP,LOG_PRIORITY from LogTemplateBase WHERE LOG_TYPE=?";
		String param = logType;
		ResultSet result = conAndExeDB(cmd, param);
		
		try {
			while (result.next()){
				String temp = result.getString("LOG_TEMP");
				int score = result.getInt("LOG_PRIORITY");
			    System.out.println(temp + " " + score);
			    scoreMap.put(temp, score);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		conClose();
    	return scoreMap;
    }
	public static void main( String[] args ) {
		
		// String url = "logtde/logtde@//etc1m-c2-scan.us.oracle.com:1521/keydb0906.us.oracle.com";
		String url = "jdbc:oracle:thin:@etc1m-c2-scan.us.oracle.com:1521/keydb0906.us.oracle.com";
		String user = "logtde";
		String passwd = "logtde";
		String cmd = "select * from LogTemplateBase";
		dbUtil db = new dbUtil(url, user, passwd);
		Map<String, Integer> scoreMap = db.getScoreFromDB("asm_alert");
		System.out.println(scoreMap.keySet().size());
//		ResultSet result = db.conAndExeDB(cmd, "");
//		try {
//			while (result.next())
//			    System.out.println(result.getInt("LOG_ID") + " " + result.getString("LOG_TEMP"));
//		} catch (SQLException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		db.conClose();
	}
}
