package me.roundaround.gamerulesmod.client.gui.screen;

import com.mojang.datafixers.util.Either;
import me.roundaround.gamerulesmod.client.gui.widget.GameRuleListWidget;
import me.roundaround.gamerulesmod.client.network.ClientNetworking;
import me.roundaround.trove.client.gui.layout.screen.ThreeSectionLayoutWidget;
import me.roundaround.trove.client.gui.util.GuiUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class GameRuleScreen extends Screen {
  private final ThreeSectionLayoutWidget layout = new ThreeSectionLayoutWidget(this);
  private final LinkedHashMap<String, Either<Boolean, Integer>> dirtyValues = new LinkedHashMap<>();
  private final Screen parent;

  private GameRuleListWidget list;
  private EditBox searchBox;
  private Button saveButton;

  public GameRuleScreen(Screen parent) {
    super(Component.translatable("gamerulesmod.main.title"));
    this.parent = parent;
  }

  @Override
  protected void init() {
    assert this.minecraft != null;
    assert this.minecraft.level != null;

    this.list = this.layout.addBody(new GameRuleListWidget(
        this.minecraft,
        this.layout,
        this::onRuleChange
    ));

    this.layout.setHeaderHeight(48);
    this.layout.getHeader().spacing(GuiUtil.PADDING);
    this.layout.addHeader(this.font, this.title);

    Component searchHint = Component.translatable("gui.game_rule.search");
    this.searchBox = new EditBox(this.font, 200, 20, searchHint);
    this.searchBox.setHint(searchHint.copy().withStyle(EditBox.SEARCH_HINT_STYLE));
    this.searchBox.setResponder((value) -> {
      if (this.list != null) {
        this.list.applyFilter(value);
      }
    });
    this.layout.addHeader(this.searchBox);

    this.saveButton = this.layout.addFooter(Button.builder(
        CommonComponents.GUI_DONE, (button) -> {
          if (this.dirtyValues.isEmpty()) {
            return;
          }

          this.minecraft.gui.setScreen(new ConfirmScreen(
              this::onConfirmChoice,
              Component.translatable("gamerulesmod.confirm.title"),
              this.generateConfirmText()
          ));
        }
    ).build());

    this.layout.addFooter(Button.builder(CommonComponents.GUI_CANCEL, (button) -> this.onClose()).build());

    this.layout.visitChildren((child) -> {
      if (child instanceof AbstractWidget widget) {
        this.addRenderableWidget(widget);
      }
    });
    this.repositionElements();

    this.list.fetch();
  }

  @Override
  protected void repositionElements() {
    this.layout.arrangeElements();
  }

  @Override
  protected void setInitialFocus() {
    if (this.searchBox != null) {
      this.setInitialFocus(this.searchBox);
    }
  }

  @Override
  public void onClose() {
    if (this.list != null) {
      this.list.close();
    }
    if (this.minecraft != null) {
      this.minecraft.gui.setScreen(this.parent);
    }
  }

  private void onRuleChange(boolean allValid, boolean anyDirty) {
    this.dirtyValues.clear();

    this.saveButton.active = allValid && anyDirty;
    if (this.saveButton.active) {
      this.dirtyValues.putAll(this.list.getDirtyValues());
    }
  }

  private void onConfirmChoice(boolean confirmed) {
    if (confirmed) {
      ClientNetworking.sendSet(this.list.getDirtyValues());
      this.onClose();
    } else {
      assert this.minecraft != null;
      this.minecraft.gui.setScreen(this);
    }
  }

  private Component generateConfirmText() {
    MutableComponent text = Component.empty();

    assert this.minecraft != null;
    ClientLevel world = this.minecraft.level;
    if (world != null && world.getLevelData().isHardcore()) {
      text.append(Component.translatable("gamerulesmod.confirm.hardcore")).append("\n\n");
    }

    text.append(Component.translatable("gamerulesmod.confirm.summary")).append("\n");

    LinkedHashMap<String, Either<Boolean, Integer>> dirtyValues = new LinkedHashMap<>();
    Iterator<Map.Entry<String, Either<Boolean, Integer>>> iterator = this.dirtyValues.entrySet().iterator();
    for (int i = 0; i < 5 && iterator.hasNext(); i++) {
      Map.Entry<String, Either<Boolean, Integer>> entry = iterator.next();
      dirtyValues.put(entry.getKey(), entry.getValue());
    }

    dirtyValues.forEach((id, value) -> text.append(Component.literal(id).withStyle(ChatFormatting.BOLD))
        .append(": ")
        .append(Component.literal(value.map(Object::toString, Object::toString))
            .withStyle(ChatFormatting.LIGHT_PURPLE))
        .append("\n"));

    int additional = this.dirtyValues.size() - dirtyValues.size();
    if (additional > 0) {
      text.append(Component.translatable("gamerulesmod.confirm.more", additional)).append("\n");
    }

    return text;
  }
}
