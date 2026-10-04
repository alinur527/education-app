package ent.kz.entbackend;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import ent.kz.entbackend.platform.PlatformException;
import ent.kz.entbackend.platform.assessment.Assessment;
import ent.kz.entbackend.platform.content.*;
import ent.kz.entbackend.platform.importing.ImportParser;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ImportParserAssessmentTests {

  private final ObjectMapper json = new ObjectMapper();
  private final ImportParser parser = new ImportParser(json);
  private final ContentValidation validation = new ContentValidation();

  private Map<String, String> fields(String type) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("key", "typed-question");
    fields.put("kind", "QUESTION");
    fields.put("parentId", "9d33e0f7-18cf-4fe1-93a4-ed351b6e0f84");
    fields.put("titleRu", "Выберите ответ, проверьте условия");
    fields.put("titleKz", "Жауапты таңдаңыз");
    fields.put("questionType", type);
    fields.put(
      "options",
      """
      [{"id":"A","textRu":"Один","textKz":"Бір"},
       {"id":"B","textRu":"Два","textKz":"Екі"},
       {"id":"C","textRu":"Три","textKz":"Үш"},
       {"id":"D","textRu":"Четыре","textKz":"Төрт"}]
      """
    );
    if (type.equals("MULTIPLE_SELECT")) {
      fields.put("correctOptionIds", "[\"A\",\"B\"]");
    } else {
      fields.put(
        "leftOptions",
        """
        [{"id":"L1","textRu":"Первый","textKz":"Бірінші"},
         {"id":"L2","textRu":"Второй","textKz":"Екінші"}]
        """
      );
      fields.put(
        "correctPairs",
        """
        [{"leftId":"L1","rightId":"A"},{"leftId":"L2","rightId":"B"}]
        """
      );
    }
    return fields;
  }

  private String cell(String value) {
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  private JsonNode parse(Map<String, String> fields) {
    String csv =
      String.join(",", fields.keySet()) +
      "\r\n" +
      fields
        .values()
        .stream()
        .map(this::cell)
        .collect(Collectors.joining(",")) +
      "\r\n";
    JsonNode rows = parser.parse(
      "typed.csv",
      csv.getBytes(StandardCharsets.UTF_8)
    );
    assertEquals(1, rows.size());
    assertEquals("QUESTION", rows.get(0).path("kind").asText());
    assertEquals(fields.get("parentId"), rows.get(0).path("parentId").asText());
    return rows.get(0).path("payload");
  }

  @Test
  void multipleSelectCsvValidatesAndKeepsContextRevisionAndScoring()
    throws Exception {
    var fields = fields("MULTIPLE_SELECT");
    fields.put("contextId", "3e217464-d27f-4139-86a6-078b54e5f55b");
    // Content revisions are longs; the CSV adapter must not narrow them to int.
    fields.put("contextVersion", "2147483648");
    JsonNode payload = parse(fields);
    validation.validate(ContentKind.QUESTION, payload, true);
    assertTrue(payload.path("correctOptionIds").isArray());
    assertTrue(payload.path("contextVersion").isIntegralNumber());
    assertEquals(2147483648L, payload.path("contextVersion").asLong());
    var frozen = Assessment.freeze(payload);
    assertEquals("ENT_MULTIPLE_2026_V1", frozen.path("scoringPolicy").asText());
    assertEquals(
      2,
      Assessment.grade(
        frozen,
        json.readTree("{\"selectedOptionIds\":[\"B\",\"A\"]}")
      ).earned()
    );
    assertEquals(
      1,
      Assessment.grade(
        frozen,
        json.readTree("{\"selectedOptionIds\":[\"A\",\"D\"]}")
      ).earned()
    );
  }

  @Test
  void matchingCsvValidatesBothArraysAndGradesPairs() throws Exception {
    JsonNode payload = parse(fields("MATCHING"));
    validation.validate(ContentKind.QUESTION, payload, true);
    assertTrue(payload.path("leftOptions").isArray());
    assertTrue(payload.path("correctPairs").isArray());
    var frozen = Assessment.freeze(payload);
    assertEquals("ENT_MATCHING_2026_V1", frozen.path("scoringPolicy").asText());
    var grade = Assessment.grade(
      frozen,
      json.readTree(
        """
        {"pairs":[{"leftId":"L1","rightId":"A"},{"leftId":"L2","rightId":"D"}]}
        """
      )
    );
    assertEquals(1, grade.earned());
    assertEquals(2, grade.maximum());
  }

  @Test
  void typedCsvStillRejectsWrongShapesAndUnknownAnswerKeys() {
    record Invalid(String type, String field, String value, String code) {}
    for (var invalid : List.of(
      new Invalid(
        "MULTIPLE_SELECT",
        "correctOptionIds",
        "{\"A\":true}",
        "INVALID_SELECTION"
      ),
      new Invalid(
        "MULTIPLE_SELECT",
        "correctOptionIds",
        "[\"X\"]",
        "INVALID_MULTIPLE_KEY"
      ),
      new Invalid(
        "MATCHING",
        "leftOptions",
        "{\"L1\":\"First\",\"L2\":\"Second\"}",
        "OPTIONS_COUNT"
      ),
      new Invalid(
        "MATCHING",
        "correctPairs",
        "{\"L1\":\"A\",\"L2\":\"B\"}",
        "PAIR_COUNT"
      ),
      new Invalid(
        "MATCHING",
        "correctPairs",
        "[{\"leftId\":\"L1\",\"rightId\":\"A\"},{\"leftId\":\"L2\",\"rightId\":\"X\"}]",
        "UNKNOWN_OR_DUPLICATE_PAIR"
      )
    )) {
      var fields = fields(invalid.type());
      fields.put(invalid.field(), invalid.value());
      JsonNode payload = parse(fields);
      var error = assertThrows(PlatformException.class, () ->
        validation.validate(ContentKind.QUESTION, payload, true)
      );
      assertEquals(
        invalid.code(),
        error.code(),
        invalid.type() + ": " + invalid.field()
      );
    }
  }

  @Test
  void contextRevisionCsvRejectsNonnumericAndNonpositiveValues() {
    for (String value : List.of("one", "1.5", "9223372036854775808")) {
      var fields = fields("MULTIPLE_SELECT");
      fields.put("contextVersion", value);
      assertEquals(
        "IMPORT_PARSE_ERROR",
        assertThrows(PlatformException.class, () -> parse(fields)).code()
      );
    }
    for (String value : List.of("0", "-1")) {
      var fields = fields("MULTIPLE_SELECT");
      fields.put("contextVersion", value);
      JsonNode payload = parse(fields);
      assertEquals(
        "INVALID_CONTEXT_VERSION",
        assertThrows(PlatformException.class, () ->
          validation.validate(ContentKind.QUESTION, payload, true)
        ).code()
      );
    }
  }
}
