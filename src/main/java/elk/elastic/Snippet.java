package elk.elastic;



import java.util.ArrayList;

import java.util.List;
import java.util.*;
import java.util.regex.*;
//import org.json.JSONObject;



public class Snippet {
	/*
	 * get log snippet according to time stamp
	 * @param log file location
	 * @return log as list
	 */
	static int defaultScore = 2;
	public static List<String> getSnippet(String file, String from, String to){
		//extract e = new extract(" ");
		List<String> loglist = extract.readFile(file);
		List<String> ret = new ArrayList<String>();
		String format = "yyyy-MM-dd HH:mm:ss";
		int fromInt = Integer.valueOf(DateUtil.date2TimeStamp(from, format));
		int toInt = Integer.valueOf(DateUtil.date2TimeStamp(to, format));
		
		boolean isFound = false;		//True if pointer is inside the range
		
		List<String> lastLines = new ArrayList<String>();
		
		for (int i = loglist.size() - 1; i >= 0; i --){
			String str = loglist.get(i);
			if (str.length() >= 19 
        			&& Character.isDigit(str.charAt(0)) 
        			&& str.charAt(10) == ' '){
        		
        		String date = str.substring(0, 19);
        		//System.out.println(str.substring(0, 19));
        		
        		int secs = Integer.valueOf(
        				DateUtil.date2TimeStamp(date, format));
        		//System.out.println(date);
        		if (secs >= fromInt && secs <= toInt){
        			//System.out.println(str);
        			isFound = true;
        			ret.addAll(lastLines);
        			ret.add(str);
        			lastLines.clear();
        		}
        		else if (secs <= fromInt){
        			break;
        		}
        		
        	}
			else if (str.length() != 0 && isFound) {
				lastLines.add(str);
			}
		}
		return ret;
	}
	
	public static List<String> getSnippet(String[] files, String from, String to){
		List<String> loglist = new ArrayList<String>();
		for (int i = files.length - 1; i >= 0; i --) {
			List<String> newList = getSnippet(files[i], from, to);
			if (newList.isEmpty() && !loglist.isEmpty())
				break;
			loglist.addAll(newList);
		}
		return loglist;
	}
	
	public static boolean hasCompPrefix (String line) {
		//return Pattern.matches("^[A-Za-z][A-Za-z0-9_]+(\\(\\))?:.*", line);
		return true;
	}
	
	public static String getCompPrefix (String line) {
		boolean hasQuote = false;
		extract e = new extract();
		int tail = line.length();
//		if (Pattern.matches("^[A-Za-z]{3,}:[0-9]{2,}: .*", line)) {
//			String first = line.split("\\s+")[1];
//			return first.split(":")[0];
//			
//		}
//		else 
		for (int i = 0; i < line.length(); i ++) {
			if (line.charAt(i) == '[' || line.charAt(i) == '(' || line.charAt(i) == '\'')
				hasQuote = true;
			if (line.charAt(i) == ':' || line.charAt(i) == ' ') {
				if (!hasQuote)
					tail = i;
				break;
			}
		}
		String cutComp = "";
		String comp = line.substring(0, tail);
		if (!hasQuote ){
			return compCorrect(comp);
		}
		else {
			cutComp = e.cutLine(comp)[0];
			if (cutComp.charAt(cutComp.length() - 1) == ':')
				cutComp = cutComp.substring(0, cutComp.length() - 1);
			return cutComp;
		}
		
	}
	
	public static String getCompPrefixForMap (String line) {
		int tail = line.length();
		int head = 0;
		for (int i = 0; i < line.length(); i ++) {
			if (line.charAt(i) == ':' || line.charAt(i) == ' ') {
				tail = i;
				break;
			}
		}
		return line.substring(head, tail);
	}
	
	
	//key: component for gipc/ocssd
	public static Map<String, List<String>> mapComponent (List<String> list) {
		Map<String, List<String>> retMap = new HashMap<String, List<String>>();
//		extract e = new extract();
		for (int j = 0; j < list.size(); j ++ ) {
			String line = list.get(j);
			if (!hasCompPrefix(line))
				continue;
			String comp = getCompPrefixForMap(line);			//deal with first word
			
			
			
			//将提取的comp加入map之中
			if (retMap.containsKey(comp)) {
				List<String> newList = retMap.get(comp);
				newList.add(line);
				retMap.put(comp, newList);
				
			}
			else {
				List<String> newList = new ArrayList<String>();
				newList.add(line);
				retMap.put(comp, newList);
			}
			
			
		}
		return retMap;
	}
	
