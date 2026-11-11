package de.lardtom.mobdefense.spear;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.mobdefense.spear.core.SpearPose;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpearGuideTipTest {
    private static final Pattern CENTIMETRES = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*cm\\b");
    private static final Pattern TIP_BEFORE_NOSE_EN = Pattern.compile("(?i)tip[^.;–]*in front of the nose");
    private static final Pattern TIP_BEFORE_NOSE_DE = Pattern.compile("(?i)spitze[^.;–]*vor (?:die|der) nase");

    static long tipCentimetres() {
        return Math.round(SpearPose.TIP_FORWARD_MM / 10.0);
    }

    static String section(String file, String heading) throws IOException {
        String text = MdTxtPlayerTextsTest.raw(file);
        int start = text.indexOf(heading);
        assertTrue(start >= 0, file + ": Abschnitt „" + heading + "“ fehlt");
        int end = text.indexOf("\n---", start);
        return text.substring(start, end < 0 ? text.length() : end).replaceAll("\\s+", " ");
    }

    static List<Double> centimetres(String text) {
        List<Double> out = new ArrayList<>();
        Matcher matcher = CENTIMETRES.matcher(text);
        while (matcher.find()) {
            out.add(Double.parseDouble(matcher.group(1).replace(',', '.')));
        }
        return out;
    }

    static JsonObject frame(String id) throws IOException {
        String path = "data/propwash/propwash_parts/frame/" + id + ".json";
        try (InputStream in = SpearGuideTipTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    static double tipForwardOfCentreMm(JsonObject frame) {
        JsonObject bottom = frame.getAsJsonObject("accessory_anchors").getAsJsonObject("bottom");
        JsonArray pos = bottom.getAsJsonArray("pos");
        double tilt = Math.toRadians(bottom.get("tilt_deg").getAsDouble());
        double localUp = SpearPose.TIP_UP_MM;
        double localZ = -SpearPose.TIP_FORWARD_MM;
        double frameZ = pos.get(2).getAsDouble() + localUp * Math.sin(tilt) + localZ * Math.cos(tilt);
        return -frameZ;
    }

    static double frontPropEdgeMm(JsonObject frame) {
        assertEquals("true_x", frame.get("shape").getAsString());
        return frame.get("wheelbase_mm").getAsDouble() / 2.0 / Math.sqrt(2.0) + frame.get("max_prop_mm").getAsDouble() / 2.0;
    }

    @Test
    void guideNamesTheTipWhereTheGameDrawsAndChecksIt() throws IOException {
        long cm = tipCentimetres();
        assertEquals(10, cm);
        String english = section("docs/guide.md", "## 4. Spear mount");
        String german = section("docs/guide.de.md", "## 4. Speer-Halterung");
        assertFalse(TIP_BEFORE_NOSE_EN.matcher(english).find(), "Anleitung EN: Spitze „in front of the nose“: " + english);
        assertFalse(TIP_BEFORE_NOSE_DE.matcher(german).find(), "Anleitung DE: Spitze „vor die Nase“: " + german);
        for (String text : List.of(english, german)) {
            List<Double> values = centimetres(text);
            assertFalse(values.isEmpty(), "Lage der Spitze fehlt: " + text);
            for (double value : values) {
                assertEquals(SpearPose.TIP_FORWARD_MM / 10.0, value, 0.5, "Spitze im Spiel " + SpearPose.TIP_FORWARD_MM + " mm vor der Mitte der Halterung, Text: " + values);
            }
        }
        assertTrue(english.contains("its tip sits about " + cm + " cm in front of the middle of the mount – on a 5″ about level with the "
                + "front props, on a 7″ the props reach further forward."), english);
        assertTrue(german.contains("die Spitze liegt etwa " + cm + " cm vor der Mitte der Halterung – an einer 5″ etwa auf Höhe der "
                + "vorderen Propeller, an einer 7″ ragen die Propeller weiter nach vorn."), german);
    }

    @Test
    void frameComparisonsInTheGuideMatchTheFrames() throws IOException {
        JsonObject five = frame("freestyle_5");
        JsonObject seven = frame("long_range_7");
        double fiveTip = tipForwardOfCentreMm(five);
        double sevenTip = tipForwardOfCentreMm(seven);
        assertEquals(0.0, fiveTip - frontPropEdgeMm(five), 10.0, "5″: Spitze " + fiveTip + " mm, Propeller " + frontPropEdgeMm(five) + " mm");
        assertTrue(frontPropEdgeMm(seven) - sevenTip >= 20.0, "7″: Spitze " + sevenTip + " mm, Propeller " + frontPropEdgeMm(seven) + " mm");
        assertEquals(150.0, sevenTip, 5.0, "docs/spear.md §4 Punkt 14: Spitze an der 7″ etwa 0,15 m vor der Mitte");
        assertEquals(190.0, frontPropEdgeMm(seven), 5.0, "docs/spear.md §4 Punkt 14: Hülle der 7″ 0,19 m");
    }
}
