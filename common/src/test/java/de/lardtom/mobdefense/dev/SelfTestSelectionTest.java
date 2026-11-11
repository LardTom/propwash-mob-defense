package de.lardtom.mobdefense.dev;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfTestSelectionTest {
    private static final SelfTest.ClientStep NOTHING = ctx -> SelfTest.StepResult.pass();
    private static final List<SelfTest.Step> STEPS = List.of(
            step("devtest", "Aufräumen", true),
            step("core", "Teile und Rezepte", false),
            step("core", "Welt-Regeln im Tab", false),
            step("dispenser", "Dispenser beladen und feuern", false),
            step("dispenser", "Eimer gesperrt", false));

    private static SelfTest.Step step(String module, String what, boolean base) {
        return new SelfTest.Step(module, SelfTest.fullName(what), 100, NOTHING, NOTHING, base);
    }

    private static List<String> names(String filter) {
        return SelfTest.select(STEPS, filter).stream().map(SelfTest.Step::shortName).toList();
    }

    @Test
    void namesStartWithTheModPrefixOnce() {
        assertEquals("mobdefense: Teile", SelfTest.fullName("Teile"));
        assertEquals("mobdefense: Teile", SelfTest.fullName("mobdefense: Teile"));
        assertEquals("mobdefense: Teile", SelfTest.fullName("  MobDefense:   Teile "));
    }

    @Test
    void withoutFilterAllStepsRunAndBaseStepsComeLast() {
        assertEquals(List.of("Teile und Rezepte", "Welt-Regeln im Tab", "Dispenser beladen und feuern", "Eimer gesperrt", "Aufräumen"), names(null));
        assertEquals(names(null), names("  "));
    }

    @Test
    void prefixesMatchWithOrWithoutModPrefixAndIgnoreCase() {
        assertEquals(List.of("Dispenser beladen und feuern", "Aufräumen"), names("dispenser"));
        assertEquals(List.of("Dispenser beladen und feuern", "Aufräumen"), names("mobdefense: Dispenser"));
        assertEquals(List.of("Teile und Rezepte", "Eimer gesperrt", "Aufräumen"), names("Teile,eimer"));
        assertEquals(List.of("Teile und Rezepte", "Eimer gesperrt", "Aufräumen"), names("Teile|eimer"));
    }

    @Test
    void moduleFolderSelectsAllStepsOfTheModule() {
        assertEquals(List.of("Teile und Rezepte", "Welt-Regeln im Tab", "Aufräumen"), names("core/"));
        assertEquals(List.of("Dispenser beladen und feuern", "Eimer gesperrt", "Aufräumen"), names("dispenser/"));
    }

    @Test
    void exclusionsRemoveSteps() {
        assertEquals(List.of("Teile und Rezepte", "Welt-Regeln im Tab", "Dispenser beladen und feuern", "Aufräumen"), names("!Eimer"));
        assertEquals(List.of("Welt-Regeln im Tab", "Aufräumen"), names("core/,!Teile"));
    }

    @Test
    void baseStepsAloneNeverRun() {
        assertTrue(names("gibt es nicht").isEmpty());
        assertTrue(names("Aufräumen").isEmpty());
    }

    @Test
    void everyModuleHasItsOwnName() {
        List<DevModule> modules = DevModules.all();
        Set<String> names = modules.stream().map(DevModule::name).collect(Collectors.toSet());
        assertEquals(modules.size(), names.size());
        assertTrue(names.contains("probe"));
        assertFalse(names.contains(SelfTest.BASE_MODULE));
        for (String name : names) {
            assertTrue(name.matches("[a-z0-9_]{1,32}"), name);
        }
    }

    @Test
    void classLogLinesAreParsedAndClientClassesFound() {
        assertEquals("java.lang.Object", ClassLoadScan.className("java.lang.Object source: shared objects file"));
        assertEquals("net.minecraft.client.Minecraft", ClassLoadScan.className("[0.512s][info][class,load] net.minecraft.client.Minecraft source: file:/x.jar"));
        assertEquals(null, ClassLoadScan.className("ohne Quelle"));
        assertTrue(ClassLoadScan.client("net.minecraft.client.Minecraft"));
        assertTrue(ClassLoadScan.client("com.mojang.blaze3d.systems.RenderSystem"));
        assertTrue(ClassLoadScan.client("de.lardtom.mobdefense.client.MobDefenseClient"));
        assertTrue(ClassLoadScan.client("de.lardtom.mobdefense.dispenser.client.DispenserOsd"));
        assertFalse(ClassLoadScan.client("de.lardtom.mobdefense.MobDefense"));
        assertFalse(ClassLoadScan.client("net.minecraft.server.MinecraftServer"));
        assertFalse(ClassLoadScan.client("de.lardtom.mobdefense.clientele.Data"));
    }
}
