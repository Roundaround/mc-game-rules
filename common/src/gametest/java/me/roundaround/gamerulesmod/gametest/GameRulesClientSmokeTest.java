package me.roundaround.gamerulesmod.gametest;

import me.roundaround.allay.api.gametest.ClientGameTest;
import me.roundaround.gamerulesmod.client.gui.screen.UnavailableScreen;
import me.roundaround.trove.gametest.ClientTest;
import me.roundaround.trove.gametest.ClientTestContext;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Opens the mod's standalone screen from the title screen and asserts it renders.
 * {@link UnavailableScreen} needs no world/server, so it exercises the mod's Trove
 * layout/widget client path on the 26.2 GUI-render thread without crashing.
 */
@ClientGameTest
public class GameRulesClientSmokeTest implements ClientTest {
  @Override
  public void runTest(ClientTestContext context) {
    context.setScreen(() -> new UnavailableScreen(null, List.of(Component.literal("smoke test"))));
    context.assertScreen(UnavailableScreen.class);
    context.waitTicks(2);
    context.returnToTitle();
  }
}
