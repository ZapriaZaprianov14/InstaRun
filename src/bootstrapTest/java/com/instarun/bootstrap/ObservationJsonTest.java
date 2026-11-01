package com.instarun.bootstrap;

import com.instarun.bootstrap.fixtures.ResultTargets;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ObservationJsonTest {

    @Test
    public void writesAnObjectByItsPropertiesNamedAfterTheGetters() {
        Map<String, Observation> properties = new LinkedHashMap<>();
        properties.put("getName", literal("Ann"));
        properties.put("isPaid", literal(true));
        properties.put("getURL", literal("http://a"));
        properties.put("x", literal(1));
        properties.put("getStatus", new Observation.EnumConstant("a.Status", "PAID"));
        properties.put("getPlaced", new Observation.Text("java.time.LocalDate", "2024-01-02"));
        properties.put("getNote", new Observation.Null());

        assertEquals("{\n"
                + "  \"name\": \"Ann\",\n"
                + "  \"paid\": true,\n"
                + "  \"URL\": \"http://a\",\n"
                + "  \"x\": 1,\n"
                + "  \"status\": \"PAID\",\n"
                + "  \"placed\": \"2024-01-02\",\n"
                + "  \"note\": null\n"
                + "}", ObservationJson.write(new Observation.Bean("a.Order", properties)));
    }

    @Test
    public void writesNumbersExactlyAndNonFiniteOnesAsText() {
        assertEquals("1.50", ObservationJson.write(literal(new BigDecimal("1.50"))));
        assertEquals("9007199254740993", ObservationJson.write(literal(9007199254740993L)));
        assertEquals("\"NaN\"", ObservationJson.write(literal(Double.NaN)));
        assertEquals("\"x\"", ObservationJson.write(literal('x')));
    }

    @Test
    public void endsAPartlyRecordedArrayWithHowManyAreLeftOut() {
        Observation numbers = new Observation.Sequence(Observation.Sequence.Kind.LIST, 7,
                Arrays.asList(literal(1), literal(2)), null);

        assertEquals("[\n  1,\n  2,\n  \"<5 more>\"\n]", ObservationJson.write(numbers));
    }

    @Test
    public void writesAMapWithLiteralKeysAsAnObjectAndAnyOtherAsEntries() {
        Observation stock = new Observation.MapOf(2, Arrays.asList(
                new Observation.Entry(literal("pen"), literal(1)), new Observation.Entry(literal("ink"), literal(2))));
        Observation byCustomer = new Observation.MapOf(1, Collections.singletonList(new Observation.Entry(
                new Observation.Bean("a.Customer", Collections.singletonMap("getName", literal("Ann"))), literal(3))));

        assertEquals("{\n  \"pen\": 1,\n  \"ink\": 2\n}", ObservationJson.write(stock));
        assertEquals("[\n  {\n    \"key\": {\n      \"name\": \"Ann\"\n    },\n    \"value\": 3\n  }\n]",
                ObservationJson.write(byCustomer));
    }

    @Test
    public void notesWhatWasNotRecorded() {
        List<Observation> unrecorded = Arrays.<Observation>asList(
                new Observation.Unrecorded(Observation.Unrecorded.Reason.CYCLE, null),
                new Observation.Unrecorded(Observation.Unrecorded.Reason.THREW, "java.lang.IllegalStateException"));

        assertEquals("[\n  \"<cycle>\",\n  \"<threw java.lang.IllegalStateException>\"\n]",
                ObservationJson.write(new Observation.Sequence(Observation.Sequence.Kind.LIST, 2, unrecorded, null)));
    }

    @Test
    public void writesAShownValueAsTheUserReadsIt() {
        String json = ObservationJson.write(Observer.forDisplay().observe(ResultTargets.loop(), Object.class));

        assertEquals("{\n  \"next\": \"<cycle>\"\n}", json);
    }

    private static Observation literal(java.io.Serializable value) {
        return new Observation.Literal(value, false);
    }
}