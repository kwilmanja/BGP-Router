import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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


}

class NetUtil {

  public static JSONObject receiveMessage(DatagramChannel dc) throws IOException, JSONException {
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


class Router {

  private final int asn;

  private final Selector selector = Selector.open();

  private final Map<String, DatagramChannel> channels = new HashMap<>();
  private final Map<String, String> relations = new HashMap<>();
  private final ArrayList<String> networks = new ArrayList<>();

  private RoutingTable routeTable;

  public Router(int asn, ArrayList<String> routerStrings) throws Exception {

    this.asn = asn;
    this.routeTable = new RoutingTable();

    for (String routerString: routerStrings){
      String[] strs = routerString.split("-");

      String ip = strs[1];
      int port = Integer.parseInt(strs[0]);

      this.relations.put(ip, strs[2]);
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
          JSONObject received = NetUtil.receiveMessage(dc);

          switch (received.getString("type")){
            case "update":
            case "withdraw":
              this.forwardUpdateWithdraw(received, dc);
              String ip = this.getIPFromChannel(dc);
              received.put("peerRelation", this.relations.get(ip));
              this.routeTable.addMessage(received);
              break;
            case "data":
              this.forwardData(received, dc);
              break;
            case "dump":
              this.handleDump(dc);
              break;
            default:
              throw new Exception("Message type not valid!");
          }

        }

        keyIterator.remove();
      }
    }
  }

  private boolean isCust(String ip){
    return this.relations.get(ip).equals("cust");
  }

  //ToDo: Check if it is a legal route to send on
  private void forwardData(JSONObject received, DatagramChannel receivedOn) throws Exception {
    JSONObject toSend;
    DatagramChannel sendOn;

    String dstIP = received.getString("dst");
    Optional<String> peerIP = this.routeTable.query(dstIP, isCust(this.getIPFromChannel(receivedOn)));

    if(peerIP.isPresent()){
      // a route was found to forward the data
      toSend = received;
      sendOn = this.channels.get(peerIP.get());
    } else {
      // no route was found to forward the data
      toSend = new JSONObject();
      toSend.put("type", "no route");
      toSend.put("src", received.getString("src"));
      toSend.put("dst", received.getString("dst"));
      toSend.put("msg", new JSONObject());
      sendOn = receivedOn;
    }

    NetUtil.sendMessage(toSend, sendOn);
  }

  private void handleDump(DatagramChannel dc) throws Exception {
    JSONObject toSend = new JSONObject();
    toSend.put("type", "table");

    String neighborIP = this.getIPFromChannel(dc);
    toSend.put("src", this.getOurIP(neighborIP));
    toSend.put("dst", neighborIP);

    JSONArray msg = this.routeTable.getTableJSON();
    toSend.put("msg", msg);

    NetUtil.sendMessage(toSend, dc);
  }

  private void forwardUpdateWithdraw(JSONObject update, DatagramChannel dc) throws Exception {

    //Build Forward Message:
    JSONObject toSend = new JSONObject();

    if(update.getString("type").equals("update")){
      toSend.put("type", "update");
      JSONObject msgReceived = update.getJSONObject("msg");

      JSONObject msgSend = new JSONObject();
      msgSend.put("network", msgReceived.getString("network"));
      msgSend.put("netmask", msgReceived.getString("netmask"));

      JSONArray asPath = new JSONArray();
      asPath.put(this.asn);
      JSONArray oldASPath = msgReceived.getJSONArray("ASPath");
      for(int i=0; i<oldASPath.length(); i++){
        asPath.put(oldASPath.get(i));
      }

      msgSend.put("ASPath", asPath);
      toSend.put("msg", msgSend);

    } else if(update.getString("type").equals("withdraw")){
      toSend.put("type", "withdraw");
      toSend.put("msg", update.getJSONArray("msg"));
    }



    //Send Forward Message:

    String originIP = this.getIPFromChannel(dc);
    String originR = this.relations.get(originIP);

    for(String ip : this.networks){

      String r = this.relations.get(ip);
      DatagramChannel sendOn = this.channels.get(ip);

      if (!ip.equals(originIP) &&
              (originR.equals("cust") || r.equals("cust"))){
        toSend.put("src", this.getOurIP(ip));
        toSend.put("dst", ip);
        NetUtil.sendMessage(toSend, sendOn);
      }
    }



  }

}



class RoutingTable{

