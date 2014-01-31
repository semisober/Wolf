package wolf.model;

public class Player {

  private final String name;
  private final boolean admin;

  public Player(String name, boolean admin) {
    this.name = name;
    this.admin = admin;
  }

  public String getName() {
    return name;
  }

  public boolean isAdmin() {
    return admin;
  }

}
