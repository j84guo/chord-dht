/*
todo :

- make as Callable and use ExecutorService to maintain a pool of threads
- send error to client when unable to forward join request
- insert transferred data on leave
*/

package chorddht;

import java.math.BigInteger;

import java.util.HashMap;
import java.util.Iterator;

import java.net.Socket;
import java.net.InetAddress;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class RequestHandler extends Thread{

  private Socket client;
  private BufferedReader in;
  private BufferedWriter out;
  private Bucket bucket;

  public RequestHandler(Socket client, BufferedReader in, BufferedWriter out, Bucket bucket){
    this.client = client;
    this.in = in;
    this.out = out;
    this.bucket = bucket;
  }

  @Override
  public void run(){
    try{
      handleRequest(in, out);
    }catch(Exception e){
      System.out.println("Could not handle request... " + e);
    }finally{
      try{
        // closing a buffered writer closes the underlying sreams
        // when and output/input stream associated with a socket is closed, so is the socket
        // closing the socket closes the associated output/input streams, if they are not already
        // finally, the buffered reader is closed as well
        // the output stream is closed first because certain output streams may throw an exception if closed twice
        // as per comments in http://developer.classpath.org/doc/java/io/BufferedOutputStream-source.html
        out.close();
        in.close();
      }catch(Exception e){
        System.out.println("Could not close socket and streams... " + e);
      }
    }
  }

  private boolean bucketOwnsKey(String s){
    return bucket.ownsKey(new BigInteger(s));
  }

  private void handleJoinRequest(Message msg) throws Exception{
    boolean keyHere = bucketOwnsKey(msg.headers.get("BucketId"));
    Message resp;

    if(keyHere){
      resp = new Message();

      if(bucket.isRoot && bucket.nextId == null && bucket.prevId == null){
        updateRootAfterJoin(msg.headers);
        joinedRootHeaders(resp.headers);
      }else{
        joinedNodeHeaders(resp.headers);
        updateNodeAfterJoin(msg.headers);
      }

      resp.command = "JOIN_OK";

      Iterator<String> it = bucket.data.keySet().iterator();
      while(it.hasNext()){
          String key = it.next();
          if(!keyInBetween(new BigInteger(key), bucket.prevId, bucket.bucketId)){
            resp.body.put(key, bucket.data.get(key));
            it.remove();
          }
      }

    }else{
      resp = forwardRequest(msg);
    }

    Message.writeMessage(out, resp);
  }

  protected boolean keyInBetween(BigInteger k, BigInteger a, BigInteger b){
    if(k.compareTo(b) < 0){
      return a.compareTo(b) < 0 ? k.compareTo(a) > 0 : true;
    }else{
      return a.compareTo(b) < 0 ? false : k.compareTo(a) > 0;
    }
  }

  // response for new node joining root
  private void joinedRootHeaders(HashMap<String, String> headers){
    headers.put("NextId", bucket.bucketId.toString());
    headers.put("NextIp", bucket.ip.getHostAddress());
    headers.put("NextPort", String.valueOf(bucket.port));

    headers.put("PrevId", bucket.bucketId.toString());
    headers.put("PrevIp", bucket.ip.getHostAddress());
    headers.put("PrevPort", String.valueOf(bucket.port));
  }

  // response for new node joining network
  private void joinedNodeHeaders(HashMap<String, String> headers){
    headers.put("NextId", bucket.bucketId.toString());
    headers.put("NextIp", bucket.ip.getHostAddress());
    headers.put("NextPort", String.valueOf(bucket.port));

    headers.put("PrevId", bucket.prevId.toString());
    headers.put("PrevIp", bucket.prevIp.getHostAddress());
    headers.put("PrevPort", String.valueOf(bucket.prevPort));
  }

  // update root after it is joined by new successor/predecessor
  private void updateRootAfterJoin(HashMap<String, String> headers) throws Exception{
    bucket.nextId = new BigInteger(headers.get("BucketId"));
    bucket.nextIp = InetAddress.getByName(headers.get("BucketIp"));
    bucket.nextPort = Integer.parseInt(headers.get("BucketPort"));

    bucket.prevId = new BigInteger(headers.get("BucketId"));
    bucket.prevIp = InetAddress.getByName(headers.get("BucketIp"));
    bucket.prevPort = Integer.parseInt(headers.get("BucketPort"));
  }

  // update node after it is joined by new predecessor
  private void updateNodeAfterJoin(HashMap<String, String> headers) throws Exception{
    bucket.prevId = new BigInteger(headers.get("BucketId"));
    bucket.prevIp = InetAddress.getByName(headers.get("BucketIp"));
    bucket.prevPort = Integer.parseInt(headers.get("BucketPort"));
  }

  private Message forwardRequest(Message msg) throws Exception{
    Message resp = null;

    try(
      Socket fwdSocket = new Socket(bucket.nextIp, bucket.nextPort);
      BufferedReader fwdIn = new BufferedReader(new InputStreamReader(fwdSocket.getInputStream()));
      BufferedWriter fwdOut = new BufferedWriter(new OutputStreamWriter(fwdSocket.getOutputStream()));
    ){
      Message.writeMessage(fwdOut, msg);
      resp = Message.readMessage(fwdIn);
    }

    return resp;
  }

  private void handleNewNodeRequest(Message msg) throws Exception{
    updateNextToNewNode(msg.headers);
  }

  private void updateNextToNewNode(HashMap<String, String> headers) throws Exception{
    bucket.nextId = new BigInteger(headers.get("BucketId"));
    bucket.nextIp = InetAddress.getByName(headers.get("BucketIp"));
    bucket.nextPort = Integer.parseInt(headers.get("BucketPort"));
  }

  private void handleLeaveRequest(Message msg) throws Exception{
    updatePrevAfterLeave(msg);
  }

  private void updatePrevAfterLeave(Message msg) throws Exception{
    if(bucket.bucketId.toString().equals(msg.headers.get("PrevId"))){
      bucket.nextId = null;
      bucket.nextIp = null;
      bucket.nextPort = -1;

      bucket.prevId = null;
      bucket.prevIp = null;
      bucket.prevPort = -1;
    }else{
      bucket.prevId = new BigInteger(msg.headers.get("PrevId"));
      bucket.prevIp = InetAddress.getByName(msg.headers.get("PrevIp"));
      bucket.prevPort = Integer.parseInt(msg.headers.get("PrevPort"));
    }

    // insert transferred data
    for(String key : msg.body.keySet()){
      bucket.data.put(key, msg.body.get(key));
    }
  }

  private void handleNodeGoneRequest(Message msg) throws Exception{
    System.out.println("Handling node gone request...");
    updateNextAfterLeave(msg);
  }

  private void updateNextAfterLeave(Message msg) throws Exception{
    if(bucket.bucketId.toString().equals(msg.headers.get("NextId"))){
      bucket.nextId = null;
      bucket.nextIp = null;
      bucket.nextPort = -1;

      bucket.prevId = null;
      bucket.prevIp = null;
      bucket.prevPort = -1;
    }else{
      bucket.nextId = new BigInteger(msg.headers.get("NextId"));
      bucket.nextIp = InetAddress.getByName(msg.headers.get("NextIp"));
      bucket.nextPort = Integer.parseInt(msg.headers.get("NextPort"));
    }
  }

  // DataId
  // DataValue
  private void handleStoreRequest(Message msg) throws Exception{
    boolean keyHere = bucketOwnsKey(msg.body.get("DataId"));
    Message resp;

    if(keyHere){
      bucket.data.put(msg.body.get("DataId"), msg.body.get("DataValue"));

      resp = new Message();
      resp.command = "STORE_OK";
    }else{
      resp = forwardRequest(msg);
    }

    Message.writeMessage(out, resp);
  }

  // DataId
  private void handleRetrieveRequest(Message msg) throws Exception{
    boolean keyHere = bucketOwnsKey(msg.body.get("DataId"));
    Message resp;

    if(keyHere){
      String key = msg.body.get("DataId");
      String value = bucket.data.containsKey(key) ? bucket.data.get(key) : "Data not found...";

      resp = new Message();

      resp.command = "RETRIEVE_OK";
      resp.body.put("DataId", key);
      resp.body.put("DataValue", value);
    }else{
      resp = forwardRequest(msg);
    }

    Message.writeMessage(out, resp);
  }

  private void handleRequest(BufferedReader in, BufferedWriter out) throws Exception{
    Message msg = Message.readMessage(in);

    switch(msg.command){

      /*
      - new node presents id
      - root forwards msg and eventually responds with JOIN_OK
      - JOIN_OK has successor/predecessor, and data for new node to take on
      */
      case "JOIN":
        handleJoinRequest(msg);
        break;

      /*
      - new node sends its id to precessor
      - predecessor updates its successor
      */
      case "NEW_NODE":
        handleNewNodeRequest(msg);
        break;

      /*
      - leaving node sends its predecessor to successor
      - msg includes data for successor to take on
      */
      case "LEAVE":
        handleLeaveRequest(msg);
        break;

      /*
      - leaving node sends its successor to predecessor
      - predecessor updates its successor
      */
      case "NODE_GONE":
        handleNodeGoneRequest(msg);
        break;

      /*
      - key is forwarded to the node responsible for it
      - responsible node stores data
      - resp is recursively returned
      */
      case "STORE":
        handleStoreRequest(msg);
        break;

      /*
      - key is forwarded to the node responsible for it
      - responsible node retrieves data
      - resp is recursively returned
      */
      case "RETRIEVE":
        handleRetrieveRequest(msg);
        break;

      default:
        System.out.println("Unknown command... " + msg.command);
    }
  }
}
