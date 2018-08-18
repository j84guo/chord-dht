package chorddht;

import java.net.Socket;
import java.net.InetAddress;
import java.net.ServerSocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class RequestListener extends Thread{

  private InetAddress ip;
  private int port;
  private Bucket bucket;

  public RequestListener(InetAddress ip, int port, Bucket bucket){
    this.ip = ip;
    this.port = port;
    this.bucket = bucket;
  }

  @Override
  public void run(){
    try(ServerSocket server = new ServerSocket(port, 50, ip)){
      acceptForever(server);
    }catch(Exception e){
      System.out.println("Could not start server... " + e);
    }
  }

  private void acceptForever(ServerSocket server){
    while(true){
      try{
        Socket client = server.accept();
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));

        RequestHandler handler = new RequestHandler(client, in, out, bucket);
        handler.start();
      }catch(Exception e){
        System.err.println("Could not start request handler... " + e);
      }
    }
  }
}
