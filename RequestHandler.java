/*
todo :

- make as Callable and use ExecutorService to maintain a pool of threads
- send error to client when unable to forward join request
- insert transferred data on leave
*/

import java.util.HashMap;
import java.math.BigInteger;

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
      System.out.println("Could not handle request... " + e.getMessage());
    }finally{
      try{
        out.close();
        in.close();
      }catch(Exception e){
        System.out.println("Could not close socket and streams... " + e.getMessage());
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
      resp.body = "This is some placeholder body data...";
    }else{
      resp = forwardJoinRequest(msg);
    }

    Message.writeMessage(out, resp);
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

  private Message forwardJoinRequest(Message msg) throws Exception{
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

    // todo: insert transferred data
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
        out.write("Request to store data...\n");
        break;

      /*
      - key is forwarded to the node responsible for it
      - responsible node retrieves data
      - resp is recursively returned
      */
      case "RETRIEVE":
        out.write("Request to retrieve data...\n");
        break;

      default:
        System.out.println("Unknown command... " + msg.command);
    }
  }
}
