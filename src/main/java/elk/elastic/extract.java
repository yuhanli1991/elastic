package elk.elastic;

/*
 * created on 2017/06/15
 * @author yuhan.l.li@Oracle.com
 * 
 * This class is for extracting log line templates from a sorted/cut log file.
 * 
 * 06/17:
 * Can deal with path, quote or parameter directly.
 * only extract template from lines with the same length
 * extract 2 times to shrink output
 * 
 * smarter split log line (consider "".(),[],'') cutLine()
 * 
 * Can't write into file in writeFile()
 * 
 * 06/19:
 * add snippet class to getSnippet and do filtering according to templates.txt
 * 
 * should convert * to regular expression
 * 
 * 
 * 06/19 Problem:
 * 1. quote word have space inside, should set a better regx ".*"
 * 2. some lines will be shorter and be cut one more word
 * 3. 缺少某一些模板，似乎和全大写有关系。在同一个partition中，后部分的似乎被抛弃了（修复）
 * 4. 拥有对应模板，但是没有被过滤掉，可能是snippet模块的问题
 * 
 *06/21
 *1. 无法处理多个空格（目前看来，多个空格的日志都是报错信息）
 *2. 无法处理开头位置不一致的问题 （目前看来，开头短的一般是错误信息）
 *3. 修复了snippet很长的bug
 *4. 添加JSON的功能，实现JSON的读写，评分机制
 *
 *06/22
 *1. 修复了开头不一致的问题
 *2. 解决了引用词语后面跟标点符号的问题
 *
 *
 *06/25
 *1. 处理了过滤后日志大量重复行的情况
 *2. 修复了cultine无法正确处理正则表达式的情况
 *3. 多空格无法处理
 *4. 多重引文字符串可以处理   'ls /u01/app/12.2.0/grid/usm/install/Oracle/EL6UEK/x86_64/3.8.13-13/3.8.13-13-x86_64/bin/oracleacfs.ko | /sbin/weak-modules --verbose --dry-run --no-initramfs --add-modules 2>&1 | grep -w 'with kernel 3.8.13-68.3.4.el6uek.x86_64' | grep -v 'with kernel 3.8.13-68.3.4.el6uek.x86_64.debug' | grep -v ksplice'.
 *5. 对较短行进行两个词的匹配，可能存在风险
 *6. 修改了addTmp，它能返回新加入的template的信息，并将新加入的加在scoreFile末尾，之前的分数信息能够被保留（依然有问题，需要一个更好的方法保存打分信息）
 *		（每次addTmp之后，将结果去JsonMap里面寻找，如果存在，则保修对应分数，如果不存在，则初始化分数）！！！！！！
 *7. 手动修改了多空格模板，但是每次addTmp之后都会被重置
 *8. 多空格问题已解决，修改了cutLine，让cut之后的数组保留了多余的空格
 *
 *
 *
 *Note:
 *diagDetail: print out all snippet lines with score at the end.
 *diagSimple: ignore similar neighbor lines with '~~', with score.
 *
 *
 *
 *06/26
 *1. 修复了\S+前有多空格的情况
 *2. 需要引入log参数，对不同log的处理中，前缀正则表达式是不同的
 *目前支持：
 *	gipcd
 *	alert
 *
 *
 *3. 第二次addTmp gipc的时候会卡很久。已解决，缩短了对于quote的正则表达式
 *
 *
 *06/27
 *1. 修复了有空格元素造成长度判定不准确的情况
 *2. 修复了非封闭括号导致判定不准确的情况
 *3. 存在问题，某些模板无法匹配，有一个模板无法处理多空格
 *4. 需要一个更好的方法来处理quote情况，目前的方法太简单，有些情况无法覆盖。比如已空格开始
 *
 *06/28
 *1. 采用了更好的cutLine方法，运用了递归处理
 *2. 处理了word以加号开头的情况，但是无法处理内部有加号的情况
 *3. 准备支持 alert gipcd ocssd crsd crsd_oraagent_crsusr crsd_orarootagent_root ohasd_oraagent_crsusr ohasd_orarootagent_root 
 *
 *4. 修复了ocssd带来的不同长度的多空格问题，$,*,+ 都被替换为.  可能有风险
 *
 *06/29
 *1. filterLog运行总时间为 5.8us * num_temp * num_lines
 *
 *06/30
 *1. 不处理第一个word，因为第一个word通常是独特的。
 *2. 更好的处理了括号没有关闭的情况
 *3. 反复 addTmp 会增加一些template, //S+ 会变成 //s*\//S+
 *
 *07/02
 *1. 解决了多//s*的问题
 *2. 
 *clssgmMemberPublicInfo: group \S+ member \S+ not found
clssgmMemberPublicInfo: group \S+ not found
clssgmMemberPublicInfo: group ocr_rwsba-cluster member \S+ not found
clssgmMemberPublicInfo: group ocr_rwsba-cluster not found

这里有问题， 会多几个temp。log本身的特性决定，log量不足 已解决，加入了更多的special word判定。可以考虑直接用sepcial word来提取模板

07/03
1. 实现了更快速的过滤和打分，采用map储存component关键字
2. 决解了extrTmp里面导致的多空格问题
3. 明天可以用过滤的方法来测试所有的日志

4. 对于comp末尾没有冒号的line不好处理
5. 对于gipchaLowerSend： 处理有问题
6. 需要更好的优化snippet.isMatched(),尝试使用第一个词 shixian 

7. gipcdFreeDeadClientInfo 有问题

07/04
1. ocssd 有很多无用行和奇怪的行，如何处理 (非字母开头的行)

 */


import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.io.*;
import java.util.*;
import java.util.regex.*;
//import java.text.SimpleDateFormat;

public class extract {

	String templatesFile = "";
	Judge judge;
	String scoreFile;
	String logType;
	List<String> fileNameMap;
	Map<String, Integer> map = new HashMap<String, Integer>();		//to store regex scores
	
