package wolf.action.setup;

import java.util.List;

import wolf.WolfException;
import wolf.model.SetupStage;
import wolf.model.Player;

public class JoinAction extends SetupAction {

  public JoinAction(SetupStage stage) {
    super(stage, "join", 0);
  }

  @Override
  protected void execute(Player invoker, List<String> args) {
    boolean added = getStage().getPlayers().add(invoker);
    if (!added) {
      throw new WolfException(invoker.getName() + " already joined!");
    }
    getBot().sendMessage(invoker.getName() + " joined the game.");
  }

  @Override
  public String getDescription() {
    return "Joins the game.";
  }

}