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

- use constants for protocol keywords
*/

import java.util.HashMap;

import java.io.BufferedReader;
import java.io.BufferedWriter;

public class Message{

  String command;
  HashMap<String, String> headers;
  String body;

  public Message(){
    this.command = null;
    this.headers = new HashMap<>();
    this.body = null;
  }

  public Message(String command, HashMap<String, String> headers, String body){
    this.command = command;
    this.headers = headers;
    this.body = body;
  }

  public static Message readMessage(BufferedReader in) throws Exception{
    String command = in.readLine().replace(" ", "");
    HashMap<String, String> headers = new HashMap<>();
    StringBuffer body = new StringBuffer();

    String line;
    String[] words;

    while((line = in.readLine()) != null && !line.equals("")){
      words = line.split(":");
      headers.put(words[0], words[1]);
    }

    while((line = in.readLine()) != null && !line.equals("MESSAGE_END")){
      body.append(line);
    }

    return new Message(command, headers, body.toString());
  }

  public static void writeMessage(BufferedWriter out, Message msg) throws Exception{
    writeMessage(out, msg.command, msg.headers, msg.body);
  }

  public static void writeMessage(BufferedWriter out, String command, HashMap<String, String> headers, String body) throws Exception{

    // commands: JOIN, LEAVE, STORE, RETRIEVE, etc.
    out.write(command.replace("\n", "") + "\n");

    // headers: BucketId, NextId, PrevId, etc.
    for(String key : headers.keySet()){
        out.write(key.replace("\n", "") + ":" + headers.get(key).replace("\n", "") + "\n");
    }

    // new line: seperates headers from body
    out.write("\n");

    // body: string data, see todo
    out.write(body + "\n");

    // packet end delimiter: some protocols don't have this, e.g. HTTP uses Content-Length to indicate body size
    out.write("MESSAGE_END\n");

    // flushes bytes from local buffer onto network
    out.flush();

  }

  public String toString(){
    return "<Message object command: " + command + " headers: " + headers + " body: " + body + ">";
  }
}