	public extract(String file, String scoreFile, String logType){
		this.templatesFile = file;
		this.scoreFile = scoreFile;
		this.logType = logType;
		this.fileNameMap = readFile(".//fileNameMap.txt");
		judge = new Judge(scoreFile, ".//log//scoreOutput.txt");
		
		//store regex scores in map
		map.put(".*", 4);
		map.put("\\s*\\S*", 3);
		map.put("\\s*\\S+", 2);
		map.put("\\S*", 2);
		map.put("\\S+", 1);
		map.put("\".*\"", 0);
		map.put("'.*'", 0);
		map.put("\\[.*\\]", 0);
		map.put("\\(.*\\)", 0);
		map.put("\\{.*\\}", 0);
		/////////////////////////
		
	}
	
	
	public extract(){
		//for test
	}
	
	
	private String getMutipleSpace (String word){
		String ret = "";
		for (int i = 0; i < word.length(); i ++) {
			if (word.charAt(i) == ' ')
				ret += " ";
			else
				break;
		}
		return ret;
	}
	
	private String getListSpace (String[] list){
		String pre = "";
		String cur = "";
		for (String l : list) {
			cur = getMutipleSpace(l);
			if (!pre.equals(cur)){
				return "\\s*";
			}
			pre = cur;
		}
		return cur;
	}
	
	private boolean isSpecialWordAll(String[] wordList) {
		for (String s : wordList) {
			if (!isSpecialWord(s))
				return false;
		}
		return true;
	}
	
	private boolean containsSpace (String[] wordList) {
		for (String s : wordList) {
			//if (s.equals(" "))
			if (Pattern.matches("^\\s+$", s))
				return true;
		}
		return false;
	}
	
	/*
	 * Special word like ASB-12 AB:12 
	 */
	public boolean isSpecialWord(String word) {
		if (!isRegex(word)) {
			if ( Pattern.matches("^([a-z]+:[0-9]+,?)+", word) 
					|| Pattern.matches("^[A-Z]+", word)
					|| Pattern.matches("^[0-9:]+(clientID)?", word)
					|| Pattern.matches("^[A-Za-z_\\-]+\\.([A-Za-z_\\-]+\\.)+[A-Za-z_\\-0-9]+,?", word)
					|| (word.contains("_") && word.charAt(0) != ' ')
					//|| chlist[0][i] == "*"
					|| Pattern.matches("^eth[0-9]+", word)
					|| Pattern.matches("^[0-9:]+Pid", word)
					|| Pattern.matches("^\\.?[A-Z0-9]+[A-Z0-9\\-_\\.#]+,?:?", word)					//处理CLSN.AQPROC.MASTER,
					|| Pattern.matches("^([a-zA-Z_]+[:=])?(0x)?[0-9a-f]+,?:?", word)						//处理十六进制数
					|| Pattern.matches("^[0-9a-z][a-z0-9_]+-([0-9a-z][0-9a-z_]+-)+[0-9a-z][0-9a-z_]+[:,]?", word)
					|| (word.length() > 1 && Pattern.matches("^[0-9\\.,:]+", word))							//处理类似于ip地址的词语
					|| Pattern.matches("[0-9a-zA-Z\\.\\-]+![0-9a-zA-Z\\.\\-]+:?", word)						//处理c4.5!ORDERk7.MESSAGEt63.CRS-6016:
					|| Pattern.matches("\\S*ora\\.([A-Za-z0-9_\\.]+\\.)?[A-Za-z0-9_]+\\S*", word)			//处理带有resource的词汇
					|| Pattern.matches("\\.ASM[0-9]+:\\.ASM:\\S+", word)						//.ASM4:.ASM:rwsba-ext
					|| Pattern.matches("\\.APX[0-9]+:\\.APX:\\S+", word)
					|| Pattern.matches("\\S+:orcl:\\S+", word)												//orcl4:orcl:rwsba-cluster
					|| Pattern.matches("[0-9\\.]+\\-[0-9\\.]+el[0-9]+uek\\.\\S+", word)
					|| Pattern.matches("[A-Za-z0-9\\-]+:[A-Za-z0-9\\-]+:[A-Za-z0-9\\-]+", word)				//TESTDB3:TESTDB:rwsad-cluster
					|| (word.length() > 3 && word.substring(0, 3).equals("rws"))) {
				return true;
			}
		}
		return false;
	}
	
	private boolean isRegex (String word) {
		if (word.contains(".*") || word.contains("\\S"))
			return true;
		return false;
	}
	
	private boolean containsRegex (String[] wordCol) {
		for (int i = 0; i < wordCol.length; i ++) {
			if (isRegex(wordCol[i]))
				return true;
		}
		return false;
	}
	
	private boolean isQuote (String word) {
		if (word.contains("\".*\""))
			return true;
		else if (word.contains("'.*'"))
			return true;
		else if (word.contains("\\[.*\\]"))
			return true;
		else if (word.contains("\\(.*\\)"))
			return true;
		else if (word.contains("\\{.*\\}"))
			return true;
		else 
			return false;
	}
	
	private boolean containsQuote (String[] wordCol) {
		for (String s : wordCol) {
			if (isQuote(s))
				return true;
		}
		return false;
	}
	
	private boolean isQuoteAll (String[] wordCol) {
		for (String s : wordCol) {
			if (!isQuote(s))
				return false;
		}
		return true;
	}
	
	private String getQuote (String word) {
		if (word.contains("\".*\""))
			return "\".*\"";
		else if (word.contains("'.*'"))
			return "'.*'";
		else if (word.contains("\\[.*\\]"))
			return "\\[.*\\]";
		else if (word.contains("\\(.*\\)"))
			return "\\(.*\\)";
		else if (word.contains("\\{.*\\}"))
			return "\\{.*\\}";
		else 
			return word;
	}
	