  public final ArrayList<Route> routes = new ArrayList<>();
  public final ArrayList<JSONObject> messages = new ArrayList<>();

  public RoutingTable(){}

  public void addMessage(JSONObject received) throws Exception {
    this.messages.add(received);
    this.buildRoutes();
  }

  private void buildRoutes() throws Exception {
    this.routes.clear();
    for(JSONObject msg : this.messages){
      this.processMessage(msg);
    }
    this.aggregate();
  }

  private void aggregate(){
    //ToDo
  }

  public void processMessage(JSONObject received) throws Exception {
    if(received.getString("type").equals("update")){
      this.routes.add(new Route(received));
    } else if(received.getString("type").equals("withdraw")){
      this.handleRouteWithdraw(received);
    } else{
      throw new Exception("message not an update or withdraw");
    }
  }

  private void handleRouteWithdraw(JSONObject received) throws JSONException {
    String peer = received.getString("src");
    JSONArray msg = received.getJSONArray("msg");
    for(int i=0; i<msg.length(); i++){
      JSONObject obj = msg.getJSONObject(i);
      String network = obj.getString("network");
      String netmask = obj.getString("netmask");

      ArrayList<Route> toRemove = new ArrayList<>();

      for(Route r : this.routes){
        if(r.peer.equals(peer) && r.network.equals(network) && r.netmask.equals(netmask)){
          toRemove.add(r);
        }
      }

      this.routes.removeAll(toRemove);
    }
  }

  public JSONArray getTableJSON() throws JSONException {
    JSONArray ja = new JSONArray();
    for(Route r : this.routes){
      ja.put(r.asJSON());
    }
    return ja;
  }

  //ToDo: Better algo for choosing route
  public Optional<String> query(String dstIP, boolean fromCust){
    ArrayList<Route> matches = new ArrayList<>();

    int bestNetmask = 0;

    for(Route r : this.routes){
      if(r.containsNetwork(dstIP) && (fromCust || r.peerRelation.equals("cust"))){
        int rNetmask = r.getNetmaskInt();
        if(rNetmask == bestNetmask){
          matches.add(r);
        } else if (rNetmask > bestNetmask){
          matches = new ArrayList<>();
          matches.add(r);
          bestNetmask = rNetmask;
        }
      }
    }




    //return if 0 or 1 matches
    if(matches.isEmpty()){
      return Optional.empty();
    } else if(matches.size() == 1){
      return Optional.of(matches.get(0).peer);
    }


    ArrayList<Route> bestMatch = new ArrayList<>();


    //LOCAL PREF
    int bestLocalPref = 0;
    for(Route r : matches){
      if(r.localPref == bestLocalPref){
        bestMatch.add(r);
      } else if (r.localPref > bestLocalPref){
        bestMatch = new ArrayList<>();
        bestMatch.add(r);
        bestLocalPref = r.localPref;
      }
    }

    if(bestMatch.size() == 1){
      return Optional.of(bestMatch.get(0).peer);
    } else if (bestMatch.size() > 1){
      matches = new ArrayList<>(bestMatch);
    }

    bestMatch = new ArrayList<>();

    //SELF ORIGIN
    for(Route r : matches){
      if(r.selfOrigin){
        bestMatch.add(r);
      }
    }

    if(bestMatch.size() == 1){
      return Optional.of(bestMatch.get(0).peer);
    } else if (bestMatch.size() > 1){
      matches = new ArrayList<>(bestMatch);
    }

    bestMatch = new ArrayList<>();

    // ASPath
    int shortestASPath = matches.get(0).asPath.size();

    for(Route r : matches) {
      int currASPathSize = r.asPath.size();
      if (currASPathSize < shortestASPath) {
        shortestASPath = currASPathSize;
        bestMatch = new ArrayList<>();
        bestMatch.add(r);
      } else if (currASPathSize == shortestASPath) {
        bestMatch.add(r);
      }
    }

    if(bestMatch.size() == 1){
      return Optional.of(bestMatch.get(0).peer);
    } else if (bestMatch.size() > 1){
      matches = new ArrayList<>(bestMatch);
    }

    // Origin
    String bestOrigin = "UNK";
    for(Route r : matches){
      if(r.origin == bestOrigin){
        bestMatch.add(r);
      } else if (r.origin == "IGP") {
        bestMatch = new ArrayList<>();
        bestMatch.add(r);
        bestOrigin = "IGP";
      } else if (bestOrigin != "IGP" && r.origin == "EGP") {
        bestMatch = new ArrayList<>();
        bestMatch.add(r);
        bestOrigin = "EGP";
      }
    }

    if(bestMatch.size() == 1){
      return Optional.of(bestMatch.get(0).peer);
    } else if (bestMatch.size() > 1){
      matches = new ArrayList<>(bestMatch);
    }

    bestMatch = new ArrayList<>();

    // LowestIP
    long lowestIP = Long.parseLong(IPAddress.ipAddressToBinary(matches.get(0).peer), 2);

    for(Route r : matches){
      long currIP = Long.parseLong(IPAddress.ipAddressToBinary(r.peer), 2);
      if(currIP < lowestIP || currIP == lowestIP){
        bestMatch = new ArrayList<>();
        bestMatch.add(r);
        lowestIP = currIP;
      }
    }

    if(bestMatch.size() == 1){
      return Optional.of(bestMatch.get(0).peer);
    }

    return Optional.empty();
  }

}

