package wolf.bot;

import java.util.List;

import wolf.model.stage.InitialStage;
import wolf.model.stage.Stage;

import com.google.common.collect.Lists;

public class TestBot implements IBot {

  private Stage stage = new InitialStage(this);

  private final List<Message> messageLog = Lists.newArrayList();

  @Override
  public void sendMessage(String message) {
    System.out.println("To ALL: " + message);

    messageLog.add(new Message(null, message));
  }

  @Override
  public void sendMessage(String user, String message) {
    System.out.println("To " + user + ": " + message);

    messageLog.add(new Message(user, message));
  }

  @Override
  public void setStage(Stage stage) {
    this.stage = stage;
  }

  @Override
  public Stage getStage() {
    return stage;
  }

  public void msg(String sender, String message) {
    onMessage(sender, message, false);
  }

  public void privMsg(String sender, String message) {
    onMessage(sender, message, true);
  }

  @Override
  public void onMessage(String sender, String message, boolean isPrivate) {
    NarratorBot.handle(this, sender, message, isPrivate);
  }

  public List<Message> getMessageLog() {
    return messageLog;
  }

  public static final class Message {
    public final String to, message;

    public Message(String to, String message) {
      this.to = to;
      this.message = message;
    }

    @Override
    public String toString() {
      return to + ": " + message;
    }
  }

  @Override
  public void muteAll() {
    System.out.println("Muting all.");
  }

  @Override
  public void unmute(String player) {
    System.out.println("Unmuted " + player);
  }

  @Override
  public void unmuteAll() {
    System.out.println("Unmuting all.");
  }

}