	private String getLargestRegex (String[] wordCol) {
		int max = -1;
		String maxS = "";
		for (String s : wordCol) {
			if (isRegex(s)) {
//				System.out.println(getQuote(s));
				if (map.containsKey(s)) {
					if (map.get(s.trim()) > max) {
						if (max == 0)
							return ".*";
						max = map.get(s.trim());
						maxS = s;
					}
					else if (map.get(s.trim()) < max && map.get(s.trim()) == 0)
						return ".*";
				}
				else
					return ".*";
			}
		}
		if (max == 2) {
			return "\\s*\\S*";
		}
		else if (max == 1) {
			return getListSpace(wordCol) + "\\S+";
		}
		return maxS;
	}
	
	private boolean isEqual (String[] wordCol) {
		String pre = wordCol[0];
		for (int i = 1; i < wordCol.length; i ++) {
			if (!pre.equals(wordCol[i]))
				return false;
		}
		return true;
	}
	
	
	private String replaceQuote (String word) {
		StringBuffer sb = new StringBuffer(word);
		for (int i = 0; i < word.length(); i ++ ) {
			char c = sb.charAt(i);
			char pre = ' ';
			if (i > 0)
				pre = sb.charAt(i - 1);
			if ((c == ')' || c == '}' || c == ']') && pre != '\\') {
				sb.setCharAt(i, '.');
			}
		}
		return sb.toString();
	}
	
	//given similar lines, return template
	private String extrTmp(List<String> list){
		String[][] chlist = new String[list.size()][cutLine(list.get(0)).length];
		StringBuffer result = new StringBuffer();
		String[] wordCollect = new String[list.size()];			//储存同一列的不同行元素
		String[] ret = new String[chlist[0].length];
		for (int i = 0; i < list.size(); i ++){
			chlist[i] = cutLine(list.get(i));
		}
		
		
		ret[0] = chlist[0][0];	//compareLine中第一个word必须相等，所以这里直接输出第一个word
		//变量声明和初始化
		///////////////////////////////////////
		
		//处理第一个word是res的情况
		ret[0] = Snippet.compCorrect(ret[0]);
		
//		if (chlist[0].length == 1)
//			return chlist[0][0];
		
		
		
		
		for (int i = 1; i < chlist[0].length; i ++){
			//对每个word进行处理
			
			// 初始化wordCollect
			for (int j = 0; j < list.size(); j ++) {
				wordCollect[j] = chlist[j][i];
			}
			
			//普通情况，判定是否有不相等
			if (isEqual (wordCollect) && !isSpecialWordAll(wordCollect)) {
				ret[i] = chlist[0][i];		//都相等，直接赋值
				
				ret[i] = replaceQuote(ret[i]);
				
				
			}
			else if (!containsRegex(wordCollect) && !isSpecialWordAll(wordCollect)){		//不包含正则表达式的情况，需要处理空格开头或者空格元素
				if (containsSpace(wordCollect))
					ret[i] = getListSpace(wordCollect) + "\\S*";
				else
					ret[i] = getListSpace(wordCollect) + "\\S+";
			}
			else if (isSpecialWordAll(wordCollect)) {					//所有元素都是specialword的情况
				ret[i] = "\\S+";
			}
			else if (containsRegex(wordCollect)) {						//包括所有\S \s quote .*的情况
				if (containsQuote(wordCollect))							//包含 quote，但是元素不相等
					ret[i] = ".*";
				else {
					try {
						ret[i] = getLargestRegex(wordCollect);				//不包含quote，只包含其他regex
					}
					catch(NullPointerException e){
						e.printStackTrace();
						System.exit(-1);
			        } 
					
				}
			}
			
		}
		
		
		///判断结束
		//数组转String
		
		for (int i = 0; i < ret.length - 1; i ++){
			result.append(ret[i]);
			result.append(' ');				//can't handle multiple spaces
			//result.append("\\s*");
		}
		result.append(ret[ret.length - 1]);	
		
		return result.toString();
	}
	
	//return similarity between lines, use target to control
	private boolean compareLines(String a, String b){
		String[] cha = cutLine(a);
		String[] chb = cutLine(b);
		int key = 0, len = Math.min(cha.length, chb.length);
		
		double target = 0.5; 		//control similarity, bigger = more strict
									//target 太小的时候使模板很短（？）
		if (cha.length != chb.length)
			return false;
		
		
		if (len == 1) {
			if (Pattern.matches("^[A-Z_]+=.+", cha[0]) && Pattern.matches("^[A-Z_]+=.+", chb[0])) {
				if (cha[0].split("=")[0].equals(chb[0].split("=")[0])) {
					return true;
				}
			}
		}
		
		
		if (!cha[0].equals(chb[0]))
			return false;					//第一个word必须相等
		for (int i = 0; i < len; i ++){
			if (cha[i].equals(chb[i]))
				key ++;
		}
		//System.out.println(len);
		if ((double)key / (double)len >= target)
			return true;
		else 
			return false;
	}
	
	//Store all lines as ArrayList loglist
	public static List<String> readFile(String file){
		List<String> loglist = new ArrayList<String>();
		
		try{
			FileReader reader = new FileReader(file);
			BufferedReader br = new BufferedReader(reader);
			//read file
			
            String str = null;
            while((str = br.readLine()) != null) {
            	loglist.add(str);
            	//System.out.println(str);
            }
            br.close();
			reader.close();
		}
		catch(FileNotFoundException e){
			e.printStackTrace();
			System.exit(-1);
        }
		catch(IOException e) {
            e.printStackTrace();
            System.exit(-1);
		}
		return loglist;
	}
	
