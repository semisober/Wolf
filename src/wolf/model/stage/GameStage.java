package wolf.model.stage;

import static com.google.common.collect.Iterables.filter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;

import wolf.WolfException;
import wolf.action.Action;
import wolf.action.game.ClearVoteAction;
import wolf.action.game.GetRoleAction;
import wolf.action.game.ListPlayersAction;
import wolf.action.game.VoteAction;
import wolf.action.game.VoteCountAction;
import wolf.action.game.admin.GetVotersAction;
import wolf.action.game.admin.ModkillPlayerAction;
import wolf.action.game.host.AbortGameAction;
import wolf.action.game.host.AnnounceAction;
import wolf.action.game.host.ReminderAction;
import wolf.action.global.GetHelpAction;
import wolf.action.privatechats.AuthorizePlayerAction;
import wolf.action.privatechats.ChatAction;
import wolf.action.privatechats.JoinRoomAction;
import wolf.action.privatechats.LeaveRoomAction;
import wolf.action.privatechats.ListRoomsAction;
import wolf.action.privatechats.NewRoomAction;
import wolf.action.privatechats.RevokeAuthorizationAction;
import wolf.bot.IBot;
import wolf.model.Faction;
import wolf.model.GameConfig;
import wolf.model.GameSummary;
import wolf.model.Player;
import wolf.model.Role;
import wolf.model.VotingHistory;
import wolf.model.chat.ChatServer;
import wolf.model.role.AbstractWolfRole;
import wolf.model.role.Corrupter;
import wolf.model.role.Demon;
import wolf.model.role.Priest;
import wolf.model.role.Vigilante;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;

public class GameStage extends Stage {

  public static final String NONE_DEAD_MSG =
      "The sun dawns and the village finds that no one has died in the night.";

  private final UUID id = UUID.randomUUID();

  private final GetHelpAction getHelpAction = new GetHelpAction(this);

  private final List<Action> daytimeActions = Lists.newArrayList();

  private final List<Action> hostActions = Lists.newArrayList();
  private final List<Action> adminActions = Lists.newArrayList();

  private final List<Action> chatActions = Lists.newArrayList();

  private final VotingHistory votingHistory = new VotingHistory();

  private final Map<Player, Player> votesToDayKill = Maps.newLinkedHashMap();
  private final List<Multimap<Player, Player>> killHistory = Lists.newArrayList();
  private ChatServer server;

  /**
   * The set of all players (even dead ones).
   */
  private final Set<Player> players;

  private boolean daytime = true;

  private final GameConfig config;

  /**
   * This is stored as part of the GameHistory.
   */
  private final DateTime startDate = new DateTime();


  public GameStage(IBot bot, GameConfig config, Set<Player> players) {
    super(bot);

    this.config = config;
    this.players = ImmutableSortedSet.copyOf(players);

    server = new ChatServer(bot);

    daytimeActions.add(getHelpAction);
    daytimeActions.add(new VoteAction(this));
    daytimeActions.add(new VoteCountAction(this));
    if (config.getSettings().get("WITHDRAW_VOTES").equals("YES")) {
      daytimeActions.add(new ClearVoteAction(this));
    }
    daytimeActions.add(new ListPlayersAction(this));
    daytimeActions.add(new GetRoleAction(this));

    adminActions.add(new ModkillPlayerAction(this));
    adminActions.add(new GetVotersAction(this));

    hostActions.add(new AnnounceAction(this));
    hostActions.add(new AbortGameAction(this));
    hostActions.add(new ReminderAction(this));

    chatActions.add(new AuthorizePlayerAction(server));
    chatActions.add(new ChatAction(server));
    chatActions.add(new JoinRoomAction(server));
    chatActions.add(new LeaveRoomAction(server));
    chatActions.add(new ListRoomsAction(server));
    chatActions.add(new NewRoomAction(server));
    chatActions.add(new RevokeAuthorizationAction(server));

    for (Player player : players) {
      player.getRole().setStage(this);
    }

    try {
      beginGame();
    } catch (Exception e) {
      e.printStackTrace();
      bot.sendMessage("There was a server error when initializing the game!");
      bot.setStage(new InitialStage(getBot()));
      return;
    }
  }

  private void beginGame() {
    getBot().sendMessage(
            "If this is your first game, please read the rules link up above. You can use /status to see what roles are in the game.");
    getBot().sendMessage(
        "Please do NOT copy/paste any text from the moderator (bold and green) as it is private.");
    getBot().sendMessage("You can use the command /help at any time for more assistance.");

    for (Player player : getPlayers()) {
      player.getRole().onGameStart();
    }

    unmutePlayers();

    getBot().sendMessage("Day 1 dawns on the village.");
  }

