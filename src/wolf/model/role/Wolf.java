package wolf.model.role;

import java.util.List;

import wolf.action.Action;
import wolf.action.Visibility;
import wolf.model.Player;
import wolf.model.Role;
import wolf.model.stage.GameStage;

import com.google.common.collect.ImmutableList;

public class Wolf extends AbstractRole {

  private Player killTarget;

  @Override
  public void onGameStart() {
    getBot().sendMessage(getPlayer().getName(),
        "The wolves are: " + getStage().getPlayers(Role.WOLF));
  }

  @Override
  public void onNightBegins() {
    killTarget = null;
    getBot().sendMessage(getPlayer().getName(),
        "Who do you want to kill?  Message me !kill <target>");
  }

  @Override
  public boolean isFinishedWithNightAction() {
    return killTarget != null;
  }

  @Override
  public List<Action> getNightActions() {
    return ImmutableList.<Action>of(killAction);
  }
  
  @Override
  public Player getTarget() {
    return killTarget;
  }

  @Override
  public void handleChat(Player sender, String message, boolean isPrivate) {
    if (getStage().isNight() && isPrivate) {
      // wolf-chat
      wolfChat(sender, message);
    }
  }

  public void wolfChat(Player sender, String message) {
    for (Player wolf : getStage().getPlayers(Role.WOLF)) {
      if (wolf != sender) {
        getBot().sendMessage(wolf.getName(), "<WolfChat> " + sender + ": " + message);
      }
    }
  }

  @Override
  public String getDescription() {
    return "The Wolves kill a villager every night. They win when their numbers equal those of the villagers.";
  }

  private Action killAction = new Action("kill", "target") {
    @Override
    protected void execute(Player invoker, List<String> args) {
      GameStage stage = Wolf.this.getStage();

      killTarget = stage.getPlayer(args.get(0));

      wolfChat(invoker, invoker + " votes to kill " + killTarget);

      stage.getBot().sendMessage(invoker.getName(),
          "Your wish to kill " + killTarget + " has been received.");
    }

    @Override
    public String getDescription() {
      return "Feast on their flesh! The target will not awaken in the morning...";
    }

    @Override
    public Visibility getVisibility() {
      return Visibility.PRIVATE;
    };
  };

}
