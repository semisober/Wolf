package wolf.model.role;

import java.util.List;

import wolf.action.Action;
import wolf.action.Visibility;
import wolf.model.Player;
import wolf.model.stage.GameStage;

import com.google.common.collect.ImmutableList;

public class Priest extends AbstractRole {

  private Player protectTarget;

  @Override
  public void onNightBegins() {
    protectTarget = null;

    getBot().sendMessage(getPlayer().getName(),
        "Who do you want to protect?  Message me !protect <target>");
  }

  @Override
  public boolean isFinishedWithNightAction() {
    return protectTarget != null;
  }

  @Override
  public List<Action> getNightActions() {
    return ImmutableList.<Action>of(protectAction);
  }

  public Player getProtectTarget() {
    return protectTarget;
  }

  private Action protectAction = new Action("protect", "target") {
    @Override
    protected void execute(Player invoker, List<String> args) {
      GameStage stage = Priest.this.getStage();

      if (protectTarget != null) {
        stage.getBot().sendMessage(invoker.getName(), "You've can't protect twice in one night!");
        return;
      }

      protectTarget = stage.getPlayer(args.get(0));
      stage.getBot().sendMessage(invoker.getName(),
          "Your wish to protect " + protectTarget + " has been received.");
    }

    @Override
    public String getDescription() {
      return "Protects the target from being killed by wolves tonight.";
    }

    @Override
    public Visibility getVisibility() {
      return Visibility.PRIVATE;
    };
  };

}