class Route{

  public String network;
  public String netmask;
  public String peer;
  public int localPref;
  public boolean selfOrigin;
  public ArrayList<Integer> asPath;
  public String origin;
  public String peerRelation;

  public Route(String network, String netmask, String peer,
               int localPref, boolean selfOrigin, ArrayList<Integer> asPath, String origin){
    this.network = network;
    this.netmask = netmask;
    this.peer = peer;
    this.localPref = localPref;
    this.selfOrigin = selfOrigin;
    this.asPath = asPath;
    this.origin = origin;
  }

  public Route(JSONObject update) throws JSONException {
    JSONObject msg = update.getJSONObject("msg");

    this.network = msg.getString("network");
    this.netmask = msg.getString("netmask");
    this.peer = update.getString("src");
    this.peerRelation = update.getString("peerRelation");
    this.localPref = msg.getInt("localpref");
    this.selfOrigin = msg.getBoolean("selfOrigin");
    this.asPath = new ArrayList<>();
    JSONArray oldASPath = msg.getJSONArray("ASPath");
    for(int i=0; i<oldASPath.length(); i++){
      this.asPath.add(oldASPath.getInt(i));
    }
    this.origin = msg.getString("origin");
  }

  public JSONObject asJSON() throws JSONException {
    JSONObject route = new JSONObject();
    route.put("network", this.network);
    route.put("netmask", this.netmask);
    route.put("peer", this.peer);
    route.put("localpref", this.localPref);
    route.put("ASPath", new JSONArray(this.asPath));
    route.put("selfOrigin", this.selfOrigin);
    route.put("origin", this.origin);
    return route;
  }

  public boolean containsNetwork(String dstIP){
    int nm = IPAddress.netmaskToInt(this.netmask);
    String binaryNetwork = IPAddress.ipAddressToBinary(this.network);
    String binaryDst = IPAddress.ipAddressToBinary(dstIP);
    return binaryNetwork.startsWith(binaryDst.substring(0, nm));
  }

  public int getNetmaskInt(){
    return IPAddress.netmaskToInt(this.netmask);
  }

}

class IPAddress{

  // 255.255.255.255 -> 32
  public static int netmaskToInt(String netmask){
    int result = 0;
    String[] netmaskSplit = netmask.split("\\.");
    for(int i=0; i<netmaskSplit.length; i++){
      int n = Integer.parseInt(netmaskSplit[i]);
      String bn = Integer.toBinaryString(n);
      while(bn.startsWith("1")){
        result++;
        bn = bn.substring(1);
      }
    }
    return result;
  }

  //ToDo
  // 255.255.255.255 -> 11111111111111111111111111111111
  public static String ipAddressToBinary(String network) {

    String[] parts = network.split("\\.");

    if (parts.length != 4) {
      throw new IllegalArgumentException("Invalid IP format" + network);
    }

    StringBuilder binaryIP = new StringBuilder();

    for (String part : parts) {
      int value = Integer.parseInt(part);
      if (value < 0 || value > 255) {
        throw new IllegalArgumentException("Invalid IP value");
      }

      // Convert the value to an 8-bit binary representation
      String binaryPart = String.format("%8s", Integer.toBinaryString(value)).replace(' ', '0');
      binaryIP.append(binaryPart);
    }

    return binaryIP.toString();
  }


}