package me.roundaround.gamerulesmod.client.option;

import com.mojang.blaze3d.platform.InputConstants;
import me.roundaround.gamerulesmod.client.gui.screen.GameRuleScreen;
import me.roundaround.trove.event.ClientLifecycle;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {
  public static KeyMapping openEditScreen;

  public static void register() {
    openEditScreen = me.roundaround.trove.client.KeyBindings.register(new KeyMapping(
        "gamerulesmod.key.openEditScreen",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        KeyMapping.Category.MISC
    ));

    ClientLifecycle.onTick(() -> {
      Minecraft client = Minecraft.getInstance();
      while (openEditScreen.consumeClick()) {
        client.gui.setScreen(new GameRuleScreen(client.gui.screen()));
      }
    });
  }

  private KeyBindings() {
  }
}
