import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Main {




  public static void main(String[] args) throws Exception {
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

    private final Selector selector = Selector.open();

    private final Map<String, DatagramChannel> channels = new HashMap<>();
    private final Map<String, Relation> relations = new HashMap<>();
    private final ArrayList<String> networks = new ArrayList<>();

    public Router(int asn, ArrayList<String> routerStrings) throws Exception {

      this.asn = asn;

      for (String routerString: routerStrings){
        String[] strs = routerString.split("-");

        String ip = strs[1];
        int port = Integer.parseInt(strs[0]);

        this.relations.put(ip, Relation.of(strs[2]));
        DatagramChannel dc = DatagramChannel.open();
        dc.connect(new InetSocketAddress(InetAddress.getByName("localhost"), port));
        this.channels.put(ip, dc);
        this.networks.add(ip);

      }

      this.sendHandshakes();
      this.registerDataChannelSelector();
    }


    private void registerDataChannelSelector() throws IOException {
      for(DatagramChannel dc : this.channels.values()){
        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ);
      }
    }

    private void sendHandshakes() throws JSONException, IOException {
      for(String ip : this.networks){
        JSONObject handshake = new JSONObject();
        handshake.put("src", this.getOurIP(ip));
        handshake.put("dst", ip);
        handshake.put("type", "handshake");
        handshake.put("msg", new JSONObject());

        NetUtil.sendMessage(handshake, this.channels.get(ip));
      }
    }

    public String getOurIP(String ip){
      return ip.substring(0, ip.length()-1) + "1";
    }

    public String getIPFromChannel(DatagramChannel dc) throws Exception {
      for(Map.Entry<String, DatagramChannel> e : this.channels.entrySet()){
        if(e.getValue().equals(dc)){
          return e.getKey();
        }
      }
      throw new Exception("Could not find key for channel!");
    }


    public void run() throws Exception {

      while(true){


        int readyChannels = selector.select();
        if (readyChannels == 0) {
          continue;
        }

        Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

        while (keyIterator.hasNext()) {
          SelectionKey selectedKey = keyIterator.next();

          if (selectedKey.isReadable()) {
            DatagramChannel dc = (DatagramChannel) selectedKey.channel();
            JSONObject recieved = NetUtil.recieveMessage(dc);

            switch (recieved.getString("type")){
              case "update":
                this.handleUpdate(recieved, dc);
                break;
              case "withdraw":
                break;
              case "data":
                break;
              case "no route":
                break;
              case "dump":
                break;
              default:
                throw new Exception("Message type not valid!");
            }

          }

          keyIterator.remove();
        }
      }
    }

    private void handleUpdate(JSONObject update, DatagramChannel dc) throws Exception {

      //Save a Copy:

      //Update Routing Table:

      //Build Forward Message:

      if(update.isNull("localpref")){
        throw new Exception("Failed");
      }

      JSONObject toSend = new JSONObject();
      toSend.put("type", "update");
      JSONObject msg = new JSONObject(update);
      msg.remove("localpref");
      msg.remove("selfOrigin");
      msg.remove("origin");
      toSend.put("msg", msg);


      //Send Forward Message:

      String originIP = this.getIPFromChannel(dc);
      Relation originR = this.relations.get(originIP);

      for(String ip : this.networks){

        Relation r = this.relations.get(ip);
        DatagramChannel sendOn = this.channels.get(ip);

        if (!ip.equals(originIP) &&
                (originR.equals(Relation.CUST) || r.equals(Relation.CUST))){
          toSend.put("src", this.getOurIP(ip));
          toSend.put("dst", ip);
          NetUtil.sendMessage(toSend, sendOn);
        }
      }


    }




  }










  public static class NetUtil {

    public static JSONObject recieveMessage(DatagramChannel dc) throws IOException, JSONException {
      ByteBuffer buffer = ByteBuffer.allocate(1024);
      buffer.clear();

      SocketAddress senderAddress = dc.receive(buffer);

      buffer.flip();
      byte[] data = new byte[buffer.limit()];
      buffer.get(data);

      String messageStr = new String(data, StandardCharsets.UTF_8);
      System.out.println("Received from " + senderAddress + ": " + messageStr);
      return new JSONObject(messageStr);
    }

    public static void sendMessage(JSONObject msg, DatagramChannel dc) throws IOException {
      byte[] messageBytes = msg.toString().getBytes(StandardCharsets.UTF_8);
      ByteBuffer buffer = ByteBuffer.wrap(messageBytes);
      dc.write(buffer);
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
