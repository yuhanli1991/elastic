package elk.elastic;

import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class App 
{
	/*
	 * args0:templateFile,
	 * args1:templateScore,
	 * args2:logtype: 
	 * args3:addtemp/scorelog
	 * args4:rawLog/log for filter
	 * args5:start
	 * args6:end
	 * args7:scoreSet
	 * args8:node
	 * args9: index
	 */
	
    public static void main( String[] args )
    {
    	
    	String[] nodes = args[8].split(",");
    	extract e = new extract(args[0], args[1], args[2]);
    	System.out.println("here");
		if (args[3].equals("addtmp")) {
			EsClient ec = new EsClient();
			List<List<String>> snippet;
			
			try {
				snippet = ec.getSnippet(nodes, args[2], args[5], args[6], "rws00fxw-cluster", "rws00fxw.us.oracle.com", 9300, args[9]);
				List<String> content = snippet.get(0);
				content = e.removeDupList(content);
				Collections.sort(content);
				System.out.println("Using " + content.size() + " lines of logs");
				e.addTmp(content);
				//return Snippet.scoreLog(judge.getScore(), snippet.get(1), logType, templatesFile, scoreSet);
			} catch (UnknownHostException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			System.out.println("Adding templates completed, please check " + args[0] + " and " + args[1]);
		}
		else if (args[3].equals("scorelog")) {
			Set<Integer> scoreSet = new HashSet<Integer>();
			String[] scores = args[7].split(",");
			for (String score : scores) {
				scoreSet.add(Integer.valueOf(score));
			}
//			String[] logFiles = args[4].split(":");
			
			List<String> list = e.diagCluster(
					nodes,
					args[2],
					args[5], 
					args[6],
					scoreSet,
					args[9]
					);
			
			System.out.println("####### Begin ########");
			for (String n : list){
				System.out.println(n);
			}
			System.out.println("####### End ########");
			System.out.println(list.size() + " lines left.");
		}
		
		
		
		
//        EsClient ec = new EsClient();
//        try {
//			List<String> list = ec.getSnippet("rws00fxu", "gipcd", "2017-07-17T00:06:31.067Z", "2017-07-17T03:07:31.067Z", "rws00fxw-cluster", "rws00fxw.us.oracle.com", 9300, "test3*").get(0);
//			for (String l : list) {
//				System.out.println(l);
//			}
//			System.out.println(list.size());
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
			System.out.println(df.format(new Date()));// new Date()为获取当前系统时间
//        } catch (UnknownHostException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//        
        
    }
}
