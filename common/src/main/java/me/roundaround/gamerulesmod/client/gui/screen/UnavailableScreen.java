package me.roundaround.gamerulesmod.client.gui.screen;

import me.roundaround.trove.client.gui.layout.screen.ThreeSectionLayoutWidget;
import me.roundaround.trove.client.gui.widget.drawable.LabelWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

public class UnavailableScreen extends Screen {
  private final ThreeSectionLayoutWidget layout = new ThreeSectionLayoutWidget(this);
  private final Screen parent;
  private final List<Component> info;

  public UnavailableScreen(Screen parent, List<Component> info) {
    super(Component.translatable("gamerulesmod.unavailable.title"));
    this.parent = parent;
    this.info = info;
  }

  @Override
  protected void init() {
    assert this.minecraft != null;

    this.layout.addHeader(this.font, this.title);

    this.layout.addBody(LabelWidget.builder(this.font, this.info)
        .alignTextCenterX()
        .alignTextCenterY()
        .hideBackground()
        .showShadow()
        .lineSpacing(2)
        .build());

    this.layout.addFooter(Button.builder(CommonComponents.GUI_BACK, (b) -> this.onClose())
        .width(Button.SMALL_WIDTH)
        .build());

    this.layout.visitChildren((child) -> {
      if (child instanceof AbstractWidget widget) {
        this.addRenderableWidget(widget);
      }
    });
    this.repositionElements();
  }

  @Override
  protected void repositionElements() {
    this.layout.arrangeElements();
  }

  @Override
  public void onClose() {
    assert this.minecraft != null;
    this.minecraft.gui.setScreen(this.parent);
  }
}
