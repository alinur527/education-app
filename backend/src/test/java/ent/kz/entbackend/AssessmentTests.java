package ent.kz.entbackend;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import ent.kz.entbackend.platform.PlatformException;
import ent.kz.entbackend.platform.assessment.Assessment;
import org.junit.jupiter.api.Test;

class AssessmentTests {

  private final ObjectMapper json = new ObjectMapper();

  private JsonNode node(String value) throws Exception {
    return json.readTree(value);
  }

  private JsonNode multiple(String keys) throws Exception {
    return node(
      "{\"questionType\":\"MULTIPLE_SELECT\",\"correctOptionIds\":" +
        keys +
        ",\"options\":[{\"id\":\"A\",\"textRu\":\"a\",\"textKz\":\"a\"},{\"id\":\"B\",\"textRu\":\"b\",\"textKz\":\"b\"},{\"id\":\"C\",\"textRu\":\"c\",\"textKz\":\"c\"},{\"id\":\"D\",\"textRu\":\"d\",\"textKz\":\"d\"}]}"
    );
  }

  @Test
  void officialMultiplePartialCreditAndCanonicalRetries() throws Exception {
    var two = multiple("[\"A\",\"B\"]");
    assertEquals(
      2,
      Assessment.grade(
        two,
        node("{\"selectedOptionIds\":[\"B\",\"A\"]}")
      ).earned()
    );
    assertEquals(
      1,
      Assessment.grade(
        two,
        node("{\"selectedOptionIds\":[\"A\",\"D\"]}")
      ).earned()
    );
    assertEquals(
      0,
      Assessment.grade(
        two,
        node("{\"selectedOptionIds\":[\"A\",\"C\",\"D\"]}")
      ).earned()
    );
    assertEquals(
      1,
      Assessment.grade(
        multiple("[\"A\"]"),
        node("{\"selectedOptionIds\":[\"A\",\"B\"]}")
      ).earned()
    );
    assertEquals(
      1,
      Assessment.grade(
        multiple("[\"A\",\"B\",\"C\"]"),
        node("{\"selectedOptionIds\":[\"A\",\"C\",\"D\"]}")
      ).earned()
    );
    assertEquals(
      0,
      Assessment.grade(
        multiple("[\"A\",\"B\",\"C\"]"),
        node("{\"selectedOptionIds\":[\"A\"]}")
      ).earned()
    );
    assertEquals(
      Assessment.grade(
        two,
        node("{\"selectedOptionIds\":[\"A\",\"B\"]}")
      ).answer(),
      Assessment.grade(
        two,
        node("{\"selectedOptionIds\":[\"B\",\"A\"]}")
      ).answer()
    );
    assertThrows(PlatformException.class, () ->
      Assessment.grade(two, node("{\"selectedOptionIds\":[\"A\",\"A\"]}"))
    );
    assertThrows(PlatformException.class, () ->
      Assessment.grade(two, node("{\"selectedOptionIds\":[\"X\"]}"))
    );
  }

  @Test
  void matchingChecksPairsAndNeverLeaksKeys() throws Exception {
    var q = (com.fasterxml.jackson.databind.node.ObjectNode) multiple(
      "[\"A\"]"
    );
    q.remove("correctOptionIds");
    q.put("questionType", "MATCHING");
    q.set(
      "leftOptions",
      node(
        "[{\"id\":\"L1\",\"textRu\":\"1\",\"textKz\":\"1\"},{\"id\":\"L2\",\"textRu\":\"2\",\"textKz\":\"2\"}]"
      )
    );
    q.set(
      "correctPairs",
      node(
        "[{\"leftId\":\"L1\",\"rightId\":\"A\"},{\"leftId\":\"L2\",\"rightId\":\"B\"}]"
      )
    );
    Assessment.validate(q, true);
    assertEquals(
      1,
      Assessment.grade(
        q,
        node(
          "{\"pairs\":[{\"leftId\":\"L1\",\"rightId\":\"A\"},{\"leftId\":\"L2\",\"rightId\":\"D\"}]}"
        )
      ).earned()
    );
    assertThrows(PlatformException.class, () ->
      Assessment.grade(
        q,
        node(
          "{\"pairs\":[{\"leftId\":\"L1\",\"rightId\":\"A\"},{\"leftId\":\"L1\",\"rightId\":\"B\"}]}"
        )
      )
    );
    q.put("secretFutureKey", "must never be public");
    var safe = Assessment.publicView(q);
    assertFalse(safe.has("correctPairs"));
    assertFalse(safe.has("secretFutureKey"));
    assertTrue(safe.has("leftOptions"));
  }
}
