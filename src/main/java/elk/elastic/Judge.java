package elk.elastic;


import java.util.*;



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
			if (line.charAt(line.length() - 2) == '-' && line.charAt(line.length() - 3) == '-' && Character.isDigit(line.charAt(line.length() - 1))) {
				score = Integer.valueOf(Character.toString(line.charAt(line.length() - 1)));
				try {
					jo.put(line.substring(0, line.length() - 3), score);
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
