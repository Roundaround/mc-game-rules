package me.roundaround.gamerulesmod.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import me.roundaround.allay.api.gametest.ClientGameTest;
import me.roundaround.gamerulesmod.client.gui.screen.GameRuleScreen;
import me.roundaround.gamerulesmod.client.option.KeyBindings;
import me.roundaround.trove.gametest.ClientTest;
import me.roundaround.trove.gametest.ClientTestContext;
import me.roundaround.trove.gametest.ClientWorld;
import me.roundaround.trove.gametest.GameTestAssertionException;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.CommonComponents;

import java.util.function.BooleanSupplier;

/**
 * The whole player-facing flow in single player: open the edit screen with its keybind, search for
 * keep_inventory, flip it, confirm on Done, then ask the server with {@code /gamerule} and read the
 * answer out of chat. The command is queried before and after so the test sees the value actually
 * flip rather than assume what the dev world starts with.
 */
@ClientGameTest
public class EditRuleThroughUiTest implements ClientTest {
  private static final String RULE = "keep_inventory";

  @Override
  public void runTest(ClientTestContext context) {
    try (ClientWorld world = context.worldBuilder().creative().stopTime(true).create()) {
      bindOpenKey(context);

      world.runCommand("gamerule " + RULE);
      String before = context.waitForChatMessage(RULE).getString();
      boolean wasOn = queriedValue(before);

      world.clickKey((mc) -> KeyBindings.openEditScreen);
      context.waitForScreen(GameRuleScreen.class);
      waitUntil(context, () -> !context.widgets(CycleButton.class).isEmpty(), "the rule list never loaded");

      EditBox search = context.computeOnClient((mc) -> mc.gui.screen().children().stream()
          .filter(EditBox.class::isInstance)
          .map(EditBox.class::cast)
          .findFirst()
          .orElseThrow(() -> new GameTestAssertionException("the edit screen has no search box")));
      context.runOnClient((mc) -> search.setValue(RULE));
      waitUntil(context, () -> context.widgets(CycleButton.class).size() == 1, "the search did not narrow the list to one rule");

      CycleButton<?> toggle = context.widget(CycleButton.class);
      String off = context.computeOnClient((mc) -> toggle.getMessage().getString());
      context.clickWidget(toggle);
      String on = context.computeOnClient((mc) -> toggle.getMessage().getString());
      check(!on.equals(off), "clicking the keep_inventory toggle did not change it (" + off + ")");

      context.clickButton(CommonComponents.GUI_DONE.getString());
      context.waitForScreen(ConfirmScreen.class);
      context.clickButton(CommonComponents.GUI_YES.getString());
      context.waitForScreen(null);
      world.settle();

      int seen = context.chatMessages().size();
      world.runCommand("gamerule " + RULE);
      waitUntil(context, () -> context.chatMessages().size() > seen, "no query feedback arrived after saving");
      String after = context.chatMessages().getFirst().getString();
      check(after.contains(RULE) && queriedValue(after) != wasOn,
          "after saving, the query still said: " + after);
    }
  }

  /** The value at the end of vanilla's "Game rule X is currently set to Y" feedback. */
  private static boolean queriedValue(String query) {
    if (query.endsWith("true")) {
      return true;
    }
    if (query.endsWith("false")) {
      return false;
    }
    throw new GameTestAssertionException("could not read a boolean out of the query feedback: " + query);
  }

  /** The mod ships the keybind unbound; give it a key for this test so the real open path runs. */
  private static void bindOpenKey(ClientTestContext context) {
    InputConstants.Key original = context.computeOnClient((mc) -> KeyBindings.openEditScreen.getDefaultKey());
    context.onCleanup(() -> context.runOnClient((mc) -> {
      KeyBindings.openEditScreen.setKey(original);
      KeyMapping.resetMapping();
    }));
    context.runOnClient((mc) -> {
      KeyBindings.openEditScreen.setKey(InputConstants.getKey("key.keyboard.g"));
      KeyMapping.resetMapping();
    });
  }

  /** Poll from the test thread; the context helpers hop to the client thread themselves. */
  private static void waitUntil(ClientTestContext context, BooleanSupplier condition, String failure) {
    for (int i = 0; i < 200; i++) {
      if (condition.getAsBoolean()) {
        return;
      }
      context.waitTick();
    }
    throw new GameTestAssertionException(failure);
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new GameTestAssertionException(message);
    }
  }
}
