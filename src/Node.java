import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.swing.text.html.parser.Entity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class Node extends SQLite{

	private static final int NODE_BRANCH = 1;
	private static final int NODE_TEXTS = 2;
	private static final int NODE_REFS = 3;

	private static int nodeCount = 0;

	static String CREATE_NODE_TABLE = 
			"CREATE TABLE " +  "Nodes " + "(\r\n" + 
					"	_id INTEGER PRIMARY KEY,\r\n" + 
					"	bid INTEGER NOT NULL,\r\n" + 
					"	parentNode INTEGER,\r\n" + 
					"	nodeType INTEGER not null,\r\n" + 
					"	siblingNum INTEGER not null,\r\n" + //0 means the first sibling 
					"	enTitle TEXT,\r\n" + 
					"	heTitle TEXT,\r\n" + 

			"	sectionNames TEXT,\r\n" + 
			"	heSectionNames TEXT,\r\n" + 


			//for support of altStructs
			"	structNum INTEGER NOT NULL default 1,\r\n" + 
			"	textDepth INTEGER,\r\n" + 

			"	startTid INTEGER,\r\n" +  //maybe only used with refferences on alt structure
			"	endTid INTEGER,\r\n" +  //maybe only used with refferences on alt structure
			"	extraTids TEXT,\r\n" +  //maybe only used with refferences on alt structure ex. "[34-70,98-200]"
			//maybe some stuff like to display chap name and or number (ei. maybe add some displaying info)

	//		"	FOREIGN KEY (bid) \r\n" + 
	//		"		REFERENCES Books (_id)\r\n" + 
	//		"		ON DELETE CASCADE,\r\n" + 
	"	FOREIGN KEY (parentNode) \r\n" + 
	"		REFERENCES Nodes (_id)\r\n" + 
	"		ON DELETE CASCADE,\r\n" + 
	"	CONSTRAINT uniqSiblingNum UNIQUE (bid,parentNode,siblingNum,structNum)\r\n" + //needs bid b/c otherwise parent is 0 for each root 

				")";





	protected static int addText(Connection c, JSONObject enJSON, JSONObject heJSON) throws JSONException{
		if(enJSON == null && heJSON == null){
			System.err.print("Both JSONs are null in Node.addText()");
			return -1;
		}
		int enLang =0,heLang=0;
		String title ="";
		JSONObject node = null;
		if(enJSON != null){
			enLang = returnLangNums(enJSON.getString("language"));
			title = enJSON.getString("title");
			node = (JSONObject) enJSON.get("schema");
		}		
		if(heJSON != null){
			heLang = returnLangNums(heJSON.getString("language"));
			title = heJSON.getString("title");
			node = (JSONObject) heJSON.get("schema");
		}
		int lang = enLang + heLang;
		/**
		 * check for errors
		 */
		if((enLang != SQLite.LANG_EN  && enLang != 0)|| (heLang != SQLite.LANG_HE  && heLang != 0)){
			System.err.println("Error in Node.addText(): not right lang numbers. enLang:" + enLang  + " heLang:" + heLang);
			return -1;
		}
		if(title.equals("")){
			System.err.println("no Title");
			return -1;
		}
		if(enJSON != null && heJSON != null){
			if(!heJSON.get("schema").toString().equals(enJSON.get("schema").toString())){
				System.err.println("en and he JSONs schemas don't match\n");	
				System.out.println(heJSON.get("schema"));
				System.out.println(enJSON.get("schema"));
				return -1;
			}
			if(!enJSON.getString("title").equals(heJSON.getString("title"))){
				System.err.println("en and he JSONs title don't match" + enJSON.getString("title") + " - " + heJSON.getString("title"));
				return -1;
			}

		}
		if(!booksInDB.containsKey(title)){
			System.err.println("Don't have book in DB and trying to add text");
			return -1;
		}
		int bid = booksInDBbid.get(title); 
		JSONObject enText = null, heText = null;
		if(enJSON != null){
			enText = (JSONObject) enJSON.get("text");
		}
		if(heJSON != null){
			heText = (JSONObject) heJSON.get("text");
		}
		insertNode(c, node,enText,heText, 0,0,bid,0,lang);
		return 1; //it worked
	}


	private static void insertSingleNodeToDB(Connection c, Integer nid, Integer bid, Integer parentNode,Integer nodeType, Integer siblingNum,
			String enTitle, String heTitle, Integer structNum, Integer textDepth, Integer startTid, Integer endTid,
			String extraTids, String sectionNames, String heSectionNames
			){
		final String INSERT_NODE = "INSERT INTO Nodes (" +
				"_id,bid,parentNode,nodeType,siblingNum,enTitle,heTitle,structNum,textDepth,startTid,endTid,extraTids,sectionNames,heSectionNames)"
				+ "VALUES (?,?, ?, ?, ?, ?, ?, ?,?, ?, ?,?,?,?);";

		PreparedStatement stmt;
		try {
			stmt = c.prepareStatement(INSERT_NODE);
			if(nid != null) stmt.setInt(1, nid);
			if(bid != null) stmt.setInt(2,bid);
			if(parentNode != null) stmt.setInt(3,parentNode);
			if(nodeType != null) stmt.setInt(4,nodeType);
			if(siblingNum != null) stmt.setInt(5,siblingNum);
			if(enTitle != null) stmt.setString(6,enTitle);
			if(heTitle != null) stmt.setString(7,heTitle);
			if(structNum != null) stmt.setInt(8,structNum); 
			if(startTid != null) stmt.setInt(10,startTid);
			if(endTid != null) stmt.setInt(11,endTid);
			if(extraTids != null) stmt.setString(12,extraTids);
			if(sectionNames != null) stmt.setString(13,sectionNames);
			if(heSectionNames != null) stmt.setString(14,heSectionNames);
			stmt.executeUpdate();
			stmt.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	private static int insertNode(Connection c, JSONObject node,JSONObject enText,JSONObject heText,int depth, int siblingNum,int bid,int parentNode,int lang){
		if(enText == null && heText == null){
			System.err.println("Both JSONs are null in insertNode");
			return -1;
		}
		String heTitle = node.getString("heTitle");
		String enTitle = node.getString("enTitle");
		int nodeID = ++nodeCount;
		int nodeType;
		JSONArray nodes = null;
		try{
			nodes  =  node.getJSONArray("nodes");
			if(depth >0){
				if(enText != null)
					enText = enText.getJSONObject(enTitle); //this is the test to determine the Node type
				if(heText != null)
					heText = heText.getJSONObject(enTitle); //this is the test to determine the Node type
			}
			nodeType = NODE_BRANCH;
		}catch(Exception e){
			nodeType = NODE_TEXTS;//leaf
		}
		insertSingleNodeToDB(c, nodeID, bid, parentNode, nodeType, siblingNum, enTitle, heTitle, 1,
				null, null, null, null, null, null);
	
		if(nodeType == NODE_BRANCH){
			for(int i =0;i<nodes.length();i++){
				insertNode(c, (JSONObject) nodes.get(i),enText,heText,depth+1,i,bid,nodeID,lang);
			}
		} else if(nodeType == NODE_TEXTS){
			int enTextDepth = 0, heTextDepth = 0;
			if(enText != null){
				JSONArray textArray = (JSONArray) enText.get(enTitle);
				ArrayList<Integer> levels = new ArrayList<Integer>();
				enTextDepth = insertTextArray(c, textArray, levels,bid,LANG_EN,nodeID);
			}
			if(heText != null){
				JSONArray textArray = (JSONArray) heText.get(enTitle);
				ArrayList<Integer> levels = new ArrayList<Integer>();
				heTextDepth = insertTextArray(c, textArray, levels,bid,LANG_HE,nodeID);
			}
			int textDepth = Math.max(enTextDepth, heTextDepth);

			String updateStatement = "UPDATE Nodes set textDepth = ? WHERE _id = ?";
			try {
				PreparedStatement stmt = c.prepareStatement(updateStatement);
				stmt.setInt(1, textDepth);
				stmt.setInt(2, nodeID);
				stmt.executeUpdate();
				stmt.close();
			} catch (SQLException e) {
				System.err.println("Error 1 in InsertNode: " + e);

			}

		}

		return 0;
	}


	/**
	 * //TODO have to deal with only hebrew getting into db.  
	 * @param connection
	 * @param textArray
	 * @param levels
	 * @param bid
	 * @param lang
	 * @param parentNodeID
	 * @return textDepth
	 */
	private static int insertTextArray(Connection c, JSONArray textArray,ArrayList<Integer> levels,int bid,int lang,int parentNodeID){
		try{
			int returnDepth = 0;
			for(int i=0;i<textArray.length();i++){
				ArrayList<Integer> levels2 = (ArrayList<Integer>) levels.clone();
				levels2.add(i);
				returnDepth = insertTextArray(c, textArray.getJSONArray(i),levels2,bid,lang,parentNodeID);
			}
			return returnDepth;
		}catch(Exception JSONException){
			levels.add(0);
			String title = "complex text:" + bid;
			int [] it = new int[MAX_LEVELS + 1];

			for(int i=0;i<textArray.length();i++){
				levels.set(levels.size()-1, i);
				for(int j=0;j<levels.size();j++)
					it[j+1] = levels.get(levels.size()-j-1);
				//String text = textArray.getString(i);
				//System.out.println(" " + levels + "---" + it);
				Text.insertValues(c, title, levels.size(), bid, textArray, lang, it,parentNodeID);

			}
			return levels.size();
		}

	}

	/**
	 * add Nodes for alt structures.
	 * @param c
	 * @param nodes
	 * @param bid
	 * @param parentNode
	 * @param structNum
	 * @return
	 * @throws JSONException
	 */
	private static int addSchemaNodes(Connection c, JSONArray nodes,int bid,int parentNode, int structNum) throws JSONException{
		for(int j =0;j<nodes.length();j++){
			JSONObject node = nodes.getJSONObject(j);
			String enTitle = node.getString("title");
			String heTitle = node.getString("heTitle");
			String nodeTypeString =  node.getString("nodeType");
			int nodeType = -1;
			if(nodeTypeString.equals("ArrayMapNode")){
				nodeType = NODE_BRANCH;//NODE_REFS;
			}
			else{
				System.err.println("Error not a ref");
				return -1;
			}

			String sectionNames = node.getJSONArray("sectionNames").toString().replace("\"\"", "\"Section\"");
			String heSectionNames = sectionNames;//node.getJSONArray("heSectionNames").toString().replace("\"\"", "\"Section\"");
			//TODO get real heSection names
			JSONArray refs = node.getJSONArray("refs");
			//if (depth == 0) use wholeRef and it's not a grid //TODO 
			//System.out.println(enTitle + " " +  heTitle + " " + nodeType + " " + sectionNames + refs);
			int nodeID = ++nodeCount;
			
			insertSingleNodeToDB(c, nodeID, bid, parentNode, nodeType, j, enTitle, heTitle, structNum,
					null, null, null, null, sectionNames, heSectionNames);
			
			if(nodeTypeString.equals("ArrayMapNode")){
				int subParentNode = nodeID;
				for(int i=0;i<refs.length();i++){
					String ref = refs.getString(i);
					nodeID = ++nodeCount;
					nodeType = NODE_REFS;
					int startTID = -1;
					int endTID = -2;
					insertSingleNodeToDB(c, nodeID, bid, subParentNode, nodeType,
							i, "", "", structNum, null, startTID, endTID, ref,
							sectionNames, heSectionNames);
				}
			}

		}
		return 1;//worked
	}

	protected static int addSchemas(Connection c, JSONObject schemas) throws JSONException{
		try{

			JSONObject alts = schemas.getJSONObject("alts");
			String bookTitle = schemas.getString("title");
			int bid = booksInDBbid.get(bookTitle);
			String [] altNames = alts.getNames(alts);
			for(int i=0;i<altNames.length;i++){
				//System.out.println(altNames[i]);
				JSONObject alt = alts.getJSONObject(altNames[i]);
				Integer nodeID = ++nodeCount;
				int structNum = i+2;//1 is the default, so add 2 to the alt structs.
				String enTitle =  altNames[i];
				String heTitle = enTitle; //TODO make real heTitle
				insertSingleNodeToDB(c, nodeID, bid, 0, NODE_BRANCH, 0,enTitle , heTitle, structNum,
						null, null, null, null, null, null);
				JSONArray nodes = alt.getJSONArray("nodes");
				
				addSchemaNodes(c, nodes, bid,nodeID,structNum);
			}
		}catch(JSONException e1){
			if(!e1.toString().equals("org.json.JSONException: JSONObject[\"alts\"] not found.")){
				throw e1;//I don't know what it is so throw it back out there
			}
			//else it only has a single structure 
		}catch(Exception e){
			System.err.println("Error (addSchemas): " + e);
		}

		return 1; //it worked
	}

}	