	public static Map<String, List<String>> mapComponent (String file) {
		List<String> list = extract.readFile(file);
		return mapComponent (list);
		
	}
	
	public static boolean mapMatch (Map<String, List<String>> map, String comp, String line, String logType) {
		if (map.containsKey(comp)){
			for (String template : map.get(comp)) {
				if (isMatched (template, line, logType))
					return true;
			}
		}
		return false;
	}
	
	public static boolean mapMatchNoStamp (Map<String, List<String>> map, String comp, String line, String logType) {
		if (map.containsKey(comp)){
			for (String template : map.get(comp)) {
				if (isMatchedNoStamp (template, line, logType))
					return true;
			}
		}
		return false;
	}
	
	public static String mapMatchTemplate (Map<String, List<String>> map, String comp, String line, String logType, boolean hasStamp) {
//		for (int i = 0; i < line.length(); i ++) {
//			if (line.charAt(i) != ' ') {
//				line = line.substring(i, line.length());
//				break;
//			}
//		}
		
		if (map.containsKey(comp))
			for (String template : map.get(comp)) {
				if (hasStamp) {
					if (isMatched (template, line, logType))
						return template;
				}
				else {
					if (isMatchedNoStamp (template, line, logType))
						return template;
				}
			}
		return "";
	}
	
	
	public static boolean traverseMatch (List<String> templates, String line, String logType) {
		for (String template : templates){
			if (isMatched (template, line, logType))
				return true;
		}
		return false;
	}
	
	public static boolean hasTimeStamp (String line) {
		String timestamp = 
				"^[0-9]{4}-.*";
		return Pattern.matches(timestamp, line);
	}
	
	
	public static String compCorrect (String comp) {
		//处理第一个word是res的情况
		if (Pattern.matches("^ora\\.([A-Za-z0-9_\\.]+\\.)?[A-Za-z_0-9]+:?", comp)) {
			String[] s = comp.split("\\.");
			//comp = s[0] + "\\S+" + s[s.length - 1];
			comp = s[0] + "\\S+";
		}
		else if (Pattern.matches("\\S+:[0-9]+:", comp)) {
			String[] s = comp.split(":");
			comp = s[0] + ":" + "\\S+";
		}
		return comp;
	}
	
	public static boolean hasLogComp (String line, String logType) {
		String[] s = null;
		if (logType.equals("ocssd") || logType.equals("gipcd"))
			s = line.split(":");
		else if (logType.equals("alert"))
			s = line.split("\\s+");
		else if (logType.equals("crsd"))
			s = line.split(":");
		
		return hasLogComp(s, logType);
	}
	
	public static boolean hasLogComp (String[] s, String logType) {
		if (logType.equals("ocssd") || logType.equals("gipcd"))	{
			if (s.length > 5) {
				return true;
			}
			else return false;
		}
		else if (logType.equals("alert")){
			if (s.length > 3)
				return true;
			else return false;
		}
		else if (logType.equals("crsd")){		//有风险！
			if (s.length > 5)
				return true;
			else return false;
		}
		return false;
	}
	
