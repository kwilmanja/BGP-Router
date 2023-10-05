import java.util.Locale;

public enum RouterType {
  CUST,
  PEER,
  PROV;

  public static RouterType of(String type){
    return RouterType.valueOf(type.toUpperCase(Locale.ROOT));
  }

}

