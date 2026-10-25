package com.zapryan.json;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Assert;

public class JsonPsiReaderTest extends BasePlatformTestCase {

    public void testKeepsNumbersAsWrittenAndWhereEachValueStarts() throws Exception {
        JsonNode.ObjectNode json = (JsonNode.ObjectNode) JsonPsiReader.read(getProject(),
                "{\"price\": 1.50, \"tags\": [\"a\\\"b\", null, true]}");

        JsonNode.NumberNode price = (JsonNode.NumberNode) json.get("price");
        assertEquals("1.50", price.getText());
        assertEquals(10, price.getOffset());
        JsonNode.ArrayNode tags = (JsonNode.ArrayNode) json.get("tags");
        assertEquals("a\"b", ((JsonNode.StringNode) tags.getElements().get(0)).getValue());
        assertTrue(tags.getElements().get(1) instanceof JsonNode.NullNode);
        assertTrue(((JsonNode.BooleanNode) tags.getElements().get(2)).getValue());
    }

    public void testReportsSyntaxErrors() {
        JsonBindingException problem = Assert.assertThrows(JsonBindingException.class,
                () -> JsonPsiReader.read(getProject(), "{\"price\": }"));

        assertEquals(JsonProblem.SYNTAX_ERROR, problem.getProblem());
    }

    public void testRejectsUnquotedWordsThatTheLenientParserAccepts() {
        JsonBindingException problem = Assert.assertThrows(JsonBindingException.class,
                () -> JsonPsiReader.read(getProject(), "{\"name\": Ann}"));

        assertEquals(JsonProblem.NOT_A_VALUE, problem.getProblem());
        assertEquals("Ann", problem.getArguments().get(0));
    }

    public void testRejectsEmptyText() {
        JsonBindingException problem = Assert.assertThrows(JsonBindingException.class,
                () -> JsonPsiReader.read(getProject(), "  "));

        assertEquals(JsonProblem.EMPTY, problem.getProblem());
    }
}