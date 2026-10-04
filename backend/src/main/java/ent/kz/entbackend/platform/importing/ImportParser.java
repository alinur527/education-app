package ent.kz.entbackend.platform.importing;

import static ent.kz.entbackend.platform.PlatformException.require;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import ent.kz.entbackend.platform.PlatformException;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ImportParser {

  private final ObjectMapper json;

  public ImportParser(ObjectMapper json) {
    this.json = json;
  }

  public ArrayNode parse(String name, byte[] bytes) {
    require(bytes.length > 0 && bytes.length <= 2 * 1024 * 1024, "IMPORT_SIZE");
    String text;
    try {
      text = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
        .replaceFirst("^\\uFEFF", "");
    } catch (Exception e) {
      throw new PlatformException(400, "INVALID_UTF8");
    }
    try {
      if (name.toLowerCase(Locale.ROOT).endsWith(".json")) {
        JsonNode root = json.readTree(text);
        require(
          root.isArray() && root.size() > 0 && root.size() <= 500,
          "IMPORT_ROWS"
        );
        return (ArrayNode) root;
      }
      require(name.toLowerCase(Locale.ROOT).endsWith(".csv"), "IMPORT_FORMAT");
      List<List<String>> rows = csv(text);
      require(rows.size() > 1 && rows.size() <= 501, "IMPORT_ROWS");
      List<String> header = rows.getFirst().stream().map(String::trim).toList();
      require(
        new HashSet<>(header).size() == header.size(),
        "DUPLICATE_HEADER"
      );
      ArrayNode result = json.createArrayNode();
      for (int i = 1; i < rows.size(); i++) {
        List<String> cells = rows.get(i);
        require(cells.size() == header.size(), "CSV_COLUMN_COUNT");
        ObjectNode row = json.createObjectNode(),
          payload = json.createObjectNode();
        for (int c = 0; c < header.size(); c++) {
          String k = header.get(c).trim(),
            v = cells.get(c);
          if (v.isEmpty()) continue;
          switch (k) {
            case "key", "kind", "parentKey", "parentId" -> row.put(k, v);
            case "options", "blocks", "questions" -> payload.set(
              k,
              json.readTree(v)
            );
            case
              "year",
              "sortOrder",
              "maxScore",
              "durationMinutes" -> payload.put(k, Integer.parseInt(v));
            case "verified", "selfEnroll" -> {
              require(v.equals("true") || v.equals("false"), "INVALID_BOOLEAN");
              payload.put(k, Boolean.parseBoolean(v));
            }
            default -> payload.put(k, v);
          }
        }
        row.set("payload", payload);
        result.add(row);
      }
      return result;
    } catch (PlatformException e) {
      throw e;
    } catch (Exception e) {
      throw new PlatformException(400, "IMPORT_PARSE_ERROR");
    }
  }

  // RFC 4180 quoting, escaped quotes and multiline cells; bounds limit parser work.
  private List<List<String>> csv(String s) {
    var result = new ArrayList<List<String>>();
    var row = new ArrayList<String>();
    var cell = new StringBuilder();
    boolean quoted = false,
      closed = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
            cell.append('"');
            i++;
          } else {
            quoted = false;
            closed = true;
          }
        } else cell.append(c);
      } else if (c == '"') {
        require(cell.isEmpty() && !closed, "CSV_QUOTE");
        quoted = true;
      } else if (c == ',' || c == '\n' || c == '\r') {
        row.add(cell.toString());
        cell.setLength(0);
        closed = false;
        require(row.size() <= 64, "CSV_COLUMNS");
        if (c != ',') {
          if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
          if (row.size() > 1 || !row.getFirst().isEmpty()) result.add(row);
          row = new ArrayList<>();
          require(result.size() <= 501, "IMPORT_ROWS");
        }
      } else {
        require(!closed, "CSV_QUOTE");
        cell.append(c);
      }
      require(cell.length() <= 200000, "IMPORT_CELL_SIZE");
    }
    require(!quoted, "CSV_QUOTE");
    if (!cell.isEmpty() || !row.isEmpty()) {
      row.add(cell.toString());
      result.add(row);
    }
    return result;
  }
}
