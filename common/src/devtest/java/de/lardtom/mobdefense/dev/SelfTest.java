package de.lardtom.mobdefense.dev;

import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class SelfTest {
    public static final String PROPERTY = "mobdefense.selftest";
    public static final String SMOKE_PROPERTY = "mobdefense.smoketest";
    public static final String ONLY_PROPERTY = "mobdefense.selftest.only";
    public static final String ONLY_ENV = "MOBDEFENSE_SELFTEST_ONLY";
    public static final String PREFIX = "mobdefense: ";
    public static final String BASE_MODULE = "devtest";

    private static final Pattern MODULE = Pattern.compile("[a-z0-9_]{1,32}");
    private static final List<Step> STEPS = new ArrayList<>();
    private static final Set<String> NAMES = new HashSet<>();
    private static boolean started;

    private SelfTest() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static boolean smokeEnabled() {
        return !enabled() && Boolean.getBoolean(SMOKE_PROPERTY);
    }

    public static Steps steps(String module) {
        if (!MODULE.matcher(module).matches()) {
            throw new IllegalArgumentException("Ungültiger Modulname: " + module);
        }
        return new Steps(module, false);
    }

    public static Steps baseSteps() {
        return new Steps(BASE_MODULE, true);
    }

    public static String fullName(String what) {
        String trimmed = what.trim();
        return trimmed.regionMatches(true, 0, PREFIX, 0, PREFIX.length()) ? PREFIX + trimmed.substring(PREFIX.length()).trim() : PREFIX + trimmed;
    }

    static synchronized void add(Step step) {
        if (started) {
            throw new IllegalStateException("Selbsttest-Schritt '" + step.name() + "' zu spät angemeldet");
        }
        if (!NAMES.add(step.name().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Doppelter Selbsttest-Schritt: " + step.name());
        }
        STEPS.add(step);
    }

    public static synchronized List<Step> all() {
        return List.copyOf(STEPS);
    }

    public static @Nullable String filter() {
        String only = System.getProperty(ONLY_PROPERTY);
        if (only == null || only.isBlank()) {
            only = System.getenv(ONLY_ENV);
        }
        return only == null || only.isBlank() ? null : only.trim();
    }

    public static synchronized List<Step> startSteps() {
        started = true;
        return select(STEPS, filter());
    }

    public static List<Step> select(List<Step> steps, @Nullable String filter) {
        List<Step> ordered = new ArrayList<>();
        steps.stream().filter(step -> !step.base()).forEach(ordered::add);
        steps.stream().filter(Step::base).forEach(ordered::add);
        Selection selection = Selection.parse(filter);
        boolean any = ordered.stream().anyMatch(step -> !step.base() && selection.chosen(step));
        List<Step> kept = new ArrayList<>();
        for (Step step : ordered) {
            if (step.base() ? any : selection.chosen(step)) {
                kept.add(step);
            }
        }
        return List.copyOf(kept);
    }

    record Selection(List<String> include, List<String> exclude) {
        static Selection parse(@Nullable String filter) {
            List<String> include = new ArrayList<>();
            List<String> exclude = new ArrayList<>();
            if (filter != null) {
                for (String token : filter.split("[,|]")) {
                    String value = token.trim();
                    boolean excluded = value.startsWith("!");
                    if (excluded) {
                        value = value.substring(1).trim();
                    }
                    if (!value.isEmpty()) {
                        (excluded ? exclude : include).add(value);
                    }
                }
            }
            return new Selection(List.copyOf(include), List.copyOf(exclude));
        }

        boolean chosen(Step step) {
            boolean included = this.include.isEmpty() || this.include.stream().anyMatch(token -> matches(step, token));
            return included && this.exclude.stream().noneMatch(token -> matches(step, token));
        }

        private static boolean matches(Step step, String token) {
            if (token.endsWith("/")) {
                return step.module().equalsIgnoreCase(token.substring(0, token.length() - 1).trim());
            }
            return startsWith(step.name(), token) || startsWith(step.shortName(), token);
        }

        private static boolean startsWith(String name, String prefix) {
            return name.regionMatches(true, 0, prefix, 0, prefix.length());
        }
    }

    public static Map<String, Object> newState() {
        return new HashMap<>();
    }

    public static final class Steps {
        private final String module;
        private final boolean base;

        private Steps(String module, boolean base) {
            this.module = module;
            this.base = base;
        }

        public String module() {
            return this.module;
        }

        public Steps add(String what, int maxTicks, ClientStep body) {
            return this.add(what, maxTicks, body, ctx -> StepResult.pass());
        }

        public Steps add(String what, int maxTicks, ClientStep body, ClientStep cleanup) {
            Objects.requireNonNull(what, "what");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(cleanup, "cleanup");
            if (maxTicks <= 0) {
                throw new IllegalArgumentException("maxTicks muss > 0 sein");
            }
            SelfTest.add(new Step(this.module, fullName(what), maxTicks, body, cleanup, this.base));
            return this;
        }
    }

    public record Step(String module, String name, int maxTicks, ClientStep body, ClientStep cleanup, boolean base) {
        public String shortName() {
            return this.name.startsWith(PREFIX) ? this.name.substring(PREFIX.length()) : this.name;
        }
    }

    @FunctionalInterface
    public interface ClientStep {
        StepResult tick(StepContext context);
    }

    public interface StepContext {
        int tick();

        String name();

        @Nullable MinecraftServer server();

        CompletableFuture<Void> onServer(Consumer<MinecraftServer> action);

        Map<String, Object> state();

        void log(String message);
    }

    public record StepResult(Status status, String message) {
        public enum Status { PENDING, PASS, FAIL }

        private static final StepResult PENDING = new StepResult(Status.PENDING, "");
        private static final StepResult PASS = new StepResult(Status.PASS, "");

        public static StepResult pending() {
            return PENDING;
        }

        public static StepResult pass() {
            return PASS;
        }

        public static StepResult pass(String message) {
            return new StepResult(Status.PASS, message);
        }

        public static StepResult fail(String reason) {
            return new StepResult(Status.FAIL, reason);
        }

        public static StepResult check(boolean condition, String reason) {
            return condition ? PASS : fail(reason);
        }

        public boolean done() {
            return this.status != Status.PENDING;
        }

        public boolean failed() {
            return this.status == Status.FAIL;
        }
    }
}
