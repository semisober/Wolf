package wolf.action.game;

import wolf.action.BotAction;
import wolf.engine.WolfEngine;

public abstract class AbstractGameAction extends BotAction {

  private WolfEngine engine;

  public AbstractGameAction() {
    super();
  }

  public AbstractGameAction(int numArgs) {
    super(numArgs);
  }

  public void setEngine(WolfEngine engine) {
    this.engine = engine;
  }

  public WolfEngine getEngine() {
    return engine;
  }

}
