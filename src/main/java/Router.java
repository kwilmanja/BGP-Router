import java.io.*;
import java.net.*;
import java.util.*;

public class Router {

  private Map<String, DatagramSocket> sockets = new HashMap<>();
  private Map<String, Integer> ports = new HashMap<>();
  private Map<String, String> relations = new HashMap<>();
  private int asn;

  public Router(int asn, List<String> connections) {
    this.asn = asn;
    System.out.println("Router at AS " + asn + " starting up");
    for (String relationship : connections) {
      String[] parts = relationship.split("-");
      String port = parts[0];
      String neighbor = parts[1];
      String relation = parts[2];

      try {
        DatagramSocket socket = new DatagramSocket();
        sockets.put(neighbor, socket);
        ports.put(neighbor, Integer.parseInt(port));
        relations.put(neighbor, relation);

        InetAddress localhost = InetAddress.getByName("localhost");
        byte[] sendData = ("{ \"type\": \"handshake\", \"src\": " + ourAddr(neighbor) + ", \"dst\": " + neighbor + ", \"msg\": {}  }").getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, localhost, ports.get(neighbor));
        socket.send(sendPacket);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public String ourAddr(String dst) {
    String[] quads = dst.split("\\.");
    quads[3] = "1";
    return quads[0] + "." + quads[1] + "." + quads[2] + "." + quads[3];
  }

  public void send(String network, String message) {
    try {
      byte[] sendData = message.getBytes();
      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("localhost"), ports.get(network));
      sockets.get(network).send(sendPacket);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void run() {
    while (true) {
      List<DatagramSocket> readySockets = new ArrayList<>();
      for (DatagramSocket socket : sockets.values()) {
        if (socket.isBound()) {
          readySockets.add(socket);
        }
      }
      DatagramSocket[] socketArray = readySockets.toArray(new DatagramSocket[0]);

      if (socketArray.length == 0) {
        continue;
      }

      byte[] receiveData = new byte[65535];
      DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

      try {
        for (DatagramSocket conn : socketArray) {
          conn.receive(receivePacket);
          String srcif = null;
          for (Map.Entry<String, DatagramSocket> entry : sockets.entrySet()) {
            if (entry.getValue().equals(conn)) {
              srcif = entry.getKey();
              break;
            }
          }
          String msg = new String(receivePacket.getData(), 0, receivePacket.getLength());

          System.out.println("Received message '" + msg + "' from " + srcif);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println("Usage: java Router <asn> <connection1> <connection2> ...");
      System.exit(1);
    }

    int asn = Integer.parseInt(args[0]);
    List<String> connections = Arrays.asList(Arrays.copyOfRange(args, 1, args.length));
    Router router = new Router(asn, connections);
    router.run();
  }
}
