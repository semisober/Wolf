package wolf;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.log4j.BasicConfigurator;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;
import wolf.action.BotAction;
import wolf.action.InitGameAction;
import wolf.action.ShutdownAction;
import wolf.engine.Player;

public class WolfBot extends PircBot {

  public static final ImmutableList<String> admins = ImmutableList.of("satnam", "semisober",
      "tomm", "leesharpe");
  public static final List<BotAction> actions = Lists.<BotAction>newArrayList(new InitGameAction(),
      new ShutdownAction());

  private static final boolean test = true;

  public static final String channel = test ? "#wolftest" : "#mtgwolf";

  public static final List<String> botNames = Lists.<String>newArrayList("Narrator", "Storytell",
      "Overseer");

  private GameHandler currentHandler = null;

  public void transition(GameHandler nextHandler) {
    this.currentHandler = nextHandler;
  }

  public GameHandler getHandler() {
    return currentHandler;
  }

  @Override
  public void onMessage(String channel, String sender, String login, String hostname, String message) {
    try {
      if (currentHandler != null) {
        currentHandler.onMessage(this, channel, sender, login, hostname, message);
        return;
      }
      handleMessage(this, actions, channel, sender, message);
    } catch (RuntimeException e) {
      if (!(e instanceof WolfException)) {
        e.printStackTrace();
      }
      sendMessage("Problem: " + e.getMessage());
    }
  }

  @Override
  public void onPrivateMessage(String sender, String login, String hostname, String message) {
    if (admins.contains(sender)) {
      if (message.startsWith("!opme")) {
        op(channel, sender);
        return;
      }
    }

    try {
      if (currentHandler != null) {
        currentHandler.onPrivateMessage(this, sender, login, hostname, message);
        return;
      }
      handleMessage(this, actions, null, sender, message);
    } catch (Exception e) {
      if (!(e instanceof WolfException)) {
        e.printStackTrace();
      }
      sendMessage(sender, "Problem: " + e.getMessage());
    }
  }

  @Override
  protected void onPart(String channel, String sender, String login, String hostname) {
    try {
      if (currentHandler != null) {
        currentHandler.onPart(this, channel, sender, login, hostname);
        return;
      }
    } catch (RuntimeException e) {
      if (!(e instanceof WolfException)) {
        e.printStackTrace();
      }
      sendMessage("Problem: " + e.getMessage());
    }
  }

  @Override
  protected void onQuit(String channel, String sender, String login, String hostname) {
    onPart(channel, sender, login, hostname);
  }

  public static void handleMessage(WolfBot bot, Collection<? extends BotAction> possibleActions,
      String channel, String sender, String message) {
    List<String> m = Lists.newArrayList(Splitter.on(' ').split(message));
    String command = m.get(0);

    if (!command.startsWith("!") || command.startsWith("!!")) {
      return;
    }

    command = command.substring(1);

    BotAction targetAction = null;
    for (BotAction action : possibleActions) {
      if (action.matches(command)) {
        targetAction = action;
        break;
      }
    }

    if (targetAction != null) {
      targetAction.tryInvoke(bot, sender, command, m.subList(1, m.size()));
    } else {
      StringBuilder possibleCommands = new StringBuilder();
      for (BotAction action : possibleActions) {
        possibleCommands.append('!').append(action.getCommandName()).append(", ");
      }
      if (!possibleActions.isEmpty()) {
        possibleCommands.delete(possibleCommands.length() - 2, possibleCommands.length());
      }
      throw new WolfException("Unrecognized command: !" + command + ".  Possible commands are: "
          + possibleCommands);
    }
  }

  public void deVoiceAll() {
    for (User user : getUsers(channel)) {
      if (user.hasVoice()) deVoice(channel, user.getNick());
    }
  }

  /**
   * Sends a message to everyone in the wolf channel.
   */
  public void sendMessage(String message) {
    super.sendMessage(channel, message);
  }

  public void sendMessage(Player player, String message) {
    super.sendMessage(player.getName(), message);
  }

  public User getUser(String nick) {
    for (User user : getUsers(channel)) {
      if (user.getNick().equalsIgnoreCase(nick)) {
        return user;
      }
    }
    return null;
  }

  public WolfBot() throws Exception {
    setName(botNames.get(0));
    setLogin(getName());

    setVerbose(true);
    startIdentServer();

    connect("irc.colosolutions.net");
    joinChannel(channel);
    sendMessage("Gleemax", "op stanford");
  }

  public static void main(String[] args) throws Exception {
    BasicConfigurator.configure();

    new WolfBot();
  }

}
