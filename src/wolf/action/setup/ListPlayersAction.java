package wolf.action.setup;

import java.util.List;

import wolf.model.SetupStage;
import wolf.model.Player;

public class ListPlayersAction extends SetupAction {

  public ListPlayersAction(SetupStage stage) {
    super(stage, "players", 0);
  }

  @Override
  protected void execute(Player invoker, List<String> args) {
    String s = getStage().getPlayers().toString();
    s = s.substring(1, s.length() - 1); // get rid of the braces
    getBot().sendMessage(getStage().getPlayers().size() + " Players: " + s);
  }

}
