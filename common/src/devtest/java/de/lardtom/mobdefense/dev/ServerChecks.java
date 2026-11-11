package de.lardtom.mobdefense.dev;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ServerChecks {
    private final List<Check> checks = new ArrayList<>();
    private String module = SelfTest.BASE_MODULE;

    @FunctionalInterface
    public interface ServerCheck {
        String run(MinecraftServer server) throws Exception;
    }

    public record Check(String module, String name, int tick, ServerCheck body) {
    }

    ServerChecks module(String name) {
        this.module = name;
        return this;
    }

    public ServerChecks add(String name, ServerCheck body) {
        return this.add(name, 0, body);
    }

    public ServerChecks add(String name, int tick, ServerCheck body) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(body, "body");
        if (tick < 0 || tick > ServerSmoke.RUN_TICKS - 10) {
            throw new IllegalArgumentException(name + ": Tick " + tick + " außerhalb von 0 bis " + (ServerSmoke.RUN_TICKS - 10));
        }
        this.checks.add(new Check(this.module, name, tick, body));
        return this;
    }

    public List<Check> all() {
        return List.copyOf(this.checks);
    }
}
