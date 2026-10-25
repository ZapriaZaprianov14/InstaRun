package com.zapryan.json;

import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonBooleanLiteral;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonNullLiteral;
import com.intellij.json.psi.JsonNumberLiteral;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses JSON text with IntelliJ's JSON PSI into a {@link JsonNode}, keeping each value's offset so problems can be
 * pointed at. Numbers keep their source text. Must run inside a read action.
 */
public final class JsonPsiReader {

    private JsonPsiReader() {
    }

    public static JsonNode read(Project project, String text) throws JsonBindingException {
        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText("instarun.json", JsonLanguage.INSTANCE, text);
        PsiErrorElement error = PsiTreeUtil.findChildOfType(file, PsiErrorElement.class);
        if (error != null) {
            throw new JsonBindingException(JsonProblem.SYNTAX_ERROR, "", error.getTextRange().getStartOffset(),
                    error.getErrorDescription());
        }
        List<JsonValue> values = ((JsonFile) file).getAllTopLevelValues();
        if (values.isEmpty()) {
            throw new JsonBindingException(JsonProblem.EMPTY, "", 0);
        }
        if (values.size() > 1) {
            throw new JsonBindingException(JsonProblem.MULTIPLE_VALUES, "",
                    values.get(1).getTextRange().getStartOffset());
        }
        return convert(values.get(0));
    }

    private static JsonNode convert(JsonValue value) throws JsonBindingException {
        int offset = value.getTextRange().getStartOffset();
        if (value instanceof JsonObject) {
            List<JsonNode.Member> members = new ArrayList<>();
            for (JsonProperty property : ((JsonObject) value).getPropertyList()) {
                JsonValue propertyValue = property.getValue();
                if (propertyValue == null) {
                    throw new JsonBindingException(JsonProblem.NOT_A_VALUE, "", property.getTextRange().getEndOffset(),
                            property.getText());
                }
                members.add(new JsonNode.Member(property.getName(), convert(propertyValue),
                        property.getTextRange().getStartOffset()));
            }
            return new JsonNode.ObjectNode(members, offset);
        }
        if (value instanceof JsonArray) {
            List<JsonNode> elements = new ArrayList<>();
            for (JsonValue element : ((JsonArray) value).getValueList()) {
                elements.add(convert(element));
            }
            return new JsonNode.ArrayNode(elements, offset);
        }
        if (value instanceof JsonStringLiteral) {
            return new JsonNode.StringNode(((JsonStringLiteral) value).getValue(), offset);
        }
        if (value instanceof JsonNumberLiteral) {
            return new JsonNode.NumberNode(value.getText(), offset);
        }
        if (value instanceof JsonBooleanLiteral) {
            return new JsonNode.BooleanNode(((JsonBooleanLiteral) value).getValue(), offset);
        }
        if (value instanceof JsonNullLiteral) {
            return new JsonNode.NullNode(offset);
        }
        // The JSON parser is lenient and reads an unquoted word as a reference; JSON itself has no such value.
        throw new JsonBindingException(JsonProblem.NOT_A_VALUE, "", offset, value.getText());
    }
}