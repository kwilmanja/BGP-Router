import java.net.Socket;

public class Router {

  public RouterType type;
  public Socket sock;

  public Router(String routerString){
    this.type = RouterType.of(routerString.substring(routerString.length() - 4));
    int port = Integer.parseInt(routerString.substring(0, 4));
    String ip = routerString.substring(5, routerString.length() - 5);
    System.out.println(type + " " + port + " " + ip);
  }


}
