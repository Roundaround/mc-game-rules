package me.roundaround.gamerulesmod.gametest;

import com.mojang.datafixers.util.Either;
import me.roundaround.allay.api.gametest.ServerGameTest;
import me.roundaround.gamerulesmod.common.gamerule.RuleInfo;
import me.roundaround.gamerulesmod.common.gamerule.RuleState;
import me.roundaround.gamerulesmod.server.gamerule.GameRulesStorage;
import me.roundaround.trove.gametest.ServerTest;
import me.roundaround.trove.gametest.ServerTestContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.Date;
import java.util.Map;

import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.check;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.info;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.op;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.sendSet;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.storage;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.track;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.value;

/**
 * The happy path on a multiplayer server: an op sees every rule as editable, a set packet
 * changes a boolean and an int rule at once, and each change lands in the history with its
 * previous value and a timestamp that the next fetch reports back.
 */
@ServerGameTest
public class OpSetsRulesTest implements ServerTest {
  @Override
  public void runTest(ServerTestContext context) {
    ServerPlayer player = context.player();
    op(context, player);
    Either<Boolean, Integer> keepBefore = track(context, GameRules.KEEP_INVENTORY);
    Either<Boolean, Integer> speedBefore = track(context, GameRules.RANDOM_TICK_SPEED);
    GameRulesStorage storage = storage(context);
    int keepChanges = storage.getChangeCount(GameRules.KEEP_INVENTORY);
    int speedChanges = storage.getChangeCount(GameRules.RANDOM_TICK_SPEED);

    check(info(context, player, GameRules.KEEP_INVENTORY).state() == RuleState.MUTABLE, "an op should see rules as mutable");

    boolean keep = !keepBefore.left().orElseThrow();
    int speed = speedBefore.right().orElseThrow() + 4;
    long sentAt = System.currentTimeMillis();
    sendSet(context, player, Map.of(
        "minecraft:keep_inventory", Either.left(keep),
        "minecraft:random_tick_speed", Either.right(speed)
    ));

    check(value(context, GameRules.KEEP_INVENTORY).equals(Either.left(keep)), "keep_inventory was not set");
    check(value(context, GameRules.RANDOM_TICK_SPEED).equals(Either.right(speed)), "random_tick_speed was not set");

    check(storage.getChangeCount(GameRules.KEEP_INVENTORY) == keepChanges + 1, "keep_inventory change was not recorded");
    check(storage.getChangeCount(GameRules.RANDOM_TICK_SPEED) == speedChanges + 1, "random_tick_speed change was not recorded");
    check(storage.getPreviousValue(GameRules.KEEP_INVENTORY).equals(keepBefore), "history kept the wrong previous value");
    if (keepChanges == 0) {
      check(storage.getOriginalValue(GameRules.KEEP_INVENTORY).equals(keepBefore), "history kept the wrong original value");
    }
    Date changed = storage.getLastChangeDate(GameRules.KEEP_INVENTORY);
    check(changed != null && changed.getTime() >= sentAt - 1000, "history change date is missing or stale: " + changed);

    RuleInfo after = info(context, player, GameRules.KEEP_INVENTORY);
    check(after.value().equals(Either.left(keep)), "fetch did not report the new value");
    check(changed.equals(after.changed()), "fetch did not report the change date");
  }
}
