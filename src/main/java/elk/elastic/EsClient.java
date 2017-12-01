package elk.elastic;

import org.elasticsearch.action.get.GetResponse; 
import org.elasticsearch.client.transport.TransportClient; 
import org.elasticsearch.common.settings.Settings; 
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import static org.elasticsearch.index.query.QueryBuilders.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.lucene.queryparser.flexible.core.builders.QueryBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryBuilders.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;



public class EsClient { 
	final static int TIME_VALUE =50000;
	
	//处理SQL语句的行
	private List<String> removeSqlTail(List<String> msg){
		List<String> ret = new ArrayList<String>();
		for (String line : msg){
			if (!Pattern.matches("^SQL.*", line) && !Pattern.matches("^SUCCESS:.*", line)) {
				ret.add(line);
			}
			else {
				ret.add(line);
				return ret;
			}
		}
		return ret;
	}
	
	public List<List<String>> getSnippet(String[] node, String logType, String from, String to, String clusterName, String host, int port, String index) throws UnknownHostException {
		System.out.println("Starting to connect " + host + ":" + port + "   Index: " + index);
		List<List<String>> ret = new LinkedList<List<String>>();
		List<String> messageList = new LinkedList<String>();
		List<String> contentList = new LinkedList<String>();
		// on startup
		Settings settings = Settings.builder()
		        .put("cluster.name", clusterName).build();
		//Add transport addresses and do something with the client...
		TransportClient client = new PreBuiltTransportClient(settings)
		        .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port));
		
		
		org.elasticsearch.index.query.QueryBuilder qb = (org.elasticsearch.index.query.QueryBuilder)boolQuery()
				.must(termQuery("host", node[0]))
//				.must(regexpQuery("path", ".*" + logType + ".*"));
				.must(termQuery("log_type", logType));
		
//		BoolQueryBuilder o = boolQuery().must(termQuery("log_type", logType));
//		
//		for (String n : node) {
//			o.should(termQuery("host", n));
//		}
//		org.elasticsearch.index.query.QueryBuilder qb = (org.elasticsearch.index.query.QueryBuilder) o;
		

		try {
			SearchResponse response = client.prepareSearch(index)
			        .setTypes("logs")
			        .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
			        .setScroll(new TimeValue(TIME_VALUE))
			        .setQuery(qb)                 // Query
			        .setPostFilter(QueryBuilders.rangeQuery("@timestamp").from(from).to(to))     // Filter
			        .addSort("@timestamp", SortOrder.fromString("asc"))
			        .setFrom(0).setSize(10000)
			        .setFetchSource(true)
			        .get();
			
			// on shutdown
			
			SearchHit[] hits = new SearchHit[0];
			Map<String, Object> source;
			do {
				hits = response.getHits().getHits();
//				System.out.println(sum += hits.length);
				for (SearchHit hit : hits) {
					source = hit.getSourceAsMap();
					Object content = "";
					if (source.containsKey("content")) {
						 content = source.get("content");
					}
					else {
						content = " ";
					}
					Object message = source.get("message");
					if (source.containsKey("tags") && ((List<String>)source.get("tags")).get(0).equals("multiline")){
						//System.out.println(content.getClass());
						if (content instanceof java.util.ArrayList<?>) {
							List<String> cl2 = (ArrayList<String>)content;
							List<String> ml2 = (ArrayList<String>)message;
							List<String> cl = removeSqlTail((ArrayList<String>)content);
							List<String> ml = removeSqlTail((ArrayList<String>)message);
//							for (String line : ((ArrayList<String>)content)) {
//								contentList.add(line);
//							}
//							for (String line : ((ArrayList<String>)message)) {
//								messageList.add(line);
//							}
							try{
								for (int i = 0; i < cl.size(); i ++) {
									contentList.add(cl.get(i));
									messageList.add(ml.get(ml.size() - cl.size() + i));
								}
							}
							catch (Exception e)
						    {
								System.out.println("=================");
						        //e.printStackTrace();
						        for (String line : cl) System.out.println(line);
						        System.out.println("--------------");
						        for (String line: cl2) System.out.println(line);
						        System.out.println("--------------");
						        for (String line: ml) System.out.println(line);
						        System.out.println("--------------");
						        for (String line: ml2) System.out.println(line);
						    }
						}
						else {
//							if (content == null)
//								System.out.println(message);
							
							contentList.add((String)content);
							if (message instanceof java.util.ArrayList<?>)
								messageList.add(((ArrayList<String>)message).get(0));
							else
								messageList.add((String)message);
						}
					}
					else {
						if (content == null)
							System.out.println(message);
						contentList.add((String)content);
						messageList.add((String)message);
					}
//					String message = (String)hit.getFields().get("line").getValue();
//					if (((String)source.get("tags")).contains("multiline")) {
//						for (String line : content.split("\n")) {
//							ret.add(line);
//						}
//					}
					
			    }
				response = client.prepareSearchScroll(response.getScrollId()).setScroll(new TimeValue(TIME_VALUE)).execute().actionGet();
			} while(hits.length != 0); // Zero hits mark the end of the scroll and the while loop.
			
			client.close();
			
			List<String> snippetArray = new ArrayList<String>(contentList);
			List<String> messageArray = new ArrayList<String>(messageList);
			
			
			ret.add(snippetArray);
			ret.add(messageArray);
			System.out.println("Completed logs search, get " + messageArray.size() + " lines");
			return ret;
		}
		catch(NullPointerException e){
			e.printStackTrace();
			return null;
        } 
		catch (ClassCastException e) {
			e.printStackTrace();
			return null;
		}
	} 
	
	
	public List<List<String>> getSnippet(String[] node, String logType, String path, String clusterName, String host, int port, String index) throws UnknownHostException {
		System.out.println("Starting to connect " + host + ":" + port + "   Index: " + index);
		List<List<String>> ret = new LinkedList<List<String>>();
		List<String> messageList = new LinkedList<String>();
		List<String> contentList = new LinkedList<String>();
		// on startup
		Settings settings = Settings.builder()
		        .put("cluster.name", clusterName).build();
		//Add transport addresses and do something with the client...
		TransportClient client = new PreBuiltTransportClient(settings)
		        .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port));
		
		org.elasticsearch.index.query.QueryBuilder qb = (org.elasticsearch.index.query.QueryBuilder)boolQuery()
				.must(matchQuery("host", node[0]))
