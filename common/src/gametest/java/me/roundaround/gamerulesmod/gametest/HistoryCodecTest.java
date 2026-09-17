package me.roundaround.gamerulesmod.gametest;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import me.roundaround.allay.api.gametest.ServerGameTest;
import me.roundaround.gamerulesmod.common.gamerule.RuleHistory;
import me.roundaround.gamerulesmod.server.gamerule.GameRulesStorage;
import me.roundaround.trove.gametest.ServerTest;
import me.roundaround.trove.gametest.ServerTestContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.Map;

import static me.roundaround.gamerulesmod.gametest.GameRulesTestSupport.check;

/**
 * The persisted history survives a save/load round trip, and a pre-2.0.0 list-style file decodes
 * through the same codec with its camelCase keys migrated: plain renames, the inverted rules,
 * the boolean-to-int fire rule, removed rules dropped, and unrecognised keys kept as they were.
 */
@ServerGameTest
public class HistoryCodecTest implements ServerTest {
  private static final Codec<GameRulesStorage> CODEC =
      Codec.withAlternative(GameRulesStorage.CODEC, GameRulesStorage.LIST_STYLE_CODEC);

  @Override
  public void runTest(ServerTestContext context) {
    roundTrip();
    legacyMigration();
  }

  private static void roundTrip() {
    RuleHistory history = RuleHistory.create(Either.left(false));
    history.recordChange(Either.left(false));
    Tag saved = CODEC.encodeStart(NbtOps.INSTANCE, decode(encodeMap(Map.of("minecraft:keep_inventory", history))))
        .getOrThrow();
    GameRulesStorage loaded = decode(saved);

    RuleHistory reloaded = loaded.getHistory().get("minecraft:keep_inventory");
    check(reloaded != null, "history entry lost in the round trip");
    check(reloaded.getOriginalValue().equals(history.getOriginalValue()), "original value changed in the round trip");
    check(reloaded.getChanges().equals(history.getChanges()), "change entries differ after the round trip");
  }

  private static void legacyMigration() {
    ListTag entries = new ListTag();
    entries.add(legacyEntry("keepInventory", Either.left(false)));
    entries.add(legacyEntry("disableRaids", Either.left(true)));
    entries.add(legacyEntry("doFireTick", Either.left(true)));
    entries.add(legacyEntry("spawnChunkRadius", Either.right(2)));
    entries.add(legacyEntry("minecraft:pvp", Either.left(true)));
    entries.add(legacyEntry("someOtherModsRule", Either.right(9)));
    CompoundTag root = new CompoundTag();
    root.put("History", entries);

    Map<String, RuleHistory> migrated = decode(root).getHistory();
    check(migrated.containsKey("minecraft:keep_inventory"), "keepInventory was not renamed");
    check(!migrated.containsKey("keepInventory"), "the legacy keepInventory key survived migration");
    check(migrated.get("minecraft:raids").getOriginalValue().equals(Either.left(false)), "disableRaids was not inverted into raids");
    check(migrated.get("minecraft:fire_spread_radius_around_player").getOriginalValue().equals(Either.right(128)),
        "doFireTick=true was not converted to a fire radius of 128");
    check(!migrated.containsKey("spawnChunkRadius"), "the removed spawnChunkRadius rule was kept");
    check(migrated.containsKey("minecraft:pvp"), "an already-namespaced key was not preserved");
    check(migrated.containsKey("someOtherModsRule"), "an unrecognised key was dropped");
    check(migrated.get("minecraft:keep_inventory").getChangeCount() == 1, "the legacy change entry was lost");
  }

  private static CompoundTag legacyEntry(String key, Either<Boolean, Integer> value) {
    CompoundTag change = new CompoundTag();
    change.put("Value", RuleHistory.VALUE_CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow());
    change.putLong("Date", 1_700_000_000_000L);
    ListTag changes = new ListTag();
    changes.add(change);
    CompoundTag entry = new CompoundTag();
    entry.put("Changes", changes);
    entry.put("OriginalValue", RuleHistory.VALUE_CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow());
    entry.putString("Key", key);
    return entry;
  }

  private static Tag encodeMap(Map<String, RuleHistory> history) {
    return Codec.unboundedMap(Codec.STRING, RuleHistory.CODEC).encodeStart(NbtOps.INSTANCE, history).getOrThrow();
  }

  private static GameRulesStorage decode(Tag tag) {
    return CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
  }
}