	public static void writeFile(String file, List<String> content){
		try{
			File f = new File(file);
			FileWriter fos = new FileWriter(f);  
			BufferedWriter bw = new BufferedWriter(fos);
			
			for (String s : content){
				//System.out.println(s);
				bw.write(s);
				bw.newLine();
				bw.flush();
			}
			bw.close();
		}
		catch(FileNotFoundException e){
			e.printStackTrace();
			System.exit(-1);
        }
		catch(IOException e) {
            e.printStackTrace();
            System.exit(-1);
		}

	}
	
	//partition lines according to similarity,
	//and do extrTmp for every part
	public List<String> partition(List<String> loglist){
		//List<String> loglist = readFile("C://Users//yuhanli//workspace//logAna//log//alertcon2.log")
		if (loglist.size() == 0)
			return new ArrayList<String>();
		List<String> tmpCollection = new ArrayList<String>();
		List<String> sip = new ArrayList<String>();
		
		for (int i = 1; i < loglist.size(); i ++){
			if (Snippet.isStrangeLine(loglist.get(i - 1)))
				continue;
			sip.add(loglist.get(i - 1));
			if (!compareLines(loglist.get(i - 1), loglist.get(i))){
//				System.out.println(loglist.get(i));
//				System.out.println(loglist.get(i - 1));
				tmpCollection.add(extrTmp(sip));
				sip = new ArrayList<String>();
			}
		}
		if (!Snippet.isStrangeLine(loglist.get(loglist.size() - 1)))
			sip.add(loglist.get(loglist.size() - 1));
		tmpCollection.add(extrTmp(sip));
		return tmpCollection;
	}
	
	/*
	 * 6/25:
	 * 之前addTmp将新log与旧的template合在一起，然后做partition，这样很难识别出哪些是新加入的template
	 * 准备用新方法，让addTmp返回新加入的template 
	 *
	 */
	
	private boolean isDotStar(String input) {
		if (input.equals(".*")) return true;
		else return false;
	}
	
	private List<String> doublePartition (List<String> templates){
		
		Comparator<String> comparator = new Comparator<String>() {
			public int compare(String s1, String s2) {
				for (int i = 0; i < Math.min(s1.length(), s2.length()); i ++) {
					char c1 = s1.charAt(i);
					char c2 = s2.charAt(i);
					if (c1 == '\\' && c2 != '\\')  return -1;
					else if (c1 != '\\' && c2 == '\\') return 1;
					else if (c1 > c2) return 1;
					else if (c1 < c2) return -1;
				}
				if (s1.length() > s2.length()) return -1;
				else if (s1.length() < s2.length()) return 1;
				else return 0;
			}
		};
		
		Comparator<String> comparatorEnd = new Comparator<String>() {
			public int compare(String s1, String s2) {
				for (int i = 0; i < Math.min(s1.length(), s2.length()); i ++) {
					char c1 = s1.charAt(i);
					char c2 = s2.charAt(i);
//					if (s1.length() > i + 1 && isDotStar(s1.substring(i, i + 2)) && !(s2.length() > i + 1 && isDotStar(s2.substring(i, i + 2)))) {
//						return -1;
//					}
//					else if (!(s1.length() > i + 1 && isDotStar(s1.substring(i, i + 2))) && !(s2.length() > i + 1 && isDotStar(s2.substring(i, i + 2)))) {
//						return 1;
//					}
					if (i == Math.min(s1.length(), s2.length()) - 2){
						if (isDotStar(s1.substring(s1.length() - 2, s1.length())) && !isDotStar(s2.substring(s2.length() - 2, s2.length()))) return -1;
						else if (!isDotStar(s1.substring(s1.length() - 2, s1.length())) && isDotStar(s2.substring(s2.length() - 2, s2.length()))) return 1;
					}
					else if (c1 == '\\' && c2 != '\\')  return -1;
					else if (c1 != '\\' && c2 == '\\') return 1;
					else if (c1 > c2) return 1;
					else if (c1 < c2) return -1;
					
					
				}
				if (s1.length() > s2.length()) return -1;
				else if (s1.length() < s2.length()) return 1;
				else return 0;
			}
		};
		
		
		Collections.sort(templates, comparator);
		templates = partition(templates);
		Collections.sort(templates, comparator);
		templates = partition(templates);
		Collections.sort(templates, comparator);
		Collections.sort(templates, comparatorEnd);
		return templates;
		
	}
	
	public List<String> addTmp(List<String> newLine) {
		List<String> template = readFile(templatesFile);
		
		//Do partition only for filtered new line (potential new template)
		List<String> newTemplate = doublePartition(newLine);
		template.addAll(newTemplate);
		newTemplate = doublePartition(template);
		
		///////////////////////////////////////////////////////
		//newTemplate is the updated templates collection.
		//Keep scores for templates.
		Map<String, Integer> jo = judge.getScore(); // read from socreFile before updating
		List<String> scoreList = new ArrayList<String>(); //used to store log lines end with score, will be written to scoreFile.
		
		boolean f = true;

		
		System.out.println("newTemplate size:  " + newTemplate.size());
		for (int i = 0; i < newTemplate.size(); i ++){
			String templateI = newTemplate.get(i);
			f = true;
//			Iterator OldTemplates = jo.keys();
			for (String OldTmp : jo.keySet()) {
				if (templateI.equals(OldTmp)){
					try{
						scoreList.add(templateI + "--" + jo.get(OldTmp));
						f = false; 
						break;
					}
					catch (Exception e1){
						e1.printStackTrace();
					}
				}
			}
			if (f){
				scoreList.add(templateI);
				//scoreList.add(templateI + "--" + 0);
			}
		}
		
		//
		//Ends here
		/////////////////////////////////////////////////
		
		
		newTemplate = finalClean(newTemplate);		//清除掉长度为一且全大写的行,这里没有处理scorelog
		
		

		writeFile(templatesFile, newTemplate);		//最后还是会写入templatesFile
		writeFile(scoreFile, scoreList);		
		return null;
	}
	
	
	private List<String> finalClean(List<String> templates){
		List<String> ret = new LinkedList<String>();
		for (String line : templates) {
			if (!Pattern.matches("^[A-Z_0-9]+( [^0-9a-zA-Z]+)?", line) && 
					(!Pattern.matches("^SQL>.*", line)) && 
					!Pattern.matches("^SUCCESS: .*", line) &&
					!Pattern.matches("^ALTER .*", line) &&
					!Pattern.matches("^AFDLIB .*", line) &&
					!Pattern.matches("^CREATE DISKGROUP", line) &&
					!Pattern.matches("^Release:.*", line) &&
					!Pattern.matches("^SITE site.*", line) &&
					!Pattern.matches("^Version:.*", line) &&
					!Pattern.matches("^path:\\S+", line) &&
					!Pattern.matches("^path:Unknown disk", line)
					){
				
				if (Pattern.matches("^ORA-[0-9]+:? [^0-9a-zA-Z]+", line)) {
					ret.add(line.split(" ")[0] + " .*");
				}
				else{
					ret.add(line);
				}
			}
		}
		return ret;
	}
	
