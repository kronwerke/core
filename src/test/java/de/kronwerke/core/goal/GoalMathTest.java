package de.kronwerke.core.goal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fills goals the way players would, light items first and heavy items first, and checks that
 * every goal reaches its hold point and, once released, its end.
 */
class GoalMathTest {
    record Item(String id, long target, long points) {}

    /** Deposits stacks of 64 (heavy items one by one) in the given order; returns the done points. */
    private static long fill(List<Item> items, long[] have, double hold, boolean released) {
        long total = 0;
        for (Item it : items) total += it.target * it.points;
        boolean moved = true;
        while (moved) {
            moved = false;
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                long done = done(items, have);
                long t = GoalMath.take(it.target - have[i], it.points > 10 ? 1 : 64, it.points, total, done, hold, released);
                if (t > 0) {
                    have[i] += t;
                    moved = true;
                    break; // in order: the next deposit starts at the first item that still takes
                }
            }
        }
        return done(items, have);
    }

    private static long done(List<Item> items, long[] have) {
        long d = 0;
        for (int i = 0; i < items.size(); i++) d += Math.min(have[i], items.get(i).target) * items.get(i).points;
        return d;
    }

    private static void check(String name, List<Item> items, double hold) {
        long total = 0;
        for (Item it : items) total += it.target * it.points;
        for (Comparator<Item> order : List.of(Comparator.comparingLong(Item::points), Comparator.comparingLong(Item::points).reversed())) {
            List<Item> sorted = new ArrayList<>(items);
            sorted.sort(order);
            long[] have = new long[sorted.size()];
            long done = fill(sorted, have, hold, false);
            assertTrue(GoalMath.held(total, done, hold, false), name + " stalls at " + done + " of " + total + " before its hold");
            done = fill(sorted, have, hold, true);
            assertEquals(total, done, name + " cannot be finished after the release");
        }
    }

    @Test
    void heavyItemCrossesTheHold() {
        // 9,750 points, hold 98 % = 9,555; the brass hearts (300) used to stall it at 9,450
        check("stage2 like", List.of(new Item("brass", 2000, 1), new Item("mana", 1000, 1), new Item("misc", 4350, 1),
                new Item("heart", 8, 300)), 0.98);
    }

    @Test
    void shippedGoals() throws Exception {
        Path file = Path.of("../pack/config/kronwerke/goals.json");
        if (!Files.exists(file)) return; // the pack is not next to Core here
        JsonArray goals = JsonParser.parseString(Files.readString(file)).getAsJsonArray();
        for (JsonElement e : goals) {
            JsonObject g = e.getAsJsonObject();
            List<Item> items = new ArrayList<>();
            for (JsonElement p : g.getAsJsonArray("pillars")) {
                for (JsonElement i : p.getAsJsonObject().getAsJsonArray("items")) {
                    JsonObject o = i.getAsJsonObject();
                    items.add(new Item(o.get("item").getAsString(), o.get("base").getAsLong(), o.has("weight") ? o.get("weight").getAsLong() : 1));
                }
            }
            check(g.get("id").getAsString(), items, g.has("holdAt") ? g.get("holdAt").getAsDouble() : 1.0);
        }
    }
}
