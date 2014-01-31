package wolf.action.setup;

import java.util.List;

import wolf.WolfException;
import wolf.model.GameModel;
import wolf.model.GameSetupStage;
import wolf.model.Player;

public class LeaveAction extends SetupAction {

  public LeaveAction(GameSetupStage stage) {
    super(stage, "leave", 0);
  }

  @Override
  protected void execute(GameModel model, Player invoker, List<String> args) {
    boolean added = getStage().getPlayers().add(invoker);
    if (added) {
      throw new WolfException(invoker.getName() + " not in game!");
    }
    model.getBot().sendMessage(invoker.getName() + " left the game.");
  }
  
  @Override
  public String getDescription() {
    return "Leaves the game.";
  }

}