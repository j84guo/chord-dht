import java.security.MessageDigest;

public class Hashing{

  public static void main(String[] args) throws Exception{
    MessageDigest hashFunction = MessageDigest.getInstance("SHA-256");

    String s = "jackson";
    byte[] b = s.getBytes("UTF-8");

    byte[] digest = hashFunction.digest(b);
    System.out.println(digest.length);
  }
}