	public List<String> addTmp(String file){
		List<String> newLine = readFile(file);
		return addTmp(newLine);
	}
	
	
	private List<String> cutLineBySpace (String line) {
		StringBuilder sb = new StringBuilder(line);
		//sb.setCharAt(0, '.');
		line = sb.toString();
		List<String> s = new ArrayList<String>();
		s.add("");
		if (line.isEmpty())
			return s;
		List<String> list = new ArrayList<String>();
		int head = 0;
		for (int i = 0; i < line.length(); i ++) {
			char ch = line.charAt(i);
			if (ch == ' '){
				if (i > head && !line.substring(head, i).equals(" ")) {
					list.add(line.substring(head, i));
					head = i + 1;
				}
			}
		}
		if (head < line.length()){
			list.add(line.substring(head, line.length()));
		}
		return list;
	}
	
	
	
	public String[] cutLine(String line){
		String[] s = new String[1];
		s[0] = "";
		if (line.isEmpty())
			return s;
		
		List<String> list = new ArrayList<String>();
		int head = 0;
		//char spliter = ' ';
		Stack<Character> stack = new Stack<Character>();
		
		//For head and tail of quote string
		String prefix = "";
		String suffix = "";
		int prefix_loc = 0;
		int suffix_loc = 0;
		//
		
		
		
		//Don't deal with the first word, for map
//		String firstWord = "";
//		for (int i = 0; i < line.length(); i ++) {
//			if (line.charAt(i) == ' ') {
//				firstWord = line.substring(0, i);
//				while (i < line.length() && line.charAt(i) == ' '){
//					i ++;
//				}
//				head = i;
//				break;
//			}
//		}
//		list.add(firstWord);
		// end
		
		
		for (int i = head; i < line.length(); i ++){
			char ch = line.charAt(i);
			
			if (!stack.isEmpty() && stack.peek() == ch){
				stack.pop();
				if (stack.isEmpty()){
					
					//Set prefix only when quote end.
					prefix = prefix_loc > head ? line.substring(head, prefix_loc) : "";
					if (prefix.length() > 0)
						prefix = prefix.charAt(prefix.length() - 1) == '\\' ? prefix.substring(0, prefix.length() - 1) : prefix;						//处理多次partition之后导致\增加的问题 
					
//					if (i + 1 < line.length() && line.charAt(i + 1) != ' ' ){
					suffix_loc = i + 1;
//					}

					while (i + 1 < line.length() && line.charAt(i + 1) != ' '){
						i ++;
					}
					head = i + 2;
					
					suffix = line.substring(suffix_loc, head - 1);
//					System.out.println("suffix " + suffix);
//					System.out.println("i      " + i);
					//list.add(line.substring(head, i + 1));
					//list.add("*");			//需要把不同的quote分开
					if (ch == '"')
						list.add(cutLine(prefix)[0] + "\".*\"" + cutLine(suffix)[0]);
					else if (ch == '\'')
						list.add(cutLine(prefix)[0] + "'.*'" + cutLine(suffix)[0]);
					else if (ch == ']')
						list.add(cutLine(prefix)[0] + "\\[.*\\]" + cutLine(suffix)[0]);
					else if (ch == ')')
						list.add(cutLine(prefix)[0] + "\\(.*\\)" + cutLine(suffix)[0]);
					else if (ch == '}')
						list.add(cutLine(prefix)[0] + "\\{.*\\}" + cutLine(suffix)[0]);
					
//					char nextChar = line.charAt(i + 1);
					
				}
				
			}
			else if (ch == '"'){
				if (stack.isEmpty())
					prefix_loc = i;
				stack.push('"');
			}
			else if (ch == '\''){
				if (stack.isEmpty())
					prefix_loc = i;
				stack.push(ch);
			}
			else if (ch == '['){
				if (stack.isEmpty())
					prefix_loc = i;
				stack.push(']');
			}
			else if (ch == '('){
				if (stack.isEmpty())
					prefix_loc = i;
				stack.push(')');
			}
			else if (ch == '{'){
				if (stack.isEmpty())
					prefix_loc = i;
				stack.push('}');
			}
			
			else if (ch == ' ' && stack.isEmpty()){
				if (i > head && !Pattern.matches("\\s+", line.substring(head, i))){
					if (!line.contains("\\S+"))
						while (line.contains("+")) {
							StringBuilder sb = new StringBuilder(line);
							sb.setCharAt(line.indexOf('+'), '.');
							line = sb.toString();
						}
					if (!line.contains(".*") && !line.contains("\\S*") && !line.contains("\\s*"))
						while (line.contains("*")) {
							StringBuilder sb = new StringBuilder(line);
							sb.setCharAt(line.indexOf('*'), '.');
							line = sb.toString();
						}
					while (line.contains("$")) {
						StringBuilder sb = new StringBuilder(line);
						sb.setCharAt(line.indexOf('$'), '.');
						line = sb.toString();
					}
					while (line.contains("?")) {
						StringBuilder sb = new StringBuilder(line);
						sb.setCharAt(line.indexOf('?'), '.');
						line = sb.toString();
					}
					while (line.contains("^")) {
						StringBuilder sb = new StringBuilder(line);
						sb.setCharAt(line.indexOf('^'), '.');
						line = sb.toString();
					}
					while (line.contains("|")) {
						StringBuilder sb = new StringBuilder(line);
						sb.setCharAt(line.indexOf('|'), '.');
						line = sb.toString();
					}
					list.add(line.substring(head, i));
					head = i + 1;
				}

			}
		}
		
		if (line.length() > head){
			if (stack.isEmpty()) {
				if (!line.contains("\\S+"))
					while (line.contains("+")) {
						StringBuilder sb = new StringBuilder(line);
						sb.setCharAt(line.indexOf('+'), '.');
						line = sb.toString();
					}
				if (!line.contains(".*") && !line.contains("\\S*") && !line.contains("\\s*"))
					while (line.contains("*")) {
						StringBuilder sb = new StringBuilder(line);
						sb.setCharAt(line.indexOf('*'), '.');
						line = sb.toString();
					}
				while (line.contains("$")) {
					StringBuilder sb = new StringBuilder(line);
					sb.setCharAt(line.indexOf('$'), '.');
					line = sb.toString();
					
				}
				while (line.contains("^")) {
					StringBuilder sb = new StringBuilder(line);
					sb.setCharAt(line.indexOf('^'), '.');
					line = sb.toString();
				}
				while (line.contains("|")) {
					StringBuilder sb = new StringBuilder(line);
					sb.setCharAt(line.indexOf('|'), '.');
					line = sb.toString();
				}
				list.add(line.substring(head, line.length()));
			}
//				list.add(line.substring(head, line.length()));
			else {
				list.add(line.substring(head, prefix_loc) + ".*");
	//			list.addAll(cutLineBySpace(line.substring(prefix_loc + 1, line.length())));											//处理没有饭括号收尾的情况，有一定风险！！
			
			}
		}
		String[] ret = new String[list.size()];
		for (int i = 0; i < list.size(); i ++){
			ret[i] = list.get(i);
			
		}
		
//		if (line.equals("Cluster Synchronization Service daemon ('.*'|\".*\"|\\(.*\\)|\\[.*\\]|\\{.*\\})\\S* \\S+ not scheduled for \\S+ msecs.")){
//			for (String s : ret){
//				System.out.println(s);
//			}
//		}
		
		return ret;
	}
	
