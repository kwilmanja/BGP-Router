import org.json.JSONException;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;

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





}