	public static String getLogComp (String line, String logType) {
		String comp = mySplit(line, logType);
		
		return compCorrect(comp);
	}
	
	
	//从第五个冒号开始 到下一个冒号或者空号结束
	public static String mySplit (String line, String logType) {		//需要加入crsd的
		int head = -1;
		int num = 0;
		extract e = new extract();
		boolean hasQuote = false;
		
		if (logType.equals("ocssd") || logType.equals("gipcd")) {
			for (int i = 0; i < line.length(); i ++ ) {
				if (head == -1 && line.charAt(i) == ':') {
					num ++;
				}
				if (head == - 1 && num == 5) {
					while(i < line.length() - 2 && line.charAt(i + 1) == ' ')
						i ++;
					head = i + 1;
					if (!Character.isLetter(line.charAt(head)) && line.charAt(head) != '(')
						return "";
					if (Character.isDigit(line.charAt(head))) {
						return "";
					}
				}	
				if (!hasQuote && line.charAt(i) == '(')
					hasQuote = true;
				if (head != -1 && i > head && (line.charAt(i) == ' ' || (line.charAt(head) != '(' && line.charAt(i) == ':'))) {
					return hasQuote ? e.cutLine(line.substring(head, i))[0] : line.substring(head, i);
				}
				if (head != -1 && i > head && i == line.length() - 1) {
					return line.substring(head, i + 1);
				}
			}
			return "";
		}
		else if (logType.equals("alert")) {
			for (int i = 0; i < line.length(); i ++ ) {
				if (head == -1 && line.charAt(i) == ' ') {
					num ++;
				}
				if (head == -1 && num == 3) {
					while(i < line.length() - 2 && line.charAt(i + 1) == ' ')
						i ++;
					head = i + 1;
				}
				if (!hasQuote && line.charAt(i) == '(')
					hasQuote = true;
				if (head != -1 && i > head && (line.charAt(i) == ' ' || line.charAt(i) == ':'))  {
					return hasQuote ? e.cutLine(line.substring(head, i))[0] : line.substring(head, i);
				}
				if (head != -1 && i > head && i == line.length() - 1) {
					return line.substring(head, i + 1);
				}
			}
			return "";
		}
		else if (logType.equals("crsd")) {
			//deal with crsd log
			for (int i = 0; i < line.length(); i ++ ) {
				if (head == -1 && line.charAt(i) == ':') {
					num ++;
				}
				if (head == -1 && num == 5) {
					
					while(i < line.length() - 2 && line.charAt(i + 1) == ' ')
						i ++;
					head = i + 1;
					while (line.charAt(head) == '{') {
						while(i < line.length() - 2  && line.charAt(i + 1) != ' ')
							i ++;
						while(i < line.length() - 2  && line.charAt(i + 1) == ' ')
							i ++;
						head = i + 1;
					}
				}
				if (!hasQuote && (line.charAt(i) == '(' || line.charAt(i) == '['))
					hasQuote = true;
				
				if (head != -1 && i > head && (line.charAt(i) == ' ' || line.charAt(i) == ':'))  {
					return hasQuote ? e.cutLine(line.substring(head, line.length()))[0] : line.substring(head, i);
				}
				if (head != -1 && i > head && i == line.length() - 1) {
					return line.substring(head, i + 1);
				}
			}
			return "";
		}
		return "";
	}
	
//	public static String getLogComp (String[] s) {
//		extract e = new extract();
////		System.out.println(s[5].trim());
//		if (Pattern.matches("\\s+", s[5]))
//			return " ";
//		if (Character.isDigit(s[5].trim().charAt(0)))
//			return " ";
//		String c = s[5].trim().split("\\s+")[0];
//		if (c.length() > 1) {
//			if (c.contains("("))
//				return e.cutLine(c)[0];
//			else
//				return c;
//		}
//		else if (c.length() == 1)	{
//			String cut = "";
//			for (int i = 5; i < s.length; i ++) {
//				cut += s[i] + ":";
//			}
//			
//	//		return cut.trim().split("\\s+")[0];
//	//		return e.cutLine(cut)[0];
//			return getCompPrefixForMap(e.cutLine(cut.trim().split("\\s+")[0])[0].trim());
//		}
//		else {
//			return "";
//		}
//	}
	
	
//	public static String searchAndScore (Map<String, List<String>> map, Map<String, Integer> jsonMap, String comp, String line, String logType, boolean hasStamp) {
//		String temp = mapMatchTemplate(map, comp, line, logType, hasStamp);
//		if (temp != "") {		//匹配到了，根据文件打分
//			try {
//				return line + " " + jsonMap.get(temp);
//			}
//			catch (Exception e1){
//				e1.printStackTrace();
//				return "";
//			}
//		}
//		else {					//没有匹配到，打分defaultScore
//			try {
//				return line + " " + defaultScore;
//			}
//			catch (Exception e1){
//				e1.printStackTrace();
//				return "";
//			}
//		}
//	}
	
	
	//给定需要的log score，输出这些score的line
	public static int searchAndScore (Map<String, List<String>> map, Map<String, Integer> jsonMap, String comp, String line, String logType, Set<Integer> scoreSet, boolean hasStamp) {
		String temp = mapMatchTemplate(map, comp, line, logType, hasStamp);
		
		
		if (temp != "") {		//匹配到了，根据文件打分
			try {
				int score = jsonMap.get(temp);
				return scoreSet.contains(score) ? score : -1;
			}
			catch (Exception e1){
				System.out.println(comp);
				e1.printStackTrace();
				return -1;
			}
		}
		else {					//没有匹配到，打分defaultScore
			return defaultScore;
		}
	}
	
