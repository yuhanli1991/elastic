package elk.elastic;


import java.io.FileNotFoundException;  
import java.io.IOException;  
import java.io.RandomAccessFile;  
import java.util.*;
/** 
 * http://bbs.csdn.net/topics/190181198 
 * 从最后一行开始读取 
 */  
public class FromEndRF {  
  
    /** 
     *  
     * @param filename 目标文件 
     * @param charset 目标文件的编码格式 
     */  
    public static List<String> read(String filename, String charset) {  
    	List<String> ret = new ArrayList<String>();
        RandomAccessFile rf = null;  
        try {  
            rf = new RandomAccessFile(filename, "r");  
            long len = rf.length();  
            long start = rf.getFilePointer();  
            long nextend = start + len - 1;  
            String line;  
            rf.seek(nextend);  
            int c = -1;  
            while (nextend > start) {  
                c = rf.read();  
                if (c == '\n' || c == '\r') {  
                    line = rf.readLine();  
                    if (line != null) {  
//                      System.out.println(new String(line  
//                                .getBytes("ISO-8859-1"), charset));  
                    	ret.add(new String(line.getBytes("ISO-8859-1"), charset));
                    
                    } 
                    else {  
//                        System.out.println(line);  
                    	ret.add(line);
                    }  
                    nextend--;  
                }  
                nextend--;  
                rf.seek(nextend);  
                if (nextend == 0) {// 当文件指针退至文件开始处，输出第一行  
                    // System.out.println(rf.readLine());  
//                    System.out.println(new String(rf.readLine().getBytes(  
//                            "ISO-8859-1"), charset));  
                	ret.add(new String(rf.readLine().getBytes(  
                            "ISO-8859-1"), charset));
                }  
            }  
        } catch (FileNotFoundException e) {  
            e.printStackTrace();  
        } catch (IOException e) {  
            e.printStackTrace();  
        } finally {  
            try {  
                if (rf != null)  
                    rf.close();  
            } catch (IOException e) {  
                e.printStackTrace();  
            }  
        }  
        return ret;
    }  
  
}  