  private void unmutePlayers() {
    if (config.getSettings().get("SILENT_GAME").equals("ENABLED")) {
      return;
    }
    for (Player player : getPlayers()) {
      getBot().unmute(player.getName());
    }
  }

  @Override
  public void handle(IBot bot, String sender, String command, List<String> args) {
    super.handle(bot, sender, command, args);

    if (isNight()) {
      checkForEndOfNight();
    }
  }

  private void checkForEndOfNight() {
    for (Player player : getPlayers()) {
      if (!player.getRole().isFinishedWithNightAction()) {
        return;
      }
    }

    // bring night to a close.
    moveToDay();
  }

  private void moveToDay() {
    // Get anyone who needs to die into the killMap. Upon being added to dying,
    // they must later die so any protection needs to be triggered beforehand.
    // Keys are victims, Values are killers.
    Multimap<Player, Player> killMap = TreeMultimap.create();

    if (!getPlayers(Faction.WOLVES).isEmpty()) {
      List<Player> targets = Lists.newArrayList();
      for (Player p : getPlayers(Faction.WOLVES)) {
        AbstractWolfRole wolf = (AbstractWolfRole) p.getRole();
        targets.add(wolf.getKillTarget());
      }

      if (targets.contains(null)) {
        // wolves haven't finished choosing yet.
        return;
      }

      // need to change this to majority from random choice.
      Player target = targets.get((int) (Math.random() * targets.size()));
      if (!isProtected(target)) {
        for (Player p : getPlayers(Faction.WOLVES)) {
          killMap.put(target, p);
        }
      }
    }

    for (Player p : getPlayers(Role.VIGILANTE)) {
      Vigilante vig = (Vigilante) p.getRole();
      if (isCorrupterTarget(p)) {
        vig.corrupt();
      }
      Player target = vig.getKillTarget();
      if (target != null) {
        if (isProtected(target)) {
          getBot().sendMessage(p.getName(), "Your bullet bounces off of " + target.getName() + ".");
        } else {
          killMap.put(target, p);
          getBot().sendMessage(p.getName(),
              "You shoot " + target.getName() + " square between the eyes.");
        }
      }
    }

    for (Player p : getPlayers(Role.DEMON)) {
      Player target = ((Demon) p.getRole()).getKillTarget();
      if (target != null && !isProtected(target)) {
        killMap.put(target, p);
      }
    }

    // Kill anyone who targets the demon - this ignores protection. May want to settings this later.
    // Clean this up later to not loop through demons.
    for (Player demon : getPlayers(Role.DEMON)) {
      boolean wolfTarget = false;
      for (Player p : getPlayers()) {
        if (p.getRole().getKillTarget() == demon || p.getRole().getSpecialTarget() == demon) {
          if ((p.getRole().getType() == Role.WOLF)) {
            wolfTarget = true;
          } else {
            getBot()
                .sendMessage(p.getName(),
                    "You realize with horror that you've targeted a demon as your soul bleeds from your body.");
            killMap.put(p, demon);
          }
        }
      }
      if (wolfTarget) {
        List<Player> wolves = getPlayers(Role.WOLF);
        Player randomWolf = wolves.get((int) (Math.random() * wolves.size()));
        getBot()
            .sendMessage(randomWolf.getName(),
                "You realize with horror that you've targeted a demon as your soul bleeds from your body.");
        killMap.put(randomWolf, demon);
      }
    }

    // killMap should now have anyone who needs to be killed in it.

    for (Player player : getPlayers()) {
      player.getRole().onNightEnds();
    }

    getBot().sendMessage("The sun dawns upon the village.");
    if (!killMap.isEmpty()) {
      deathNotifications(killMap);

      if (config.getSettings().get("REVEAL_NIGHT_KILLERS").equals("YES")) {
        for (Player p : killMap.keySet()) {
          StringBuilder output = new StringBuilder();
          Player wolfKiller = null;
          output.append("You find that ").append(p.getName()).append(" ");
          for (Player killer : killMap.get(p)) {
            if (killer.getRole().getType() == Role.WOLF) {
              wolfKiller = killer;
            } else {
              output.append(killer.getRole().getKillMessage()).append(" and ");
            }
          }
          if (wolfKiller != null) {
            output.append(wolfKiller.getRole().getKillMessage());
          } else {
            output.setLength(output.length() - 5);
          }
          output.append(".");
          getBot().sendMessage(output.toString());
          p.setAlive(false);
        }
      } else if (config.getSettings().get("REVEAL_NIGHT_KILLERS").equals("NO")) {
        for (Player p : killMap.keySet()) {
          p.setAlive(false);
        }
        StringBuilder output = new StringBuilder();
        output.append("You find that ").append(Joiner.on(" and ").join(killMap.keySet()));
        if (killMap.keySet().size() > 1) {
          output.append(" are dead.");
        } else {
          output.append(" is dead.");
        }
        getBot().sendMessage(output.toString());
      }
    } else {
      getBot().sendMessage(NONE_DEAD_MSG);
    }

    String mode = config.getSettings().get("NIGHT_KILL_ANNOUNCE");
    if (!mode.equals("NONE")) {
      for (Player p : killMap.keySet()) {
        if (mode.equals("FACTION")) {
          getBot().sendMessage(p.getName() + " was a " + p.getRole().getFaction() + ".");
        } else if (mode.equals("ROLE")) {
          getBot().sendMessage(p.getName() + " was a " + p.getRole().getType() + ".");
        }
      }
    }

    killHistory.add(killMap);
    getBot().onPlayersChanged();

    if (checkForWinner() != null) {
      return;
    }

    daytime = true;
    getBot().sendMessage("");
    getBot().sendMessage("*********************");
    getBot().sendMessage("NEW DAY");
    getBot().sendMessage("*********************");
    getBot().sendMessage("");
    unmutePlayers();
  }

