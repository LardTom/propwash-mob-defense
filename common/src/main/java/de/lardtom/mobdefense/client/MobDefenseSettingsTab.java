package de.lardtom.mobdefense.client;

import de.lardtom.mobdefense.dispenser.core.FireCadence;
import de.lardtom.mobdefense.rules.MobDefenseRules;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class MobDefenseSettingsTab extends ConfigTab {
    public static final String ID = "mobdefense:settings";
    public static final int ORDER = 1120;
    public static final int SLIDER_WIDTH = 96;
    public static final String PREFIX = "mobdefense.settings.";
    public static final String TITLE = PREFIX + "title";
    public static final String SECTION = PREFIX + "dispenser";
    public static final String DELAY = PREFIX + "dispenser.delay";
    public static final String DELAY_TOOLTIP = PREFIX + "dispenser.delay.tooltip";
    public static final String INTERVAL = PREFIX + "dispenser.interval";
    public static final String INTERVAL_TOOLTIP = PREFIX + "dispenser.interval.tooltip";
    public static final String MILLIS = PREFIX + "dispenser.millis";
    public static final String RULE = PREFIX + "dispenser.rule";
    public static final String FILE = PREFIX + "file";
    private static boolean registered;
    private static @Nullable MobDefenseSettingsTab active;

    private int delayTicks;
    private int intervalTicks;
    private @Nullable StepSlider delaySlider;
    private @Nullable StepSlider intervalSlider;
    private int sectionY;
    private int delayY;
    private int intervalY;
    private int sliderWidth;
    private int ruleY;

    public MobDefenseSettingsTab() {
        this.read(ClientSettings.get().cadence());
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PropwashClientApi.registerConfigTab(MobDefenseSettingsTab::new);
        MobDefenseClient.onClientTick(CadenceSync::tick);
    }

    public static @Nullable MobDefenseSettingsTab active() {
        return active;
    }

    public static Component millis(double ticks) {
        return Component.translatable(MILLIS, FireCadence.millis((int) Math.round(ticks)));
    }

    public static int ruleTicks() {
        return Math.max(1, MobDefenseRules.number(Minecraft.getInstance().level, RuleDefaults.DISPENSER_COOLDOWN_TICKS));
    }

    private void read(FireCadence.Setting setting) {
        this.delayTicks = setting.delayTicks();
        this.intervalTicks = setting.intervalTicks();
    }

    public FireCadence.Setting setting() {
        return new FireCadence.Setting(this.delayTicks, this.intervalTicks);
    }

    public @Nullable StepSlider delaySlider() {
        return this.delaySlider;
    }

    public @Nullable StepSlider intervalSlider() {
        return this.intervalSlider;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable(TITLE);
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    protected void build() {
        active = this;
        int left = this.x + Metrics.PADDING;
        int width = Math.max(0, this.width - 2 * Metrics.PADDING);
        int labelNeed = Math.max(this.host.font().width(Component.translatable(DELAY)), this.host.font().width(Component.translatable(INTERVAL)))
                + Metrics.LABEL_GAP;
        this.sliderWidth = Math.clamp(width - labelNeed, Metrics.CONTROL_MIN, SLIDER_WIDTH);
        int sliderX = left + width - this.sliderWidth;
        this.sectionY = this.y + Metrics.PADDING;
        this.delayY = this.sectionY + Metrics.SECTION_HEIGHT + Metrics.LABEL_GAP;
        this.delaySlider = this.host.addWidget(slider(sliderX, this.delayY, this.delayTicks, this::delayChanged));
        this.delaySlider.setTooltip(Tooltip.create(Component.translatable(DELAY_TOOLTIP)));
        this.intervalY = this.delayY + Metrics.CONTROL_HEIGHT + Metrics.GAP;
        this.intervalSlider = this.host.addWidget(slider(sliderX, this.intervalY, this.intervalTicks, this::intervalChanged));
        this.intervalSlider.setTooltip(Tooltip.create(Component.translatable(INTERVAL_TOOLTIP, FireCadence.millis(FireCadence.MIN_TICKS))));
        this.ruleY = this.intervalY + Metrics.CONTROL_HEIGHT + Metrics.GAP;
        this.reportBottom(this.ruleY + 3 * Metrics.LINE_HEIGHT);
    }

    private StepSlider slider(int sliderX, int sliderY, int ticks, java.util.function.DoubleConsumer onChange) {
        StepSlider slider = new StepSlider(sliderX, sliderY, this.sliderWidth, Metrics.CONTROL_HEIGHT, FireCadence.MIN_TICKS, FireCadence.MAX_TICKS, 1.0, ticks,
                MobDefenseSettingsTab::millis, onChange);
        return slider.display(FireCadence.MILLIS_PER_TICK, 0.0);
    }

    private void delayChanged(double ticks) {
        this.delayTicks = FireCadence.clamp((int) Math.round(ticks));
        this.host.changed();
    }

    private void intervalChanged(double ticks) {
        this.intervalTicks = FireCadence.clamp((int) Math.round(ticks));
        this.host.changed();
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = this.x + Metrics.PADDING;
        int width = Math.max(0, this.width - 2 * Metrics.PADDING);
        int labelWidth = Math.max(0, width - this.sliderWidth - Metrics.LABEL_GAP);
        ThemeDraw.sectionTitle(g, this.host.font(), Component.translatable(SECTION), left, this.sectionY, width);
        ThemeText.fitted(g, this.host.font(), Component.translatable(DELAY), left, Metrics.textY(this.delayY, Metrics.CONTROL_HEIGHT), labelWidth, Palette.TEXT);
        ThemeText.fitted(g, this.host.font(), Component.translatable(INTERVAL), left, Metrics.textY(this.intervalY, Metrics.CONTROL_HEIGHT), labelWidth,
                Palette.TEXT);
        int rule = ruleTicks();
        boolean raised = rule > Math.min(this.delayTicks, this.intervalTicks);
        int used = ThemeText.wrapped(g, this.host.font(), Component.translatable(RULE, FireCadence.millis(Math.max(rule, FireCadence.MIN_TICKS))), left,
                this.ruleY, width, raised ? Palette.WARNING : Palette.TEXT_FAINT);
        int fileY = this.ruleY + used + Metrics.LABEL_GAP;
        int fileUsed = ThemeText.wrapped(g, this.host.font(), Component.translatable(FILE, ClientSettings.FILE_NAME), left, fileY, width, Palette.TEXT_FAINT);
        this.reportBottom(fileY + fileUsed);
    }

    @Override
    public void resetDefaults() {
        this.read(FireCadence.Setting.DEFAULT);
        if (this.delaySlider != null) {
            this.delaySlider.setCurrent(this.delayTicks);
        }
        if (this.intervalSlider != null) {
            this.intervalSlider.setCurrent(this.intervalTicks);
        }
        this.host.changed();
    }

    @Override
    public void save() {
        if (ClientSettings.get().cadence(this.setting())) {
            CadenceSync.changed();
        }
    }

    @Override
    public void discard() {
        this.read(ClientSettings.get().cadence());
    }
}
