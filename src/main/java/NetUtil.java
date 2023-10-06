//import org.json.JSONObject;
//
//import java.io.IOException;
//import java.net.DatagramPacket;
//import java.net.DatagramSocket;
//
//public class NetUtil {
//
//  public static void sendMessage(DatagramSocket sock, JSONObject obj) throws IOException {
//    byte[] data = obj.toString().getBytes();
//
//    DatagramPacket packet = new DatagramPacket(data, data.length, sock.getInetAddress(), sock.getPort());
//    sock.send(packet);
//  }
//
//
//
//}
