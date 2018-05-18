import java.net.Socket;

import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class KkSocket{

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
        System.out.println("server: " + in.readLine());

        String input;
        while((input = stdin.readLine()) != null){
          out.println(input);

          String response = in.readLine();
          System.out.println("response: " + response);
          if(response.equals("Bye.")){
            break;
          }
        }
      }
      catch(Exception e){
        System.out.println(e);
      }
  }
}
