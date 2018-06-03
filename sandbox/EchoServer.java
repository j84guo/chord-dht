import java.net.Socket;
import java.net.InetAddress;
import java.net.ServerSocket;

import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class EchoServer{

  public static void main(String[] args){
      if(args.length != 1){
          System.err.println("Usage: java EchoServer <port>");
          System.exit(1);
      }

      int port = Integer.parseInt(args[0]);
      InetAddress ip = null;

      try{
        ip = InetAddress.getByName("127.0.0.1");
      }catch(Exception e){
        System.out.println(e);
      }

      try(
        ServerSocket s = new ServerSocket(port, 50, ip);
        Socket c = s.accept();
        PrintWriter out = new PrintWriter(c.getOutputStream(), true);
        BufferedReader in = new BufferedReader(
          new InputStreamReader(c.getInputStream())
        );
      ){
        String input;

        // EchoServer reads one line of input at a time
        while((input = in.readLine()) != null){
          System.out.println("received " + input);

          // Even if the server sleeps before replying the client will still wait for data.
          // One technique for indicating how many bytes should be read is to send a header
          // containing the content length.
          // Alternatively one could simply end the TCP connection.
          // Also alternatively, one could use an end of message delimiter.
          // It is also possible that TCP maintains a timeout afterwhich the connection is closed if no data is received.
          // Thread.sleep(5000);

          // just print out received data
          // out.println("echo: " + input);
        }

        System.out.println("Input stream ended.");
      }
      catch(Exception e){
        System.out.println(e);
      }
  }
}
