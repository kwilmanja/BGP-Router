import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Router {

  private final int asn;

  private final Map<String, DatagramSocket> sockets = new HashMap<>();
  private final Map<String, Relation> relations = new HashMap<>();
  private final ArrayList<String> networks = new ArrayList<>();

  public Router(int asn, ArrayList<String> routerStrings) throws Exception {

    this.asn = asn;

    for (String routerString: routerStrings){
      String[] strs = routerString.split("-");

      String ip = strs[1];
      int port = Integer.parseInt(strs[0]);

      this.relations.put(ip, Relation.of(strs[2]));
      DatagramSocket ds = new DatagramSocket();
      ds.connect(InetAddress.getByName("localhost"), port);
      this.sockets.put(ip, ds);
    }

    this.sendHandshakes();
  }

  private void sendHandshakes() throws JSONException, IOException {
    for(String ip : this.networks){
      JSONObject handshake = new JSONObject();
      handshake.put("src", this.getOurIP(ip));
      handshake.put("dst", ip);
      handshake.put("type", "handshake");
      handshake.put("msg", new JSONObject());
      NetUtil.sendMessage(this.sockets.get(ip), handshake);
    }

  }

  public void run(){
    while(true){

    }
  }



  public String getOurIP(String ip){
    return ip.substring(0, ip.length()-1) + "1";
  }


}
