/*
todo:

- handle edge case in request
- maybe read all data and validate with regex
- determine action when request is invalid

- treat body as array of bytes
- send body length
- use buffered byte streams to read requests
- determine how to transmit binary data
- specify body encoding for string data
- use better delimiter for body key-value pairs

- use constants for protocol keywords
*/

import java.util.HashMap;

import java.io.BufferedReader;
import java.io.BufferedWriter;

public class Message{

  String command;
  HashMap<String, String> headers;
  HashMap<String, String> body;

  public Message(){
    this.command = null;
    this.headers = new HashMap<>();
    this.body = new HashMap<>();
  }

  public Message(String command, HashMap<String, String> headers, HashMap<String, String> body){
    this.command = command;
    this.headers = headers;
    this.body = body;
  }

  public static Message readMessage(BufferedReader in) throws Exception{
    String command = in.readLine().replace(" ", "");
    HashMap<String, String> headers = new HashMap<>();
    HashMap<String, String> body = new HashMap<>();

    String line;
    String[] words;

    while((line = in.readLine()) != null && !line.equals("")){
      words = line.split(":");
      headers.put(words[0], words[1]);
    }

    while((line = in.readLine()) != null && !line.equals("MESSAGE_END")){
      // todo: use a safe delimiter
      words = line.split(":");
      body.put(words[0], words[1]);
    }

    return new Message(command, headers, body);
  }

  public static void writeMessage(BufferedWriter out, Message msg) throws Exception{
    writeMessage(out, msg.command, msg.headers, msg.body);
  }

  public static void writeMessage(BufferedWriter out, String command, HashMap<String, String> headers, HashMap<String, String> body) throws Exception{

    // commands: JOIN, LEAVE, STORE, RETRIEVE, etc.
    out.write(command.replace("\n", "") + "\n");

    // headers: BucketId, NextId, PrevId, etc.
    for(String key : headers.keySet()){
        out.write(key.replace("\n", "") + ":" + headers.get(key).replace("\n", "") + "\n");
    }

    // new line: seperates headers from body
    out.write("\n");

    // body: key-value data, see todo
    for(String key : body.keySet()){
      out.write(key + ":" + body.get(key) + "\n");
    }

    // packet end delimiter: some protocols don't have this, e.g. HTTP uses Content-Length to indicate body size
    out.write("MESSAGE_END\n");

    // flushes bytes from local buffer onto network
    out.flush();

  }

  public String toString(){
    return "<Message object command: " + command + " headers: " + headers + " body: " + body + ">";
  }
}
