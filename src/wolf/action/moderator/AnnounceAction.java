package wolf.action.moderator;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import wolf.WolfException;
import wolf.action.Action;
import wolf.model.Player;
import wolf.model.stage.GameStage;

import com.google.common.base.Joiner;

public class AnnounceAction extends Action {

  public AnnounceAction(GameStage stage) {
    super(stage, "announce", "message");
  }

  @Override
  protected void execute(Player invoker, List<String> args) {
    StringBuilder output = new StringBuilder();
    output.append("ANNOUNCEMENT - ").append(Joiner.on(" ").join(args));
    getBot().sendMessage(output.toString());
  }

  @Override
  public void apply(Player invoker, List<String> args) {
    checkNotNull(invoker);
    checkNotNull(args);

    if (requiresAdmin() && !invoker.isAdmin()) {
      throw new WolfException("You must be an admin to do that.");
    }

    execute(invoker, args);
  }

  @Override
  public boolean requiresAdmin() {
    return true;
  }

  @Override
  protected boolean argSizeMatters() {
    return false;
  }

  @Override
  protected boolean onlyIfAlive() {
    return false;
  }

  @Override
  public String getDescription() {
    return "Makes an announcement to the channel.";
  }

}
