import java.net.Socket;

import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class EchoSocket{

  public static void main(String[] args){
      String host = args[0];
      int port = Integer.parseInt(args[1]);

      try(
        Socket s = new Socket(host, port);
        PrintWriter out = new PrintWriter(s.getOutputStream(), true);
        BufferedReader in = new BufferedReader(
          new InputStreamReader(s.getInputStream())
        );
        BufferedReader stdin = new BufferedReader(
          new InputStreamReader(System.in)
        );
      ){
        String input;
        while((input = stdin.readLine()) != null){
          out.println(input);
          System.out.println("response: " + in.readLine());
        }
      }
      catch(Exception e){
        System.out.println(e);
      }
  }
}
