package elk.elastic;

import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sf.json.JSONArray; 


public class App 
{
	/*
	 * args0:templateFile,
	 * args1:templateScore,
	 * args2:logtype: 
	 * args3:addtemp/scorelog/appearance
	 * args4:rawLog/log for filter
	 * args5:start
	 * args6:end
	 * args7:scoreSet
	 * args8:node
	 * args9: index
	 */
	
    public static void main( String[] args )
    {
//    	System.out.println(Pattern.matches("idef=\\S+", "idef=\"eth0\"/10.208.144.0:public"));
//    	System.out.println(Snippet.getCompPrefixForMap("clssgmCopyoutMemberInfo\\(.*\\): packed memberNo\\(.*\\) grock\\(.*\\) nodeNum\\(.*\\) privateDataSize\\(.*\\) publicDataSize\\(.*\\) \\S+ \\S+ \\S+ dereg \\S+ orphan \\S+"));
    	
//    	extract e = new extract();
//    	System.out.println(e.cutLine("Start Date               ")[2]);
    	
    	JSONArray jsonA = new JSONArray();
    	
    	String[] nodes = args[8].split(",");
    	extract e = new extract(args[0], args[1], args[2]);
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
		else if (args[3].equals("appearance")) {
			
			
			Map<String, Integer> appearance = e.GetAppearance(
					nodes,
					args[2],
					args[5], 
					args[6],
					args[9]
					);
			for (String temp : appearance.keySet()){
				System.out.println(temp + " ~~ " + appearance.get(temp));
			}
		}
		else if (args[3].equals("buildMatrix")) {
			String[] pathes = args[4].split(",");
			Map<String, List<Integer>> appearance = e.GetAppearance(
					nodes,
					args[2],
					pathes,
					args[9]
					);
			for (String temp : appearance.keySet()){
				System.out.print(temp + " ~~ ");
				for (int app : appearance.get(temp)) {
					System.out.print(app + " - ");
				}
				System.out.println("");
			}
		}
		else if (args[3].equals("MatforEveryFile")) {
			int[][][] ret = e.getMatforEveryFile(nodes, args[2], args[9]);
//			for (int i = 0; i < ret.length; i ++) {
//				for (int j = 0; j < ret[0].length; j ++) {
//					for (int k = 0; k < ret[0][0].length; k ++) {
//						System.out.print(ret[i][j][k] + ",");
//					}
//				}
//			}
			JSONArray jsonArray = JSONArray.fromObject(ret);
			List<String> ls = new ArrayList<String>();
			ls.add(jsonArray.toString());
			extract.writeFile(".//log//matForEveryFile.json", ls);
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
