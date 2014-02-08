package wolf.rankings;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.webbitserver.HttpControl;
import org.webbitserver.HttpHandler;
import org.webbitserver.HttpRequest;
import org.webbitserver.HttpResponse;

import wolf.WolfDB;
import wolf.model.Faction;
import wolf.model.Role;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ez.DB;
import ez.Row;

public class RankingsHandler implements HttpHandler {

  private static final DecimalFormat format = new DecimalFormat("0.00");

  private final DB db = WolfDB.get();

  @Override
  public void handleHttpRequest(HttpRequest request, HttpResponse response, HttpControl control)
      throws Exception {

    Set<String> ratedGames = Sets.newLinkedHashSet();
    for (Row game : db.select("SELECT id FROM games WHERE rated = TRUE ORDER BY start_date ASC")) {
      ratedGames.add(game.<String>get("id"));
    }

    Multimap<String, Row> gameRows = LinkedListMultimap.create();
    for (Row row : db.select("SELECT * FROM players")) {
      String id = row.get("game_id");
      if (!ratedGames.contains(id)) {
        continue;
      }
      gameRows.put(id, row);
    }

    final Multimap<String, Integer> scores = LinkedListMultimap.create();
    for (String game : gameRows.keySet()) {
      for (Entry<String, Integer> score : getScores(gameRows.get(game)).entrySet()) {
        scores.put(score.getKey(), score.getValue());
      }
    }

    List<String> rankings = Lists.newArrayList(scores.keySet());
    Collections.sort(rankings, new Comparator<String>() {
      @Override
      public int compare(String a, String b) {
        int ret = total(scores.get(b)) - total(scores.get(a));
        if (ret == 0) {
          return a.compareTo(b);
        }
        return ret;
      }
    });

    JsonArray ret = new JsonArray();
    for (String player : rankings) {
      JsonObject o = new JsonObject();
      o.addProperty("name", player);
      o.addProperty("wins", getWins(scores.get(player)));
      o.addProperty("losses", getLosses(scores.get(player)));
      o.addProperty("win_percentage", getWinPercentage(scores.get(player)));
      o.addProperty("score", total(scores.get(player)));
      ret.add(o);
    }

    response.content(ret.toString()).end();
  }

  private int getWins(Collection<Integer> scores) {
    int ret = 0;
    for (Integer i : scores) {
      if (i > 0) {
        ret++;
      }
    }
    return ret;
  }

  private int getLosses(Collection<Integer> scores) {
    int ret = 0;
    for (Integer i : scores) {
      if (i < 0) {
        ret++;
      }
    }
    return ret;
  }

  private String getWinPercentage(Collection<Integer> scores) {
    int wins = getWins(scores);
    int losses = getLosses(scores);

    if (wins == 0 && losses == 0) {
      return format.format(0);
    }

    double d = 1.0 * wins / (wins + losses);
    return format.format(d * 100);
  }

  private int total(Collection<Integer> c) {
    int ret = 0;
    for (Integer i : c) {
      ret += i;
    }
    return ret;
  }

  private Map<String, Integer> getScores(Collection<Row> rows) {
    Set<Faction> factions = EnumSet.noneOf(Faction.class);
    for (Row row : rows) {
      Role role = Role.parse(row.<String>get("role"));
      factions.add(role.getFaction());
    }

    int pointsForWinner = (int) (12 * (1 - 1.0 / factions.size()));
    int pointsForLoser = (int) (-12 * (1.0 / factions.size()));

    Map<String, Integer> ret = Maps.newHashMap();
    for (Row row : rows) {
      boolean winner = row.get("winner");
      ret.put(row.<String>get("name"), winner ? pointsForWinner : pointsForLoser);
    }
    return ret;
  }

}