  private void deathNotifications(Multimap<Player, Player> killMap) {

    for (Player dead : killMap.keySet()) {
      for (Player killer : killMap.get(dead)) {
        String mode = null;
        if (killer.getRole().getType() == Role.DEMON) {
          mode = config.getSettings().get("TELL_DEMON_ON_KILL");
        } else if (killer.getRole().getType() == Role.WOLF) {
          mode = config.getSettings().get("TELL_WOLVES_ON_KILL");
        } else if (killer.getRole().getType() == Role.VIGILANTE) {
          mode = config.getSettings().get("TELL_VIG_ON_KILL");
        }
        if (mode != null) {
          if (mode.equals("FACTION")) {
            getBot().sendMessage(killer.getName(),
                dead.getName() + " was a " + dead.getRole().getFaction() + ".");
          } else if (mode.equals("ROLE")) {
            getBot().sendMessage(killer.getName(),
                dead.getName() + " was a " + dead.getRole().getType() + ".");
          } else if (mode.equals("NONE")) {}
        }
      }
    }
  }

  private boolean isProtected(Player player) {
    if (player.getRole().getType() == Role.DEMON) {
      return true;
    }
    for (Player p : getPlayers(Role.PRIEST)) {
      if (!isCorrupterTarget(p)) {
        Priest priest = (Priest) p.getRole();
        if (Objects.equal(priest.getSpecialTarget(), player)) {
          return true;
        }
      }
    }
    return false;
  }

  public void moveToNight() {
    daytime = false;

    getBot().muteAll();
    server.clearAllRooms();
    getBot().sendMessage("Night falls on the village.");

    for (Player player : getPlayers()) {
      player.getRole().onNightBegins();
    }
  }

  /**
   * @return The winning faction.
   */
  public Faction checkForWinner() {
    Map<Faction, Integer> factionCount = getFactionCounts();

    int numAlive = getPlayers().size();

    Faction winner = null;

    if (factionCount.get(Faction.VILLAGERS) == numAlive) {
      winner = Faction.VILLAGERS;
    } else if (factionCount.get(Faction.DEMONS) == 0) {
      if (factionCount.get(Faction.WOLVES) >= factionCount.get(Faction.VILLAGERS)) {
        if (getPlayers(Role.HUNTER).isEmpty()) {
          winner = Faction.WOLVES;
        } else {
          winner = Faction.VILLAGERS;
        }
      }
    } else {
      if (factionCount.get(Faction.DEMONS) >= Math.ceil(((double) numAlive) / 2)) {
        winner = Faction.DEMONS;
      }
    }

    if (winner != null) {
      getBot().sendMessage("<h2>The " + winner.getPluralForm() + " have won the game!</h2>");
      GameSummary.printGameLog(getBot(), players, winner, killHistory);
      getBot().setStage(new InitialStage(getBot()));
      getBot().unmuteAll();
      getBot().recordGameResults(this);
    }

    return winner;
  }

