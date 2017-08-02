package elk.elastic;


import java.util.*;
import java.util.regex.Pattern;



public class Judge {
	String templateFile;
	String scoreFile;
	public Judge(String templateFile, String scoreFile){
		this.templateFile = templateFile;
		this.scoreFile = scoreFile;
	}
	
	
	//get scores from templateFile
	//line--score
	public Map<String, Integer> getScore(){
		List<String> list = extract.readFile(templateFile);
		int score = -1;
		Map<String, Integer> jo = new HashMap<String, Integer>();
		for (String line : list){
//			if (line.length() > 1 && line.charAt(line.length() - 2) == '-' && line.charAt(line.length() - 3) == '-' && Character.isDigit(line.charAt(line.length() - 1))) {
			if (Pattern.matches("^.*--[0-9]+$", line)) {
//				String[] sp = line.split("--");
//				String content = "";
//				if (sp.length > 2) {		//deal with 'abc-----------0'
//					for (int i = 0; i < sp.length - 2; i ++){
//						content += sp[i] + "--";
//					}
//					content += sp[sp.length - 2];
//				}
//				else
//					content = sp[0];
//				score = Integer.valueOf(sp[sp.length - 1]);
				int tail = -2;
				for (int i = line.length() - 1; i >= 0; i --) {
					if (!Character.isDigit(line.charAt(i))) {
						tail = i - 1;
						break;
					}
				}
//				System.out.println(line);
				String content = line.substring(0, tail);
				score = Integer.valueOf(line.substring(tail + 2, line.length()));
				
					//score = Integer.valueOf(Character.toString(line.charAt(line.length() - 1)));
				try {
					jo.put(content, score);
				}
				catch (Exception e1){
					e1.printStackTrace();
				}
				
			}
			else {
				try {
					jo.put(line, 0);
				}
				catch (Exception e1){
					e1.printStackTrace();
				}
			}
		}
		return jo;
	}
}
