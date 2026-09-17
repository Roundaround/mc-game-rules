package me.roundaround.gamerulesmod.gametest;

import com.mojang.datafixers.util.Either;
import me.roundaround.gamerulesmod.common.gamerule.RuleHelper;
import me.roundaround.gamerulesmod.common.gamerule.RuleInfo;
import me.roundaround.gamerulesmod.network.Networking;
import me.roundaround.gamerulesmod.server.gamerule.GameRulesStorage;
import me.roundaround.gamerulesmod.server.gamerule.RuleInfoServerHelper;
import me.roundaround.gamerulesmod.server.network.ServerNetworking;
import me.roundaround.trove.gametest.GameTestAssertionException;
import me.roundaround.trove.gametest.ServerTestContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRule;

import java.util.List;
import java.util.Map;

/**
 * Shared helpers for the Game Rules {@code @ServerGameTest} suite. The dedicated server is
 * multiplayer, so the op-permission gate is what decides who may edit; the hardcore once-only
 * lock only exists in single player and is not reachable here.
 */
final class GameRulesTestSupport {
  private GameRulesTestSupport() {
  }

  /** Op {@code player} for the rest of the test. */
  static void op(ServerTestContext context, ServerPlayer player) {
    context.onCleanup(() -> context.runOnServer((server) -> server.getPlayerList().deop(player.nameAndId())));
    context.runOnServer((server) -> server.getPlayerList().op(player.nameAndId()));
  }

  /** Feed a set packet straight to the server handler, as the loader bridge would on receipt. */
  static void sendSet(ServerTestContext context, ServerPlayer player, Map<String, Either<Boolean, Integer>> values) {
    context.runOnServer((server) -> ServerNetworking.handleSet(new Networking.SetC2S(values), player));
  }

  /** What a fetch from {@code player} would answer with. */
  static List<RuleInfo> fetch(ServerTestContext context, ServerPlayer player) {
    return context.computeOnServer(
        (server) -> RuleInfoServerHelper.collect(server.overworld().getGameRules(), player, null));
  }

  static RuleInfo info(ServerTestContext context, ServerPlayer player, GameRule<?> rule) {
    String id = RuleHelper.idOf(rule);
    return fetch(context, player).stream()
        .filter((info) -> info.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new GameTestAssertionException("fetch did not include " + id));
  }

  static Either<Boolean, Integer> value(ServerTestContext context, GameRule<?> rule) {
    return context.computeOnServer((server) -> RuleHelper.getValue(server.overworld().getGameRules(), rule));
  }

  /** Snapshot {@code rule} and restore it at teardown so tests leave the shared world untouched. */
  static Either<Boolean, Integer> track(ServerTestContext context, GameRule<?> rule) {
    Either<Boolean, Integer> original = value(context, rule);
    context.onCleanup(() -> context.runOnServer(
        (server) -> RuleHelper.setValue(server.overworld().getGameRules(), rule, original, server)));
    return original;
  }

  static GameRulesStorage storage(ServerTestContext context) {
    return context.computeOnServer((server) -> server.getDataStorage().computeIfAbsent(GameRulesStorage.STATE_TYPE));
  }

  static void check(boolean condition, String message) {
    if (!condition) {
      throw new GameTestAssertionException(message);
    }
  }
}