//				.must(regexpQuery("path", ".*" + logType + ".*"));
				.must(matchQuery("log_type", logType))
				.must(regexpQuery("path", path));
		
		try {
			SearchResponse response = client.prepareSearch(index)
			        .setTypes("logs")
			        .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
			        .setScroll(new TimeValue(TIME_VALUE))
			        .setQuery(qb)                 // Query
			        .addSort("@timestamp", SortOrder.fromString("asc"))
			        .setFrom(0).setSize(10000)
			        .setFetchSource(true)
			        .get();
			
			// on shutdown
			
			SearchHit[] hits = new SearchHit[0];
			Map<String, Object> source;
			do {
				hits = response.getHits().getHits();
//				System.out.println(sum += hits.length);
				for (SearchHit hit : hits) {
					source = hit.getSourceAsMap();
					Object content = "";
					if (source.containsKey("content")) {
						 content = source.get("content");
					}
					else {
						content = " ";
					}
					Object message = source.get("message");
					if (source.containsKey("tags") && ((List<String>)source.get("tags")).get(0).equals("multiline")){
						//System.out.println(content.getClass());
						if (content instanceof java.util.ArrayList<?>) {
							List<String> cl = (ArrayList<String>)content;
							List<String> ml = (ArrayList<String>)message;
//							for (String line : ((ArrayList<String>)content)) {
//								contentList.add(line);
//							}
//							for (String line : ((ArrayList<String>)message)) {
//								messageList.add(line);
//							}
							for (int i = 0; i < cl.size(); i ++) {
								contentList.add(cl.get(i));
								messageList.add(ml.get(ml.size() - cl.size() + i));
							}
						}
						else {
//							if (content == null)
//								System.out.println(message);
							
							contentList.add((String)content);
							if (message instanceof java.util.ArrayList<?>)
								messageList.add(((ArrayList<String>)message).get(0));
							else
								messageList.add((String)message);
						}
					}
					else {
						if (content == null)
							System.out.println(message);
						contentList.add((String)content);
						messageList.add((String)message);
					}
//					String message = (String)hit.getFields().get("line").getValue();
//					if (((String)source.get("tags")).contains("multiline")) {
//						for (String line : content.split("\n")) {
//							ret.add(line);
//						}
//					}
					
			    }
				response = client.prepareSearchScroll(response.getScrollId()).setScroll(new TimeValue(TIME_VALUE)).execute().actionGet();
			} while(hits.length != 0); // Zero hits mark the end of the scroll and the while loop.
			
			client.close();
			
			List<String> snippetArray = new ArrayList<String>(contentList);
			List<String> messageArray = new ArrayList<String>(messageList);
			
			
			ret.add(snippetArray);
			ret.add(messageArray);
			System.out.println("Completed logs search, get " + messageArray.size() + " lines");
			return ret;
		}
		catch(NullPointerException e){
			e.printStackTrace();
			return null;
        } 
		catch (ClassCastException e) {
			e.printStackTrace();
			return null;
		}
		
		
	}
}
