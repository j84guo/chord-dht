/*
todo:

- thread safe interface to bucket object
- insert any transferred data when joining
- implement leave, send data for successor to take on
*/

import java.math.BigInteger;
import java.security.MessageDigest;

import java.util.HashMap;
import java.util.Scanner;

import java.net.Socket;
import java.net.InetAddress;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class Bucket{

  public static final BigInteger HASH_RANGE = getSha256Range();

  /*
  zgl - 172.31.72.10
  home - 192.168.2.41
  */
  public static final InetAddress ROOT_PRIVATE_IP = getLocalHost();
  public static final int ROOT_PORT = 5000;

  protected boolean isRoot;

  protected BigInteger bucketId;
  protected InetAddress ip;
  protected int port;
  protected HashMap<String, String> data;

  protected BigInteger nextId;
  protected InetAddress nextIp;
  protected int nextPort;

  protected BigInteger prevId;
  protected InetAddress prevIp;
  protected int prevPort;

  private Scanner scanner;

  public Bucket(InetAddress ip, int port, boolean isRoot){
    this.isRoot = isRoot;

    this.bucketId = getBucketId(ip, port);
    this.ip = ip;
    this.port = port;
    this.data = new HashMap<>();

    this.nextId = null;
    this.nextIp = null;
    this.nextPort = -1;

    this.prevId = null;
    this.prevIp = null;
    this.prevPort = -1;

    this.scanner = new Scanner(System.in);
    System.out.println("Created " + (isRoot ? "root" : "non-root") + " bucket on: " + ip + ":" + port);
  }

  private static BigInteger getSha256Range(){
    return new BigInteger("2").pow(256);
  }

  public static InetAddress getLocalHost(){
    InetAddress host = null;

    try{
      host = InetAddress.getLocalHost();
    }catch(Exception e){
      exitWithError("Could not get local host... " + e.getMessage());
    }

    return host;
  }

  private static BigInteger getBucketId(InetAddress ip, int port){
    BigInteger id = null;

    try{
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      String text = ip.getHostAddress() + ":" + port;
      byte[] digest = sha256.digest(text.getBytes("UTF-8"));
      id = new BigInteger(1, digest);
    }catch(Exception e){
      exitWithError("Could not compute bucket id... " + e.getMessage());
    }

    return id;
  }

  public void start(){
    joinNetwork();
    startRequestListener();
    cliLoop();
  }

  private void joinNetwork(){
    System.out.println("Starting bucket: " + bucketId.toString());

    if(!isRoot){
      sendJoin();
    }
  }

  // query root, get successor and predecessor, insert any transferred data
  private void sendJoin(){
    try(
      Socket socket = new Socket(ROOT_PRIVATE_IP, ROOT_PORT);
      BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    ){

      Message msg = buildJoinMessage();
      Message.writeMessage(out, msg);

      Message resp = Message.readMessage(in);
      updateNextPrevData(resp);

      sendNewNode();
    }catch(Exception e){
      exitWithError("Could not join network... " + e.getMessage());
    }
  }

  private void sendNewNode() throws Exception{
    try(
      Socket prev = new Socket(prevIp, prevPort);
      BufferedReader in = new BufferedReader(new InputStreamReader(prev.getInputStream()));
      BufferedWriter out = new BufferedWriter(new OutputStreamWriter(prev.getOutputStream()));
    ){
      Message msg = buildNewNodeMessage();
      Message.writeMessage(out, msg);
    }
  }

  private Message buildNewNodeMessage(){
    Message msg = new Message();
    msg.command = "NEW_NODE";

    msg.headers.put("BucketId", bucketId.toString());
    msg.headers.put("BucketIp", ip.getHostAddress());
    msg.headers.put("BucketPort", String.valueOf(port));

    msg.body = "The body should be empty!";
    return msg;
  }

  private Message buildJoinMessage(){
    Message msg = new Message();
    msg.command = "JOIN";

    msg.headers.put("BucketId", bucketId.toString());
    msg.headers.put("BucketIp", ip.getHostAddress());
    msg.headers.put("BucketPort", String.valueOf(port));

    msg.body = "The body should be empty!";
    return msg;
  }

  private void updateNextPrevData(Message resp) throws Exception{
    nextId = new BigInteger(resp.headers.get("NextId"));
    nextIp = InetAddress.getByName(resp.headers.get("NextIp"));
    nextPort = Integer.parseInt(resp.headers.get("NextPort"));

    prevId = new BigInteger(resp.headers.get("PrevId"));
    prevIp = InetAddress.getByName(resp.headers.get("PrevIp"));
    prevPort = Integer.parseInt(resp.headers.get("PrevPort"));

    // todo: insert transferred data after joining
  }

  private void startRequestListener(){
    RequestListener listener = new RequestListener(ip, port, this);
    listener.start();
  }

  private void leaveNetwork(){
    if(nextId != null && prevId != null){
      sendLeave();
    }
    System.exit(0);
  }

  private void sendNodeGone() throws Exception{
    try(
      Socket prev = new Socket(prevIp, prevPort);
      BufferedReader in = new BufferedReader(new InputStreamReader(prev.getInputStream()));
      BufferedWriter out = new BufferedWriter(new OutputStreamWriter(prev.getOutputStream()));
    ){
      Message msg = buildNodeGoneMessage();
      Message.writeMessage(out, msg);
    }
  }

  private Message buildNodeGoneMessage(){
    Message msg = new Message();
    msg.command = "NODE_GONE";

    msg.headers.put("NextId", nextId.toString());
    msg.headers.put("NextIp", nextIp.getHostAddress());
    msg.headers.put("NextPort", String.valueOf(nextPort));

    msg.body = "The body should be empty!";
    return msg;
  }

  private void sendLeave(){
    try(
      Socket next = new Socket(nextIp, nextPort);
      BufferedReader in = new BufferedReader(new InputStreamReader(next.getInputStream()));
      BufferedWriter out = new BufferedWriter(new OutputStreamWriter(next.getOutputStream()));
    ){
      Message msg = buildLeaveMessage();
      Message.writeMessage(out, msg);

      sendNodeGone();
    }catch(Exception e){
      exitWithError("Could not leave network... " + e.getMessage());
    }
  }

  private Message buildLeaveMessage(){
    Message msg = new Message();
    msg.command = "LEAVE";

    msg.headers.put("PrevId", prevId.toString());
    msg.headers.put("PrevIp", prevIp.getHostAddress());
    msg.headers.put("PrevPort", String.valueOf(prevPort));

    msg.body = "The body should contain any data to transfer!";
    return msg;
  }

  private void cliLoop(){
    String input;

    while(true){
      input = scanner.nextLine();

      switch(input){
        case "bucket":
          System.out.println(this);
          break;

        case "threads":
          System.out.println("Thread count (system group): " + Thread.activeCount());
          break;

        case "leave":
          leaveNetwork();
          break;

        default:
          System.out.println("Unknown: " + input);
      }
    }
  }

  protected boolean ownsKey(BigInteger key){
    if(!isValidKey(key)){
      throw new IllegalArgumentException("Invalid hash key.");
    }

    if(nextId == null && prevId == null){
      return true;
    }else if(key.compareTo(bucketId) < 0){
      return prevId.compareTo(bucketId) < 0 ? key.compareTo(prevId) > 0 : true;
    }else{
      return prevId.compareTo(bucketId) < 0 ? false : key.compareTo(prevId) > 0;
    }
  }

  private static boolean isValidKey(BigInteger key){
    return key.compareTo(new BigInteger("0")) >= 0 && key.compareTo(HASH_RANGE) < 0;
  }

  public static void exitWithError(String error){
    System.err.println(error);
    System.exit(1);
  }

  public static void exitWithUsage(){
    System.err.println("usage: java Bucket <port>");
    System.exit(1);
  }

  public String toString(){
    return "<Bucket object bucketId: " + bucketId + " ip: " + ip.getHostAddress() + " port: " + port +
      " nextId: " + (nextId == null ? "null" : nextId) + " nextIp: " + (nextIp == null ? "null" : nextIp.getHostAddress()) + " nextPort: " + nextPort +
      " prevId: " + (prevId == null ? "null" : prevId) + " prevIp: " + (prevIp == null ? "null" : prevIp.getHostAddress()) + " prevPort: " + prevPort + ">";
  }

  public static void main(String[] args) throws Exception{
    if(args.length < 1){
      exitWithUsage();
    }

    InetAddress ip;
    int port;
    boolean isRoot;

    if(args[0].equals("root")){
      ip = Bucket.ROOT_PRIVATE_IP;
      port = Bucket.ROOT_PORT;
      isRoot = true;
    }else{
      ip = InetAddress.getLocalHost();
      port = Integer.parseInt(args[0]);
      isRoot = false;
    }

    Bucket bucket = new Bucket(ip, port, isRoot);
    bucket.start();
  }
}
