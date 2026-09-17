package me.roundaround.gamerulesmod.gametest;

import com.mojang.datafixers.util.Either;
import me.roundaround.allay.api.gametest.ServerGameTest;
import me.roundaround.gamerulesmod.server.gamerule.GameRulesStorage;
import me.roundaround.trove.gametest.ServerTest;
import me.roundaround.trove.gametest.ServerTestContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.Map;

import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.check;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.op;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.sendSet;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.storage;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.track;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.value;

/**
 * Entries a well-behaved client never sends must not crash the handler, change anything, or
 * leave a phantom entry in the history: an unknown rule id, and an int value for a boolean rule.
 */
@ServerGameTest
public class MalformedSetIsIgnoredTest implements ServerTest {
  @Override
  public void runTest(ServerTestContext context) {
    ServerPlayer player = context.player();
    op(context, player);
    Either<Boolean, Integer> before = track(context, GameRules.PVP);
    GameRulesStorage storage = storage(context);
    int changes = storage.getChangeCount(GameRules.PVP);

    sendSet(context, player, Map.of(
        "minecraft:not_a_rule", Either.left(true),
        "minecraft:pvp", Either.right(1)
    ));

    check(value(context, GameRules.PVP).equals(before), "a wrong-typed value changed pvp");
    check(storage.getChangeCount(GameRules.PVP) == changes, "a wrong-typed value was recorded in the history");
    check(!storage.hasChanged("minecraft:not_a_rule"), "an unknown rule id was recorded in the history");
  }
}