	public static boolean isMatched (String template, String line, String logType) {
		String timestamp = 
				"^[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}:[0-9]{2}.[0-9]{3}";
		String forShortLine = "[ \\+]\\S+\\s*\\S+ ";			//对较短行的处理，可能存在风险！！
		String component = "";
		//支持不同类型的日志处理
		if (logType.equals("alert")) {
			component = " (\\[.*\\]\\S+:)?\\s*";
		}
		else if (logType.equals("gipcd")) {
			component = " :\\s*[A-Za-z]+:[0-9]+\\s*:\\s*";
		}
		else if (logType.equals("ocssd")) {
			component = " :\\s*[A-Za-z]+:[0-9]+\\s*:\\s*";
		}
		else if (logType.equals("crsd")) {
			component = " :\\s*[A-Za-z]+:[0-9]+\\s*:\\s*(\\{[0-9]+:[0-9]+:[0-9]+\\}\\s*){0,2}\\s*";
//			forShortLine = "\\*:kgfn.c\\S+:\\s*";
		}
		else
			component = ".*";			//!!!如果指定无效，则使用通配符
		
		
		//////////////////////
		
		if (Pattern.matches(timestamp + component + template + "\\s*", line))
			return true;
		else if (Pattern.matches(timestamp + forShortLine + template + "\\s*", line))
			return true;
		
		return false;
	}
	
	public static boolean isMatchedNoStamp (String template, String line, String logType) {
		String NoStamp = "";
		if (Pattern.matches(NoStamp + template + "\\s*", line))
			return true;
		return false;
	}

	
	public static List<String> filter(String templateFile, List<String> snippet, String logType){
		/*
		 * 07/05
		 * 用于过滤掉已在template中出现过的log，用于测试
		 * 其中，对于几种情况的log直接抛弃（假设是噪声）
		 * 1. 没有时间戳并且不是以字母开头的
		 * 2. 有时间戳但是comp不是以字母或者(开头的 (数字开头的被抛弃)
		 * 3. 有时间戳但是comp为空的
		 * 4. 只有时间戳没有内容的
		 * 
		 * 注意，在脚本cut log的时候需要把有时间戳和没有的分别处理，并最终放在一个文件中
		 * 
		 */
		
		List<String> ret = new ArrayList<String>();
		
//		for (String template : templates){
//			System.out.println(template);
//		}
		System.out.println("Snippet size:  " + snippet.size());
		
		Map<String, List<String>> map = mapComponent(templateFile);
		for (int i = snippet.size() - 1; i >= 0; i --){
			String line = snippet.get(i);
			
			if (!hasTimeStamp(line)){		//没有时间戳的行
				if (Character.isLetter(line.trim().charAt(0))) {		//抛弃掉不以字母开头的行
					String comp = getCompPrefix(line.trim());
					if (!mapMatchNoStamp(map, comp, line.trim(), logType))
						ret.add(line);
				}
			}
			else {				//以数字开头带有时间戳的
				//不处理数字开头的comp的line
				String comp = "";
//				String[] s = line.split(":");		//Save time
				boolean has = hasLogComp(line, logType);
				if (has)
					comp = getLogComp(comp, logType);
				
				if (comp.length() > 0 && !Character.isLetter(comp.charAt(0)) && comp.charAt(0) != '(')
					continue;
				/////
				//不处理空comp
				if (has && comp.length() == 0)
					continue;
				///
				//只有时间戳的情况
				String timeStamp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}:[0-9]{2}.[0-9]{3,6}(\\-[0-9]{2}:[0-9]{2})?$";
				if (Pattern.matches(timeStamp, line)) 
					continue;
				////////////////
				
				if (has) {
					if (Pattern.matches("^[A-Z_]+=.+", comp))
						comp = comp.split("=")[0] + "=" + "\\S+";
					if (!mapMatch(map, comp, line, logType))
						ret.add(line);
				}
				else {
					ret.add(line);		//for test
				}
			}
		}
		return ret;
	}
	
	public static List<String> filterForAddTmp (String templateFile, List<String> snippet){
		List<String> templates = extract.readFile(templateFile);
		List<String> ret = new ArrayList<String>();
		boolean f = true;
		//System.out.println(snippet.size());
		for (int i = snippet.size() - 1; i >= 0; i --){
			f = true;
			for (String template : templates){
//				if (snippet.get(i).equals(
//						"gipcdNodeSendAck: Recvd an ack from client thread, clnt header:(req: 0x7f33780d4e70 [hostname(rwsba10), id (0000000000000270Trace file /scratch/u01/app/crsusr/diag/crs/rwsba07/crs/trace/gipcd.trc"))
//				{
//					System.out.println(template);
//				}
				if (Pattern.matches("^" + template + "\\s*", snippet.get(i))){
					f = false;
					break;
				}
			}
			if (f){
				ret.add(snippet.get(i));
			}
		}
//		for (String s : ret)
//			System.out.println(s);
//		
//		System.out.println("newLine size: " + ret.size());
		
		return ret;
	}
	
	