	public List<String> removeDupList(List<String> logList) {
		Set<String> hashset = new HashSet<String>();
		List<String> ret = new LinkedList<String>();
		for (String line : logList) {
			if (!line.isEmpty() && !hashset.contains(line) && Character.isLetter(line.charAt(0)) && !Snippet.isStrangeLine(line)){
				hashset.add(line);
				ret.add(line);
			}
		}
		
		
		return new ArrayList<String>(ret);
	}
	
	
//	public List<String> diagDetail(String logFile, String from, String to){
//		
//		//Snippet.getSnippet(logFile, from, to);
//		
//		return Snippet.scoreLog(judge.getScore(), Snippet.getSnippet(logFile, from, to), logType, templatesFile);
//	}
//	
//	public List<String> diagScore(String logFile, String from, String to){
//		//Snippet.getSnippet(logFile, from, to);
//		
//		return Snippet.scoreLog(judge.getScore(), Snippet.getSnippet(logFile, from, to), logType, templatesFile);
//	}
	
	public List<String> diagScore(String logFile, String from, String to, Set<Integer> scoreSet) {
		//Snippet.getSnippet(logFile, from, to);
		return Snippet.scoreLog(judge.getScore(), Snippet.getSnippet(logFile, from, to), logType, templatesFile, scoreSet);
	}
	
	public List<String> diagScore(String[] logFiles, String from, String to, Set<Integer> scoreSet) {
		//Snippet.getSnippet(logFile, from, to);
		return Snippet.scoreLog(judge.getScore(), Snippet.getSnippet(logFiles, from, to), logType, templatesFile, scoreSet);
	}
	
	public List<String> filterLog (String logFile, String from, String to){
		//Snippet.getSnippet(logFile, from, to);
		
		return Snippet.filter(templatesFile, Snippet.getSnippet(logFile, from, to), logType);
	}
	
