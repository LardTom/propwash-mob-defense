package de.lardtom.mobdefense.dev;

public interface DevModule {
    String name();

    default void serverChecks(ServerChecks checks) {
    }

    default void clientSteps(SelfTest.Steps steps) {
    }
}