  private Map<Faction, Integer> getFactionCounts() {
    Map<Faction, Integer> ret = Maps.newHashMap();

    for (Faction f : Faction.values()) {
      ret.put(f, 0);
    }

    for (Player p : getPlayers()) {
      Integer c = ret.get(p.getRole().getFaction());
      ret.put(p.getRole().getFaction(), c + 1);
    }

    return ret;
  }

  public Map<Player, Player> getVotesToDayKill() {
    return votesToDayKill;
  }

  /**
   * Gets an ALIVE player with the given name.
   */
  @Override
  public Player getPlayer(String name) {
    Player p = getPlayerOrNull(name);

    if (p == null) {
      throw new WolfException("No such player: " + name);
    }

    if (!p.isAlive()) {
      throw new WolfException(name + " is dead.");
    }

    return p;
  }

  /**
   * Gets the player with the given name (alive or dead).
   * 
   * Returns 'null' if that player doesn't exist.
   */
  @Override
  public Player getPlayerOrNull(String name) {
    for (Player p : this.players) {
      if (p.getName().equalsIgnoreCase(name)) {
        return p;
      }
    }
    return null;
  }

  public List<Player> getPlayers(Role role) {
    List<Player> ret = Lists.newArrayList();
    for (Player player : getPlayers()) {
      if (player.getRole().getType() == role) {
        ret.add(player);
      }
    }
    return ret;
  }

  public List<Player> getPlayers(Faction faction) {
    List<Player> ret = Lists.newArrayList();
    for (Player player : getPlayers()) {
      if (player.getRole().getFaction() == faction) {
        ret.add(player);
      }
    }
    return ret;
  }

  /**
   * Returns every ALIVE player.
   */
  public Set<Player> getPlayers() {
    return ImmutableSortedSet.copyOf(filter(players, alive));
  }

  public Set<Player> getDeadPlayers() {
    return ImmutableSortedSet.copyOf(filter(players, Predicates.not(alive)));
  }

  public boolean isCorrupterTarget(Player target) {
    for (Player p : getPlayers(Role.CORRUPTER)) {
      Corrupter corrupter = (Corrupter) p.getRole();
      if (corrupter.getSpecialTarget().equals(target)) {
        return true;
      }
    }
    return false;
  }

  public VotingHistory getVotingHistory() {
    return votingHistory;
  }

  public boolean isDay() {
    return daytime;
  }

  public boolean isNight() {
    return !daytime;
  }

  @Override
  public List<Action> getAvailableActions(Player player) {
    List<Action> actions = Lists.newArrayList();

    if (player.isAdmin()) {
      actions.addAll(hostActions);
      actions.addAll(adminActions);
    } else if (player.equals(config.getHost())) {
      actions.addAll(hostActions);
    }
    if (isDay()) {
      if (config.getSettings().get("PRIVATE_CHAT").equals("ENABLED")) {
        actions.addAll(chatActions);
      }
      actions.addAll(daytimeActions);
    } else {
      List<Action> ret = Lists.newArrayList();
      ret.add(getHelpAction);
      ret.addAll(player.getRole().getNightActions());
      actions.addAll(ret);
    }
    return actions;
  }

  @Override
  public List<Action> getAdminActions() {
    return ImmutableList.copyOf(Iterables.concat(hostActions, adminActions));
  }

  @Override
  public void handleChat(IBot bot, String sender, String message) {
    Player player = getPlayer(sender);

    player.getRole().handleChat(player, message);
  }

  public String getSetting(String settingName) {
    return config.getSettings().get(settingName);
  }

  @Override
  public Iterable<Player> getAllPlayers() {
    return ImmutableList.copyOf(this.players);
  }

  public DateTime getStartDate() {
    return startDate;
  }

  public GameConfig getConfig() {
    return config;
  }

  @Override
  public void setHost(Player newHost) {
    config.setHost(newHost);
    if (config.getHost() == null) {
      getBot().sendMessage("There is now no host.");
    } else {
      getBot().sendMessage(config.getHost() + " is now the host of the game.");
    }
  }

  @Override
  public Player getHost() {
    return config.getHost();
  }

  public UUID getId() {
    return id;
  }

  private static final Predicate<Player> alive = new Predicate<Player>() {
    @Override
    public boolean apply(Player player) {
      return player.isAlive();
    }
  };

}