	public List<String> diagCluster(String[] node, String logType, String from, String to, Set<Integer> scoreSet, String index) {
		EsClient ec = new EsClient();
		List<List<String>> snippet;
		try {
			snippet = ec.getSnippet(node, logType, from, to, "rws00fxw-cluster", "rws00fxw.us.oracle.com", 9300, index);
			
			List<String> ret = Snippet.scoreLog(judge.getScore(), snippet.get(0), logType, templatesFile, scoreSet, snippet.get(1));
//			Collections.sort(ret);
			return ret;
			//return Snippet.scoreLog(judge.getScore(), snippet.get(1), logType, templatesFile, scoreSet);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		
	}
	
	
	
	public List<String> diagSimple(String[] logFiles, String from, String to, Set<Integer> scoreSet){
		return simplify(diagScore(logFiles, from, to, scoreSet));
		
	}
	
	public List<String> simplify (List<String> loglist) {
		List<String> sip = new ArrayList<String>();
		int preI = 0;
		
		for (int i = 1; i < loglist.size(); i ++){
			if (!compareLines(loglist.get(i - 1), loglist.get(i))){
//				System.out.println(loglist.get(i));
//				System.out.println(loglist.get(i - 1));
				sip.add(loglist.get(i - 1));
				if (preI < i - 1){
					sip.add("~~ " + (i - preI - 1));
//					sip.add(loglist.get(i - 1));
				}
				preI = i;
			}
		}
		if (loglist.size() > 0) {
			sip.add(loglist.get(loglist.size() - 1));
			if (loglist.size() - 1 - preI > 0)
				sip.add("~~ " + (loglist.size() - 1 - preI));
		}
		return sip;
	}
	
	public Map<String, Integer> GetAppearance (String[] node, String logType, String from, String to, String index) {
		EsClient ec = new EsClient();
		List<List<String>> snippet;
		try {
			snippet = ec.getSnippet(node, logType, from, to, "rws00fxw-cluster", "rws00fxw.us.oracle.com", 9300, index);
			
			Map<String, Integer> ret = Snippet.getAppearance(judge.getScore(), snippet.get(0), logType, templatesFile);
//			Collections.sort(ret);
			return ret;
			//return Snippet.scoreLog(judge.getScore(), snippet.get(1), logType, templatesFile, scoreSet);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
	
	
	//输出矩阵,多个文件中每个template出现的次数.
	public Map<String, List<Integer>> GetAppearance (String[] node, String logType, String[] path, String index) {	
		EsClient ec = new EsClient();
		Map<String, List<Integer>> ret = new HashMap<String, List<Integer>>();
		List<String> templates = readFile(templatesFile);
		for (String t : templates) {
			ret.put(t, new LinkedList<Integer>());
		}
		
		
		List<List<String>> snippet;
		for (int i = 0; i < path.length; i ++) {
			String p = path[i];
			try {
				snippet = ec.getSnippet(node, logType, p, "rws00fxw-cluster", "rws00fxw.us.oracle.com", 9300, index);
				
				Map<String, Integer> map = Snippet.getAppearance(judge.getScore(), snippet.get(0), logType, templatesFile);
				
//				for (String key : map.keySet()) {
//					List<Integer> l = ret.get(key);
//					l.add(map.get(key));
//					ret.put(key, l);
//				}
				for (String key : ret.keySet()) {
					List<Integer> l = ret.get(key);
					if (map.containsKey(key)) {
						l.add(map.get(key));
					}
					else {
						l.add(0);
					}
					ret.put(key, l);
				}
				
				
	//			Collections.sort(ret);
				//return Snippet.scoreLog(judge.getScore(), snippet.get(1), logType, templatesFile, scoreSet);
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return ret;
	}
	
	public int[][][] getMatforEveryFile (String[] node, String logType, String index) {
		EsClient ec = new EsClient();
		int tmpLen = extract.readFile(templatesFile).size();
		int[][][] ret = new int[216][tmpLen][tmpLen];
		List<List<String>> snippet;
		
		for (int i = 1; i <= 216; i ++) {
			try {
				snippet = ec.getSnippet(node, logType, Integer.toString(i), "rws00fxw-cluster", "rws00fxw.us.oracle.com", 9300, index);
				ret[i - 1] = Snippet.getMatArray(judge.getTmpNum(), judge.getScore(), snippet.get(0), logType, templatesFile);
			}
			catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			}
		}
		return ret;
	}
	
	public void replaceRawAsTmp (String[] node, String logType, String index) {
		EsClient ec = new EsClient();
		List<List<String>> snippet;
		
		java.util.Comparator<String> comparator = new java.util.Comparator<String>() {
			public int compare(String s1, String s2) {
				int a = Integer.parseInt(s1.split("\\.")[2]);
				int b = Integer.parseInt(s2.split("\\.")[2]);
				if (a > b) 
					return 1;
				else if (a < b)
					return -1;
				else 
					return 0;
			}
		};
		Collections.sort(this.fileNameMap, comparator);
		
		for (int i = 1; i <= 216; i ++) {
			try {
				System.out.println("===== " + Integer.toString(i) + " =====");
				//String path = this.fileNameMap.get(i - 1).split("\\.")[1] + "." + this.fileNameMap.get(i - 1).split("\\.")[2];
				String path = "";
				if (this.fileNameMap.get(i - 1).split("\\.")[1].equals("log"))
					path = Integer.toString(i);
				else 
					path = ".*" + Integer.toString(i);
				
//				if (path.equals("")) {
//					throw new java.lang.RuntimeException("Path: " + Integer.toString(i) + " don't have related file name in ./fileName.txt");
//				}
				System.out.println("File name is: " + path);
				snippet = ec.getSnippet(node, logType, path, "rws00fxw-cluster", "rws00fxw.us.oracle.com", 9300, index);
				writeFile(".//templatedLogs//" + Integer.toString(i), Snippet.getGeneralList(judge.getScore(), snippet.get(0), logType, templatesFile));
			}
			catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
//	public static void writeJSON(String file, JSONObject jo){
//		List<String> input = new ArrayList<String>();
//		input.add(jo.toString());
//		writeFile(file, input);
//	}
	
//	public static JSONObject readJSON(String file){
//		List<String> read = readFile(file);
//		//System.out.println(read.get(0));
//		JSONObject jo = new JSONObject();
//		try{
//			jo = new JSONObject(read.get(0));
//		}
//		catch (Exception e){
//			e.printStackTrace();
//		}
//		
//		return jo;
//	}
	

	
//	public static void main(String[] args) throws Exception{

		
		
		
		
		
		
		
		
//		String[] s = JsonConvert.createPostSearchParam("http://10.208.149.207:9200", "test-*", "snippet", "2017-07-10T01:53:41.057Z", "2017-07-10T02:53:41.057Z", "gipc", "rws00fxu", "100", "1m");

//		List<String> l = JsonConvert.getAllLines("http://10.208.149.207:9200", "test-*", "snippet", "2017-07-10T01:53:41.057Z", "2017-07-10T05:53:41.057Z", "gipc", "rws00fxu", "1000", "1m");
//		for (String n : l) {
//			System.out.println(n);
//		}
//		System.out.println(l.size());
		//args1: template file, stores all template regx.
		//args2: score file, stores all template regx with "--score_number" at the end.
		// score file and template file are always sync. Manually score logs in score file and they can be keep in addTmp.
//		extract e = new extract(".//log//ocssdTemplate.txt", ".//log//ocssdTemplateScore.txt", "ocssd");		//it's template file location, remember to back up it before addTmp.
		
		// 		Uncomment it if you want to add more template according to cut log.
//		e.addTmp(".//log//ocssd_all2_rws1270361.trc");
//		Set<Integer> scoreSet = new HashSet<Integer>();
//		scoreSet.add(3);
//		List<String> list = e.diagSimple(
//				".//log//ocssd_3.trc", 
//				"2017-05-01 00:00:00", 
//				"2017-07-30 01:00:00"
//				,scoreSet
//				);
//		for (String n : list){
//			System.out.println(n);
//		}
//		System.out.println("====================================================");
//		System.out.println("Filtered Snippet size is " + list.size());
//		System.out.println("Templates number is " + readFile(e.templatesFile).size());
//		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
//		System.out.println(df.format(new Date()));// new Date()为获取当前系统时间
		
		
//		
//		Map<String, List<String>> map = Snippet.mapComponent(".//log//gipcTemplate.txt");
//		for (String s : map.keySet()) {
//			System.out.println("com:  " + s);
//			for (String a : map.get(s)) {
//				System.out.println(a);
//			}
//		}
//		System.out.println(map.containsKey("gipcdConfig_vir2phyID_setunit"));
//		System.out.println(map.keySet().size());
//		
//		System.out.println(Snippet.getLogComp("2017-07-02 19:57:27.092 :    AGFW:3775817472: {1:33271:2} ora.ASMNET2LSNR_ASM.lsnr:", "crsd"));
//		System.out.println(Snippet.getCompPrefix("ora.abc.dg:"));
//		System.out.println(Snippet.isMatched("kgfnConnect2Int: cstr=\\(.*\\)", 
//				"2017-07-10 21:44:57.974*:kgfn.c@6723: kgfnConnect2Int: cstr=(DESCRIPTION=(ADDRESS=(PROTOCOL=beq)(PROGRAM=/u01/app/12.2.0/grid/bin/oracle)(ARGV0=oracle+ASM1_ocr)(ENVS='ORACLE_HOME=/u01/app/12.2.0/grid,ORACLE_SID=+ASM1')(ARGS='(DESCRIPTION=(LOCAL=YES)(ADDRESS=(PROTOCOL=beq)))')(PRIVS=(USER=crsusr)(GROUP=oinstall)))(enable=setuser))", "crsd"));
//		System.out.println(Pattern.matches("ARSE\\(.*\\), \\S+ .\\)\\) ELEMENT\\(.*\\)^BDEFAULT_TEMPLATE=_STRING^BDEFAULT_TEMPLATE=_NOFLAGS^B^A] ", 
//				"ARSE(%NAME%, ., 2), %USR_ORA_DOMAIN%, .)) ELEMENT(INSTANCE_NAME= %GEN_USR_ORA_INST_NAME%)^BDEFAULT_TEMPLATE=_STRING^BDEFAULT_TEMPLATE=_NOFLAGS^B^A] "));
		
//		String[] a = new String[4];
//		a[0] = "\\S+"; a[1] = "\\s*\\S+"; a[2] = "\\S*"; a[3] = "\\S*";
//		System.out.println(e.getLargestRegex(a));
		/*
		 * Main func ends here
		 */
//		String[] cut = e.cutLine("rim hub timeout    30    grace period       30");
//		for (String s : cut)
//			System.out.println(s);		
//		List<String> l = new ArrayList<String>();
//		l.add("ora.abc.dg");
//		System.out.println(e.extrTmp(l));
//		String[] s = new String[2];
//		s[0] = "\\s*\\S*";
//		s[1] = " \\s*\\S+";
//		System.out.println(e.isSpecialWordAll(s));
		/////////////////\\S*('.*'|\".*\"|\\(.*\\)|\\[.*\\]|\\{.*\\})\\S*
//		String timestamp = 
//				"^[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}:[0-9]{2}.[0-9]{3}";
//		String component = " :\\s*[A-Z]+:[0-9]+\\s*:\\s*";
//		String forShortLine = "[ \\+]\\S+\\s*\\S+ ";		
//		System.out.println(Pattern.matches(
//				"^" + "clssgmCopyoutMemberInfo: IG.ASMSYS.BACKGROUND, id \\S+ gin \\S+ grp priv data \\S+ members \\S+ incarnation \\S+ updateseq\\(.*\\), msgsize \\S+",  
//				"clssgmCopyoutMemberInfo: IG+ASMSYS$BACKGROUND, id 13, gin 1 grp priv data 0, members 1, incarnation 1, updateseq(1), msgsize 972"));
//		System.out.println(Pattern.matches(".*" + ".*" + " to allocate \\S+ bytes of shared memory \\S+", 
//				"2017-06-19 21:21:12.910+ORA-04031: unable to allocate 56 bytes of shared memory (\"shared pool\",\"unknown object\",\"KKSSP^26\",\"kglseshtSegs\")"));
//		
//		
//		
		
//		try {
//			JSONObject templateDict = new JSONObject("{\"a\":\"b\"}");
//			templateDict.put("name", "Yuhan");
//			templateDict.put("sex", "male");
//			System.out.println(templateDict.toString());
//			extract.writeJSON(".//log//hellworld.json", templateDict);
//		}
//		catch (Exception e1){
//			e1.printStackTrace();
//		}
//		
//		
//		System.out.println(extract.readJSON(".//log//hellworld.json").toString());
//		
//		
//		Judge jdge = new Judge(".//log//templatesScore.txt", ".//log//score.json");
//		System.out.println(jdge.getScore().toString());
	
//	}

}