//	public static List<String> scoreLog(Map<String, Integer> jsonMap, List<String> snippet, String logType, String templateFile){
//		List<String> ret = new ArrayList<String>();
//		Map<String, List<String>> map = mapComponent(templateFile);
//		
//		System.out.println("Snippet size:  " + snippet.size());
//		
//		
//		for (int i = snippet.size() - 1; i >= 0; i --){
//			String line = snippet.get(i);
//			if (!hasTimeStamp(line)) {							//没有时间戳
//				if (Character.isLetter(line.trim().charAt(0))) {		//以字母开头的行
//					String comp = getCompPrefix(line.trim());
//					ret.add(searchAndScore (map, jsonMap, comp, line, logType, false));
//				}
//				else {											//不以字母开头的，直接作为噪声处理
//					ret.add(line + " " + -1);
//				}
//			}
//			else {			//有时间戳
//				//不处理数字开头的comp的line
//				String comp = "";
//				boolean has = hasLogComp(line, logType);
//				if (has)
//					comp = getLogComp(line, logType);
//				
//				if (comp.length() > 0 && !Pattern.matches("^[A-Za-z\\(].*", comp)){
//					ret.add(line + " " + -1);
//					continue;
//				}
//				/////
//				//不处理空comp
//				if (has && comp.length() == 0) {
//					ret.add(line + " " + -1);
//					continue;
//				}
//				////
//				String timeStamp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}:[0-9]{2}.[0-9]{3,6}(\\-[0-9]{2}:[0-9]{2}$)?";
//				if (Pattern.matches(timeStamp, line)) {
//					ret.add(line + " " + -1);
//					continue;
//				}
//				////////////////
//				
//				if (has) {
////					String comp = getLogComp(line);
//					if (Pattern.matches("^[A-Z_]+=.+", comp))
//						comp = comp.split("=")[0] + "=" + "\\S+";
//					ret.add(searchAndScore(map, jsonMap, comp, line, logType, true));
//				}
//				else {
//					ret.add(line + " " + 0);			//有时间戳但没有comp的 作为噪声处理
//				}
//			}
//		}
//		return ret;
//	}
	
	
	public static boolean isStrangeLine(String line) {
		if (line.length() > 2000)
			return false;
		List<String> StrangeList = new ArrayList<String>();
		StrangeList.add("xEE");
		StrangeList.add("Agent' ");
		StrangeList.add("\u0002");
		StrangeList.add("\u0001");
		
		
		for (String l : StrangeList) {
			if (line.contains(l)) {
				return true;
			}
		}
		return false;
	}
	
	
	public static List<String> scoreLog(Map<String, Integer> jsonMap, List<String> snippet, String logType, String templateFile, Set<Integer> scoreSet, List<String> messageList) {
		List<String> ret = new LinkedList<String>();
		Map<String, List<String>> map = mapComponent(templateFile);
		boolean has = true;
		
		
		System.out.println("Snippet size:  " + snippet.size());
		
		int score = -1;
		for (int i = 0; i < snippet.size(); i ++){
			String line = snippet.get(i);
			if (line.length() > 2000)
				continue;
			if (Pattern.matches("\\s+", line))			//处理空行
				continue;						
			//没有时间戳
			if (Character.isLetter(line.charAt(0))) {		//以字母开头的行
				String comp = getCompPrefix(line);
				
				if (Pattern.matches("^[A-Z_]+=.+", comp))
					comp = comp.split("=")[0] + "=" + "\\S+";
				
				
				
				score = searchAndScore(map, jsonMap, comp, line, logType, scoreSet, false);
				if (score != -1)
					if (!isStrangeLine(line))
						ret.add(messageList.get(i) + " " + score);
			}
			else {											//不以字母开头的，直接作为噪声处理，抛弃
				//ret.add(line + " " + -1);
			}
		}
		return ret;
	}
	
	public static List<String> scoreLog(Map<String, Integer> jsonMap, List<String> snippet, String logType, String templateFile, Set<Integer> scoreSet){
		List<String> ret = new ArrayList<String>();
		Map<String, List<String>> map = mapComponent(templateFile);
		
		System.out.println("Snippet size:  " + snippet.size());
		
		int score = -1;
		for (int i = snippet.size() - 1; i >= 0; i --){
			String line = snippet.get(i);
			if (Pattern.matches("\\s+", line))			//处理空行
				continue;
			if (!hasTimeStamp(line)) {							//没有时间戳
				if (Character.isLetter(line.trim().charAt(0))) {		//以字母开头的行
					String comp = getCompPrefix(line.trim());
					score = searchAndScore(map, jsonMap, comp, line, logType, scoreSet, false);
					if (score != -1)
						ret.add(line + " " + score);
				}
				else {											//不以字母开头的，直接作为噪声处理，抛弃
					//ret.add(line + " " + -1);
				}
			}
			else {			//有时间戳
				//不处理数字开头的comp的line
				
				//Don't deal with extra long line
				if (line.length() > 2000)
					continue;
				
				String comp = "";
				boolean has = hasLogComp(line, logType);
				if (has)
					comp = getLogComp(line, logType);
				
				if (comp.length() > 0 && !Pattern.matches("^[A-Za-z\\(].*", comp)){
					//ret.add(line + " " + -1);
					continue;
				}
				
				/////
				//不处理空comp
				if (has && comp.length() == 0) {
					//ret.add(line + " " + -1);
					continue;
				}
				////
				String timeStamp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}:[0-9]{2}.[0-9]{3,6}(\\-[0-9]{2}:[0-9]{2}$)?";
				if (Pattern.matches(timeStamp, line)) {
					//ret.add(line + " " + -1);
					continue;
				}
				////
				timeStamp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}:[0-9]{2}.[0-9]{3,6}(\\-[0-9]{2}:[0-9]{2})?\\*.*";
				if (Pattern.matches(timeStamp, line)) {
					//ret.add(line + " " + -1);
					continue;
				}
				////////////////
				
				if (has) {
//					String comp = getLogComp(line);
					if (Pattern.matches("^[A-Z_]+=.+", comp))
						comp = comp.split("=")[0] + "=" + "\\S+";
					score = searchAndScore(map, jsonMap, comp, line, logType, scoreSet, true);
					if (score != -1)
						ret.add(line + " " + score);
				}
				else {
					//ret.add(line + " " + 0);			//有时间戳但没有comp的 作为噪声处理
				}
			}
		}
		return ret;
	}
	
	
	// Too ugly to deal with multiple space line
//	private static String multiSpace(String line){
//		String[] sArray = line.split(" ");
//		String ret = "";
//		for (String word : sArray) {
//			ret += word + "\\s*";
//		}
//	}
	
}
