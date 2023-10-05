import java.util.ArrayList;

public class Main {




  public static void main(String[] args){
    System.out.println("Hello Andrew Panzone");
    int asn = Integer.parseInt(args[0]);

    ArrayList<Router> routers = new ArrayList<>();
    for(int i=1; i<args.length; i++){
      routers.add(new Router(args[i]));
    }




  }





}
