import java.net.Socket;
import java.net.InetAddress;
import java.net.ServerSocket;

import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class KkServer{

  public static void main(String[] args){
    if(args.length != 1){
        System.err.println("Usage: java KkServer <port>");
        System.exit(1);
    }

    int port = Integer.parseInt(args[0]);
    InetAddress ip = null;

    try{
      ip = InetAddress.getByName("127.0.0.1");
    }catch(Exception e){
      System.out.println(e.getMessage());
    }

    try(
      ServerSocket s = new ServerSocket(port, 50, ip);
      Socket c = s.accept();
      PrintWriter out = new PrintWriter(c.getOutputStream(), true);
      BufferedReader in = new BufferedReader(
        new InputStreamReader(c.getInputStream())
      );
    ){
      String input, output;

      KkProtocol kkp = new KkProtocol();
      output = kkp.processInput(null);
      out.println(output);

      while((input = in.readLine()) != null){
        output = kkp.processInput(input);
        out.println(output);

        if(output.equals("Bye.")){
          break;
        }
      }
    }catch(Exception e){
      System.out.println(e.getMessage());
    }
  }
}
