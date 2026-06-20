package me.roundaround.gamerulesmod.client.gui.widget;

import com.mojang.datafixers.util.Either;
import me.roundaround.gamerulesmod.client.ClientUtil;
import me.roundaround.gamerulesmod.client.network.ClientNetworking;
import me.roundaround.gamerulesmod.common.gamerule.RuleHelper;
import me.roundaround.gamerulesmod.common.gamerule.RuleInfo;
import me.roundaround.gamerulesmod.common.gamerule.RuleState;
import me.roundaround.gamerulesmod.network.Networking;
import me.roundaround.trove.client.gui.layout.NonPositioningLayoutWidget;
import me.roundaround.trove.client.gui.layout.linear.LinearLayoutWidget;
import me.roundaround.trove.client.gui.layout.screen.ThreeSectionLayoutWidget;
import me.roundaround.trove.client.gui.util.GuiUtil;
import me.roundaround.trove.client.gui.widget.FlowListWidget;
import me.roundaround.trove.client.gui.widget.IconButtonWidget;
import me.roundaround.trove.client.gui.widget.ParentElementEntryListWidget;
import me.roundaround.trove.client.gui.widget.TooltipWidget;
import me.roundaround.trove.client.gui.widget.drawable.LabelWidget;
import me.roundaround.trove.network.request.ServerRequest;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.LoadingDotsText;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.CommonColors;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class GameRuleListWidget extends ParentElementEntryListWidget<GameRuleListWidget.Entry> implements AutoCloseable {
  private static final Component EMPTY_TEXT =
      Component.translatable("gamerulesmod.main.none").withStyle(ChatFormatting.ITALIC);
  private static final Component NO_RESULTS_TEXT =
      Component.translatable("gamerulesmod.main.noResults").withStyle(ChatFormatting.ITALIC);

  private final BiConsumer<Boolean, Boolean> onRuleChange;
  private final List<Section> sections = new ArrayList<>();

  private ServerRequest<Networking.FetchS2C> pendingRequest;
  private String filter = "";
  private boolean rulesLoaded = false;

  public GameRuleListWidget(
      Minecraft client,
      ThreeSectionLayoutWidget layout,
      BiConsumer<Boolean, Boolean> onRuleChange
  ) {
    super(client, layout);
    this.onRuleChange = onRuleChange;

    this.setContentPaddingY(GuiUtil.PADDING / 2);
  }

  public void fetch() {
    this.cancel();

    this.rulesLoaded = false;
    this.sections.clear();

    this.clearEntries();
    this.addEntry(LoadingEntry.factory(this.client.font));
    this.arrangeElements();

    ServerRequest<Networking.FetchS2C> pendingRequest = ClientNetworking.sendFetch();

    pendingRequest.getFuture().orTimeout(30, TimeUnit.SECONDS).whenCompleteAsync(
        (payload, throwable) -> {
          if (throwable != null) {
            this.setError();
          } else {
            try {
              this.setRules(payload.rules());
            } catch (Exception e) {
              me.roundaround.gamerulesmod.GameRules.LOGGER.error("Exception thrown while populating rules list!", e);
              this.setError();
            }
          }
        }, this.client
    );
  }

  private void cancel() {
    if (this.pendingRequest != null) {
      this.pendingRequest.cancel();
      this.pendingRequest = null;
    }
  }

  private void setError() {
    this.clearEntries();
    this.addEntry(ErrorEntry.factory(this.client.font));
    this.arrangeElements();
  }


  private void setRules(final List<RuleInfo> rules) {
    this.clearEntries();
    this.sections.clear();
    this.rulesLoaded = false;

    final Font textRenderer = this.client.font;

    final GameRules gameRules = ClientUtil.getDefaultRules();
    final HashMap<String, RuleState> stateMap = new HashMap<>();
    final HashMap<String, Date> changedMap = new HashMap<>();

    rules.forEach((ruleInfo) -> {
      RuleHelper.setValue(gameRules, ruleInfo.id(), ruleInfo.value());
      stateMap.put(ruleInfo.id(), ruleInfo.state());
      changedMap.put(ruleInfo.id(), ruleInfo.changed());
    });

    final HashMap<GameRuleCategory, HashMap<GameRule<?>, FlowListWidget.EntryFactory<? extends RuleEntry>>> ruleEntries = new HashMap<>();

    gameRules.availableRules().filter(RuleHelper::isSupported).forEach((rule) -> {
      RuleState state = stateMap.get(RuleHelper.idOf(rule));
      if (state == null) {
        // Server didn't report this rule (e.g. feature-set mismatch); skip it.
        return;
      }
      Date changed = changedMap.get(RuleHelper.idOf(rule));
      FlowListWidget.EntryFactory<? extends RuleEntry> factory = RuleHelper.isBoolean(rule)
          ? BooleanRuleEntry.factory(gameRules, rule, state, changed, this::onRuleChange, textRenderer)
          : IntRuleEntry.factory(gameRules, rule, state, changed, this::onRuleChange, textRenderer);
      ruleEntries.computeIfAbsent(rule.category(), (category) -> new HashMap<>()).put(rule, factory);
    });

    // Build every entry up front and keep them grouped into sections so filtering can show/hide
    // entries without rebuilding them (which would discard in-progress, unsaved edits).
    ruleEntries.entrySet().stream()
        .sorted(Map.Entry.comparingByKey(Comparator.comparing(GameRuleCategory::id)))
        .forEach((categoryEntry) -> {
          CategoryEntry header = this.addEntry(CategoryEntry.factory(categoryEntry.getKey().label(), textRenderer));
          ArrayList<RuleEntry> sectionRules = new ArrayList<>();
          categoryEntry.getValue().entrySet().stream()
              .sorted(Map.Entry.comparingByKey(Comparator.comparing(GameRule::getIdentifier)))
              .forEach((ruleEntry) -> sectionRules.add(this.addEntry(ruleEntry.getValue())));
          this.sections.add(new Section(header, sectionRules));
        });

    this.rulesLoaded = true;
    this.onRuleChange();
    this.applyFilter(this.filter);
  }

  public void applyFilter(String filter) {
    this.filter = filter == null ? "" : filter;
    if (this.rulesLoaded) {
      this.rebuildVisible();
    }
  }

  private void rebuildVisible() {
    this.clearEntries();

    if (this.sections.isEmpty()) {
      this.addEntry(EmptyEntry.factory(EMPTY_TEXT, this.client.font));
      this.arrangeElements();
      return;
    }

    final String query = this.filter.trim().toLowerCase(Locale.ROOT);

    final ArrayList<Entry> visible = new ArrayList<>();
    for (Section section : this.sections) {
      List<RuleEntry> matched = query.isEmpty()
          ? section.rules()
          : section.rules().stream().filter((rule) -> rule.matchesFilter(query)).toList();
      if (!matched.isEmpty()) {
        visible.add(section.header());
        visible.addAll(matched);
      }
    }

    if (visible.isEmpty()) {
      this.addEntry(EmptyEntry.factory(NO_RESULTS_TEXT, this.client.font));
    } else {
      this.entries.addAll(visible);
    }

    this.calculateContentHeight();
    this.arrangeElements();
  }

  private void onRuleChange() {
    // Evaluate every rule (across all sections), not just the ones currently visible under the
    // active search filter, so edits to a filtered-out rule still count toward validity/dirtiness.
    boolean allValid = true;
    boolean anyDirty = false;
    for (Section section : this.sections) {
      for (RuleEntry ruleEntry : section.rules()) {
        if (!ruleEntry.isValid()) {
          allValid = false;
        }
        if (ruleEntry.isDirty()) {
          anyDirty = true;
        }
      }
    }

    this.onRuleChange.accept(allValid, anyDirty);
  }

  public LinkedHashMap<String, Either<Boolean, Integer>> getDirtyValues() {
    LinkedHashMap<String, Either<Boolean, Integer>> values = new LinkedHashMap<>();
    for (Section section : this.sections) {
      for (RuleEntry ruleEntry : section.rules()) {
        if (!ruleEntry.isValid()) {
          return new LinkedHashMap<>();
        }
        if (ruleEntry.isDirty()) {
          values.put(ruleEntry.getId(), ruleEntry.getValue());
        }
      }
    }
    return values;
  }

  @Override
  public void close() {
    this.cancel();
  }

  private record Section(CategoryEntry header, List<RuleEntry> rules) {
  }

  public static class RuleContext {
    private final String id;
    private final Component name;
    private final List<Component> tooltip;
    private final String narrationName;
    private final String searchText;
    private final Either<Boolean, Integer> initialValue;
    private final RuleState state;
    private final Runnable onChange;

    private Either<Boolean, Integer> value;
    private boolean valid = true;

    private RuleContext(
        String id,
        Component name,
        List<Component> tooltip,
        String narrationName,
        String searchText,
        Either<Boolean, Integer> initialValue,
        RuleState state,
        Runnable onChange
    ) {
      this.id = id;
      this.name = name;
      this.tooltip = tooltip;
      this.narrationName = narrationName;
      this.searchText = searchText;
      this.initialValue = initialValue;
      this.state = state;
      this.onChange = onChange;

      this.value = initialValue;
    }

    public static RuleContext of(
        GameRules gameRules,
        GameRule<?> rule,
        RuleState state,
        Date changed,
        Runnable onChange
    ) {
      String id = RuleHelper.idOf(rule);
      Either<Boolean, Integer> value = RuleHelper.getValue(gameRules, rule);

      return new RuleContext(
          id,
          getDisplayName(rule),
          getTooltip(rule, value, state, changed),
          getNarrationName(rule),
          buildSearchText(rule, id),
          value,
          state,
          onChange
      );
    }

    public String getId() {
      return this.id;
    }

    public Component getDisplayName() {
      return this.name;
    }

    public List<Component> getTooltip() {
      return this.tooltip;
    }

    public String getNarrationName() {
      return this.narrationName;
    }

    public boolean matchesFilter(String lowerCaseQuery) {
      return this.searchText.contains(lowerCaseQuery);
    }

    public Either<Boolean, Integer> getValue() {
      return this.value;
    }

    public void setValue(boolean value) {
      if (!this.isMutable()) {
        return;
      }
      this.value = Either.left(value);
    }

    public void setValue(int value) {
      if (!this.isMutable()) {
        return;
      }
      this.value = Either.right(value);
    }

    public boolean isDirty() {
      return !Objects.equals(this.initialValue, this.value);
    }

    public boolean isValid() {
      return this.valid;
    }

    public void setValid(boolean valid) {
      this.valid = valid;
    }

    public boolean isMutable() {
      return this.state.equals(RuleState.MUTABLE);
    }

    public void markChanged() {
      this.onChange.run();
    }

    private static Component getDisplayName(GameRule<?> rule) {
      return Component.translatable(rule.getDescriptionId());
    }

    // Mirrors vanilla's search: rule id, readable name, category label, and description are all
    // searchable via a case-insensitive substring match (see AbstractGameRulesScreen.RuleList).
    private static String buildSearchText(GameRule<?> rule, String id) {
      StringBuilder builder = new StringBuilder();
      builder.append(id).append(' ');
      builder.append(getDisplayName(rule).getString()).append(' ');
      builder.append(rule.category().label().getString());
      getDescription(rule).ifPresent((description) -> builder.append(' ').append(description.getString()));
      return builder.toString().toLowerCase(Locale.ROOT);
    }

    private static List<Component> getTooltip(
        GameRule<?> rule,
        Either<Boolean, Integer> currentValue,
        RuleState state,
        Date changed
    ) {
      ArrayList<Component> lines = new ArrayList<>();

      lines.add(Component.literal(RuleHelper.idOf(rule)).withStyle(ChatFormatting.YELLOW));
      getDescription(rule).ifPresent(lines::add);
      lines.add(getDefaultValueLine(rule));
      lines.add(getCurrentValueLine(rule, currentValue));
      lines.add(getChangedLine(changed));
      getDisabledLine(state).ifPresent(lines::add);

      return lines;
    }

    private static Optional<Component> getDescription(GameRule<?> rule) {
      String descriptionI18nKey = rule.getDescriptionId() + ".description";
      if (Language.getInstance().has(descriptionI18nKey)) {
        return Optional.of(Component.translatable(descriptionI18nKey));
      }
      return Optional.empty();
    }

    private static Component getDefaultValueLine(GameRule<?> rule) {
      return Component.translatable(
              "editGamerule.default",
              Component.literal(RuleHelper.getDefaultValue(rule).map(Object::toString, Object::toString))
          )
          .withStyle(ChatFormatting.GRAY);
    }

    private static Component getCurrentValueLine(GameRule<?> rule, Either<Boolean, Integer> currentValue) {
      Either<Boolean, Integer> defaultValue = RuleHelper.getDefaultValue(rule);
      boolean differentFromDefault = !Objects.equals(defaultValue, currentValue);

      MutableComponent value = Component.literal(currentValue.map(Object::toString, Object::toString));
      if (differentFromDefault) {
        value = value.withStyle(ChatFormatting.GREEN);
      }

      return Component.empty().withStyle(ChatFormatting.GRAY).append(Component.translatable("gamerulesmod.main.current", value));
    }

    private static Component getChangedLine(@Nullable Date changed) {
      Component value = changed == null ?
          Component.translatable("gamerulesmod.main.never").withStyle(ChatFormatting.ITALIC) :
          Component.literal(formatDate(changed)).withStyle(ChatFormatting.AQUA);

      return Component.empty().withStyle(ChatFormatting.GRAY).append(Component.translatable("gamerulesmod.main.date", value));
    }

    private static String formatDate(Date date) {
      return date.toInstant()
          .atZone(ZoneId.systemDefault())
          .toLocalDateTime()
          .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT));
    }

    private static Optional<Component> getDisabledLine(RuleState state) {
      return Optional.ofNullable(switch (state) {
            case LOCKED -> Component.translatable(
                "gamerulesmod.main.immutable.locked",
                Component.translatable("selectWorld.gameMode.hardcore").withStyle(ChatFormatting.RED)
            );
            case DENIED -> Component.translatable("gamerulesmod.main.immutable.denied");
            default -> null;
          })
          .map((text) -> Component.literal("\n")
              .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
              .append(Component.translatable("gamerulesmod.main.immutable", text)));
    }

    private static String getNarrationName(GameRule<?> rule) {
      String defaultLine = getDefaultValueLine(rule).getString();
      return getDescription(rule).map((description) -> description.getString() + "\n" + defaultLine).orElse(defaultLine);
    }
  }

  public abstract static class Entry extends ParentElementEntryListWidget.Entry {
    protected static final int HEIGHT = 20;

    protected final Font textRenderer;

    protected Entry(Font textRenderer, int index, int left, int top, int width) {
      this(textRenderer, index, left, top, width, HEIGHT);
    }

    protected Entry(Font textRenderer, int index, int left, int top, int width, int height) {
      super(index, left, top, width, height);
      this.textRenderer = textRenderer;
    }
  }

  public static class LoadingEntry extends Entry {
    private static final Component LOADING_TEXT = Component.translatable("gamerulesmod.main.loading");

    private final long createTime;
    private final LabelWidget spinner;

    public LoadingEntry(Font textRenderer, int index, int left, int top, int width) {
      super(textRenderer, index, left, top, width, 36);

      this.createTime = Util.getMillis();

      LinearLayoutWidget layout = LinearLayoutWidget.vertical(
              this.getContentLeft(),
              this.getContentTop(),
              this.getContentWidth(),
              this.getContentHeight()
          )
          .mainAxisContentAlignCenter()
          .defaultOffAxisContentAlignCenter();

      LabelWidget label = layout.add(LabelWidget.builder(textRenderer, LOADING_TEXT)
          .showShadow()
          .hideBackground()
          .build());

      this.spinner = layout.add(LabelWidget.builder(textRenderer, getSpinnerText())
          .color(CommonColors.GRAY)
          .showShadow()
          .hideBackground()
          .build());

      this.addLayout(
          layout,
          (self) -> self.setPositionAndDimensions(
              this.getContentLeft(),
              this.getContentTop(),
              this.getContentWidth(),
              this.getContentHeight()
          )
      );

      this.addDrawableChild(label);
      this.addDrawable(this.spinner);
    }

    private Component getSpinnerText() {
      return Component.literal(LoadingDotsText.get(Util.getMillis()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      if (Util.getMillis() - this.createTime < 100) {
        // Prevent flashing by holding off on rendering anything until after 100ms
        return;
      }

      super.extractRenderState(context, mouseX, mouseY, delta);
      this.spinner.setText(this.getSpinnerText());
    }

    public static FlowListWidget.EntryFactory<LoadingEntry> factory(Font textRenderer) {
      return (index, left, top, width) -> new LoadingEntry(textRenderer, index, left, top, width);
    }
  }

  public static class ErrorEntry extends Entry {
    private static final Component ERROR_TEXT_1 = Component.translatable("gamerulesmod.main.error1");
    private static final Component ERROR_TEXT_2 = Component.translatable("gamerulesmod.main.error2");

    public ErrorEntry(Font textRenderer, int index, int left, int top, int width) {
      super(textRenderer, index, left, top, width, 36);

      LinearLayoutWidget layout = LinearLayoutWidget.vertical(
              this.getContentLeft(),
              this.getContentTop(),
              this.getContentWidth(),
              this.getContentHeight()
          )
          .mainAxisContentAlignCenter()
          .defaultOffAxisContentAlignCenter();

      layout.add(LabelWidget.builder(textRenderer, List.of(ERROR_TEXT_1, ERROR_TEXT_2))
          .color(CommonColors.RED)
          .alignTextCenterX()
          .alignTextCenterY()
          .showShadow()
          .hideBackground()
          .build());

      this.addLayout(
          layout,
          (self) -> self.setPositionAndDimensions(
              this.getContentLeft(),
              this.getContentTop(),
              this.getContentWidth(),
              this.getContentHeight()
          )
      );

      layout.visitChildren((child) -> {
        if (child instanceof AbstractWidget widget) {
          this.addDrawableChild(widget);
        }
      });
    }

    public static FlowListWidget.EntryFactory<ErrorEntry> factory(Font textRenderer) {
      return (index, left, top, width) -> new ErrorEntry(textRenderer, index, left, top, width);
    }
  }

  public static class EmptyEntry extends Entry {
    public EmptyEntry(Component text, Font textRenderer, int index, int left, int top, int width) {
      super(textRenderer, index, left, top, width, 36);

      LinearLayoutWidget layout = LinearLayoutWidget.vertical(
              this.getContentLeft(),
              this.getContentTop(),
              this.getContentWidth(),
              this.getContentHeight()
          )
          .mainAxisContentAlignCenter()
          .defaultOffAxisContentAlignCenter();

      layout.add(LabelWidget.builder(textRenderer, text)
          .alignTextCenterX()
          .alignTextCenterY()
          .overflowBehavior(LabelWidget.OverflowBehavior.SCROLL)
          .showShadow()
          .hideBackground()
          .build());

      this.addLayout(
          layout,
          (self) -> self.setPositionAndDimensions(
              this.getContentLeft(),
              this.getContentTop(),
              this.getContentWidth(),
              this.getContentHeight()
          )
      );

      layout.visitChildren((child) -> {
        if (child instanceof AbstractWidget widget) {
          this.addDrawableChild(widget);
        }
      });
    }

    public static FlowListWidget.EntryFactory<EmptyEntry> factory(Component text, Font textRenderer) {
      return (index, left, top, width) -> new EmptyEntry(text, textRenderer, index, left, top, width);
    }
  }

  public static class CategoryEntry extends Entry {
    public CategoryEntry(Component text, Font textRenderer, int index, int left, int top, int width) {
      super(textRenderer, index, left, top, width);

      LinearLayoutWidget layout = LinearLayoutWidget.vertical(
              this.getContentLeft(),
              this.getContentTop(),
              this.getContentWidth(),
              this.getContentHeight()
          )
          .mainAxisContentAlignCenter()
          .defaultOffAxisContentAlignCenter();

      layout.add(LabelWidget.builder(textRenderer, text.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW))
          .alignTextCenterX()
          .alignTextCenterY()
          .overflowBehavior(LabelWidget.OverflowBehavior.SCROLL)
          .showShadow()
          .hideBackground()
          .build());

      this.addLayout(
          layout,
          (self) -> self.setPositionAndDimensions(
              this.getContentLeft(),
              this.getContentTop(),
              this.getContentWidth(),
              this.getContentHeight()
          )
      );

      layout.visitChildren((child) -> {
        if (child instanceof AbstractWidget widget) {
          this.addDrawableChild(widget);
        }
      });
    }

    @Override
    protected void renderBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      renderRowShade(
          context,
          this.getContentLeft(),
          this.getContentTop(),
          this.getContentRight(),
          this.getContentBottom(),
          DEFAULT_SHADE_FADE_WIDTH,
          DEFAULT_SHADE_STRENGTH
      );
    }

    public static FlowListWidget.EntryFactory<CategoryEntry> factory(Component text, Font textRenderer) {
      return (index, left, top, width) -> new CategoryEntry(text, textRenderer, index, left, top, width);
    }
  }

  public abstract static class RuleEntry extends Entry {
    protected static final int CONTROL_MIN_WIDTH = 100;

    protected final RuleContext context;

    protected RuleEntry(RuleContext context, Font textRenderer, int index, int left, int top, int width) {
      super(textRenderer, index, left, top, width);

      this.context = context;

      this.createAndAddTooltip();
      this.createAndAddContent();
    }

    protected abstract AbstractWidget createControlWidget();

    public boolean isDirty() {
      return this.context.isDirty();
    }

    public boolean isValid() {
      return this.context.isValid();
    }

    public boolean matchesFilter(String lowerCaseQuery) {
      return this.context.matchesFilter(lowerCaseQuery);
    }

    public String getId() {
      return this.context.getId();
    }

    public Either<Boolean, Integer> getValue() {
      return this.context.getValue();
    }

    private void createAndAddTooltip() {
      NonPositioningLayoutWidget layout = new NonPositioningLayoutWidget(
          this.getContentLeft(),
          this.getContentTop(),
          this.getContentWidth(),
          this.getContentHeight()
      );
      TooltipWidget tooltip = layout.add(
          new TooltipWidget(this.context.getTooltip()),
          (parent, self) -> self.setDimensionsAndPosition(
              parent.getWidth(),
              parent.getHeight(),
              parent.getX(),
              parent.getY()
          )
      );
      this.addLayout(
          layout,
          (self) -> self.setPositionAndDimensions(
              this.getContentLeft(),
              this.getContentTop(),
              this.getContentWidth(),
              this.getContentHeight()
          )
      );
      this.addDrawable(tooltip);
    }

    protected void createAndAddContent() {
      LinearLayoutWidget layout = LinearLayoutWidget.horizontal(
              this.getContentLeft(),
              this.getContentTop(),
              this.getContentWidth(),
              this.getContentHeight()
          )
          .spacing(GuiUtil.PADDING)
          .defaultOffAxisContentAlignCenter();

      layout.add(
          LabelWidget.builder(this.textRenderer, this.context.getDisplayName())
              .alignTextLeft()
              .alignTextCenterY()
              .overflowBehavior(LabelWidget.OverflowBehavior.WRAP)
              .maxLines(2)
              .showShadow()
              .hideBackground()
              .build(), (parent, self) -> self.setSize(this.getLabelWidth(parent), this.getContentHeight())
      );

      layout.add(
          this.createControlWidget(),
          (parent, self) -> self.setSize(this.getControlWidth(parent), this.getContentHeight())
      );

      this.addLayout(
          layout,
          (self) -> self.setPositionAndDimensions(
              this.getContentLeft(),
              this.getContentTop(),
              this.getContentWidth(),
              this.getContentHeight()
          )
      );
      layout.visitChildren((child) -> {
        if (child instanceof AbstractWidget widget) {
          this.addDrawableChild(widget);
        }
      });
    }

    protected int getLabelWidth(LinearLayoutWidget layout) {
      return layout.getWidth() - 2 * layout.getSpacing() - this.getControlWidth(layout) - IconButtonWidget.SIZE_V;
    }

    protected int getControlWidth(LinearLayoutWidget layout) {
      return Math.max(CONTROL_MIN_WIDTH, Math.round(layout.getWidth() * 0.25f));
    }
  }

  public static class BooleanRuleEntry extends RuleEntry {
    protected BooleanRuleEntry(
        RuleContext context,
        Font textRenderer,
        int index,
        int left,
        int top,
        int width
    ) {
      super(context, textRenderer, index, left, top, width);
    }

    public Boolean getBooleanValue() {
      return this.getValue().left().orElseThrow();
    }

    @Override
    protected AbstractWidget createControlWidget() {
      var widget = CycleButton.onOffBuilder(this.getBooleanValue())
          .displayOnlyValue()
          .withCustomNarration((button) -> button.createDefaultNarrationMessage()
              .append("\n")
              .append(this.context.getNarrationName()))
          .create(
              0, 0, 1, 1, this.context.getDisplayName(), (button, value) -> {
                this.context.setValue(value);
                this.context.markChanged();
              }
          );
      widget.active = this.context.isMutable();
      return widget;
    }

    public static FlowListWidget.EntryFactory<BooleanRuleEntry> factory(
        GameRules gameRules,
        GameRule<?> rule,
        RuleState state,
        Date changed,
        Runnable onChange,
        Font textRenderer
    ) {
      return (index, left, top, width) -> new BooleanRuleEntry(
          RuleContext.of(gameRules, rule, state, changed, onChange),
          textRenderer,
          index,
          left,
          top,
          width
      );
    }
  }

  public static class IntRuleEntry extends RuleEntry {
    protected IntRuleEntry(RuleContext context, Font textRenderer, int index, int left, int top, int width) {
      super(context, textRenderer, index, left, top, width);
    }

    public Integer getIntValue() {
      return this.getValue().right().orElseThrow();
    }

    @Override
    protected AbstractWidget createControlWidget() {
      var widget = new EditBox(
          this.textRenderer,
          CONTROL_MIN_WIDTH,
          20,
          this.context.getDisplayName().copy().append(this.context.getNarrationName()).append("\n")
      );
      widget.setValue(Integer.toString(IntRuleEntry.this.getIntValue()));
      widget.setTextColorUneditable(10526880); // From PressableWidget
      widget.setResponder((value) -> {
        int previousValue = IntRuleEntry.this.getIntValue();
        boolean previousValid = this.context.isValid();

        try {
          int parsed = Integer.parseInt(value);
          this.context.setValue(parsed);
          this.context.setValid(true);
          widget.setTextColor(GuiUtil.LABEL_COLOR);
        } catch (Exception e) {
          this.context.setValid(false);
          widget.setTextColor(GuiUtil.ERROR_COLOR);
        }

        if (previousValue != IntRuleEntry.this.getIntValue() || previousValid != this.context.isValid()) {
          this.context.markChanged();
        }
      });
      widget.active = this.context.isMutable();
      return widget;
    }

    public static FlowListWidget.EntryFactory<IntRuleEntry> factory(
        GameRules gameRules,
        GameRule<?> rule,
        RuleState state,
        Date changed,
        Runnable onChange,
        Font textRenderer
    ) {
      return (index, left, top, width) -> new IntRuleEntry(
          RuleContext.of(gameRules, rule, state, changed, onChange),
          textRenderer,
          index,
          left,
          top,
          width
      );
    }
  }
}
