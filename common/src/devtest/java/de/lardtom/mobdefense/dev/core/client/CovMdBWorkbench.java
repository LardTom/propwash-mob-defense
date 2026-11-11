package de.lardtom.mobdefense.dev.core.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.core.CoreBuild;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.client.WorkbenchExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class CovMdBWorkbench {
    private static final AtomicReference<@Nullable WorkbenchContext> CONTEXT = new AtomicReference<>();
    private static boolean registered;

    private CovMdBWorkbench() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PropwashClientApi.registerWorkbenchExtension(new WorkbenchExtension() {
            @Override
            public Identifier id() {
                return MobDefense.id("selftest_cov_md_b_workbench");
            }

            @Override
            public void init(WorkbenchContext context) {
                CONTEXT.set(context);
            }
        });
    }

    public static @Nullable WorkbenchContext context() {
        return CONTEXT.get();
    }

    public static void open(MinecraftServer server, BlockPos pos, BuildSpec spec) {
        ServerPlayer player = Arena.player(server);
        player.closeContainer();
        ServerLevel level = player.level();
        Arena.place(level, pos, BuiltInRegistries.BLOCK.getValue(CoreBuild.propwash("drone_workbench")).defaultBlockState());
        MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
        if (provider == null || player.openMenu(provider).isEmpty()) {
            throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
        }
        CONTEXT.set(null);
        ImportResult imported = PropwashApi.builds().applyImport(player, ImportRequest.wholeBuild(DroneData.of(spec)));
        if (!imported.status().success()) {
            throw new IllegalStateException("Bau nicht in die Werkbank übernommen: " + imported);
        }
    }

    public static StepResult shown(SelfTest.StepContext ctx, int ticks, BuildSpec spec) {
        Minecraft mc = Minecraft.getInstance();
        WorkbenchContext context = CONTEXT.get();
        if (context == null || context.screen() != mc.gui.screen() || context.result().isEmpty()
                || !context.result().get().build().accessories().equals(spec.accessories())) {
            return ticks > 400 ? StepResult.fail("Werkbank zeigt den Bau nicht: " + mc.gui.screen()) : StepResult.pending();
        }
        return StepResult.pass("Werkbank zeigt " + context.result().get().build().accessories());
    }

    public static List<AbstractWidget> widgets(Screen screen, Predicate<AbstractWidget> filter) {
        List<AbstractWidget> out = new ArrayList<>();
        collect(screen.children(), filter, out);
        return out;
    }

    private static void collect(List<? extends GuiEventListener> children, Predicate<AbstractWidget> filter, List<AbstractWidget> out) {
        for (GuiEventListener child : children) {
            if (child instanceof AbstractWidget widget && filter.test(widget)) {
                out.add(widget);
            }
            if (child instanceof ContainerEventHandler container) {
                collect(container.children(), filter, out);
            }
        }
    }

    public static void click(AbstractWidget widget) {
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        widget.onClick(new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)), false);
    }
}
