package me.roundaround.gamerulesmod.server.network;

import com.mojang.datafixers.util.Either;
import me.roundaround.gamerulesmod.common.gamerule.RuleHelper;
import me.roundaround.gamerulesmod.common.gamerule.RuleInfo;
import me.roundaround.gamerulesmod.common.gamerule.RuleState;
import me.roundaround.gamerulesmod.network.Networking;
import me.roundaround.gamerulesmod.server.gamerule.GameRulesStorage;
import me.roundaround.gamerulesmod.server.gamerule.RuleInfoServerHelper;
import me.roundaround.trove.event.ServerLifecycle;
import me.roundaround.trove.network.TroveNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ServerNetworking {
  private ServerNetworking() {
  }

  public static void init() {
    // Force GameRulesStorage creation on server start so it can auto-migrate the persisted data and
    // import any legacy (pre-2.0.0 flat-file) history. Replaces the deleted MinecraftServerMixin
    // createWorlds TAIL injection.
    ServerLifecycle.onServerStarted(GameRulesStorage::bootstrap);
  }

  public static void sendFetch(ServerPlayer player, int reqId, List<RuleInfo> rules) {
    TroveNetworking.sendToClient(player, new Networking.FetchS2C(reqId, rules));
  }

  // Handlers are dispatched on the server thread by the loader networking bridge.
  public static void handleFetch(Networking.FetchC2S payload, ServerPlayer player) {
    ServerLevel world = player.level();
    if (world == null) {
      return;
    }

    sendFetch(
        player,
        payload.reqId(),
        RuleInfoServerHelper.collect(world.getGameRules(), player, null)
    );
  }

  public static void handleSet(Networking.SetC2S payload, ServerPlayer player) {
    ServerLevel world = player.level();
    if (world == null) {
      return;
    }

    final GameRules gameRules = world.getGameRules();
    final Set<String> mutableRules = RuleInfoServerHelper.collect(
            gameRules,
            player,
            (state) -> state.equals(RuleState.MUTABLE)
        )
        .stream()
        .map(RuleInfo::id)
        .collect(Collectors.toSet());
    final GameRulesStorage historyStorage = world.getServer()
        .getDataStorage()
        .computeIfAbsent(GameRulesStorage.STATE_TYPE);
    final var warnCount = new Object() {
      int value = 0;
    };

    payload.values().forEach((id, either) -> {
      Either<Boolean, Integer> previousValue = RuleHelper.getValue(gameRules, id);
      if (previousValue == null || previousValue.left().isPresent() != either.left().isPresent()) {
        // Unknown rule id or a value of the wrong type (a malformed packet); skip so the history
        // never records a change that didn't happen.
        return;
      }
      if (!mutableRules.contains(id)) {
        warnCount.value++;
        return;
      }
      RuleHelper.setValue(gameRules, id, either);
      historyStorage.recordChange(id, previousValue);
    });

    if (warnCount.value > 0) {
      me.roundaround.gamerulesmod.GameRules.LOGGER.warn(
          "Player {} attempted to change {} game rule(s), but did not have permission to do so.",
          player.getName().getString(),
          warnCount.value
      );
    }
  }
}
