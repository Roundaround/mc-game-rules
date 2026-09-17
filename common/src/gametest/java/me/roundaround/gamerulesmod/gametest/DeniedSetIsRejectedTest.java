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

import java.util.Map;

import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.check;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.fetch;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.sendSet;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.storage;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.track;
import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.value;

/**
 * A player without op sees every rule as denied, and a set packet from them (which the client UI
 * would never send, so this is the crafted-packet case) changes nothing and records nothing.
 */
@ServerGameTest
public class DeniedSetIsRejectedTest implements ServerTest {
  @Override
  public void runTest(ServerTestContext context) {
    ServerPlayer player = context.player();
    Either<Boolean, Integer> before = track(context, GameRules.FALL_DAMAGE);
    GameRulesStorage storage = storage(context);
    int changes = storage.getChangeCount(GameRules.FALL_DAMAGE);

    for (RuleInfo info : fetch(context, player)) {
      check(info.state() == RuleState.DENIED, "a non-op should see " + info.id() + " as denied, not " + info.state());
    }

    sendSet(context, player, Map.of("minecraft:fall_damage", Either.left(!before.left().orElseThrow())));

    check(value(context, GameRules.FALL_DAMAGE).equals(before), "a non-op changed fall_damage");
    check(storage.getChangeCount(GameRules.FALL_DAMAGE) == changes, "a rejected change was recorded in the history");
  }
}
