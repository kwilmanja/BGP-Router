import org.json.JSONException;
import org.json.JSONObject;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class Router {

  public RouterType type;
  public DatagramSocket sock;
  public String ip;


  public Router(String routerString) throws SocketException, UnknownHostException, JSONException {

    String[] strs = routerString.split("-");

    this.type = RouterType.of(strs[2]);
    this.ip = strs[1];

    int port = Integer.parseInt(strs[0]);
    this.sock = new DatagramSocket();
    this.sock.connect(InetAddress.getByName("localhost"), port);
    this.sendHandshake();
  }

  private void sendHandshake() throws JSONException {
    JSONObject handshake = new JSONObject();
    handshake.put("src", this.getOurIP());
    handshake.put("dst", this.ip);
    handshake.put("type", "handshake");
    handshake.put("msg", new JSONObject());

    handshake.toString().getBytes()

  }



  public String getOurIP(){
    return ip.substring(0, ip.length()-1) + "1";
  }


}
