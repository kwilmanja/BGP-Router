import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Main {




  public static void main(String[] args) throws Exception {
    System.out.println("Hello Andrew Panzone");
    int asn = Integer.parseInt(args[0]);

    ArrayList<String> routers = new ArrayList<>();
    for(int i=1; i<args.length; i++){
      routers.add(args[i]);
    }

    Router r = new Router(asn, routers);
    r.run();
  }


  public static class Router {

    private final int asn;

    private final Map<String, DatagramSocket> sockets = new HashMap<>();
    private final Map<String, Relation> relations = new HashMap<>();
    private final ArrayList<String> networks = new ArrayList<>();
//    private final NetUtil netUtil = new NetUtil();

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

  public static class NetUtil {

    public static void sendMessage(DatagramSocket sock, JSONObject obj) throws IOException {
      byte[] data = obj.toString().getBytes();

      DatagramPacket packet = new DatagramPacket(data, data.length, sock.getInetAddress(), sock.getPort());
      sock.send(packet);
    }



  }


  public enum Relation {
    CUST,
    PEER,
    PROV;

    public static Relation of(String type){
      return Relation.valueOf(type.toUpperCase(Locale.ROOT));
    }

  }




}
