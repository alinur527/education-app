package ent.kz.entbackend;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import ent.kz.entbackend.platform.PlatformException;
import ent.kz.entbackend.platform.content.*;
import ent.kz.entbackend.platform.importing.ImportParser;
import ent.kz.entbackend.platform.materials.FileValidation;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.junit.jupiter.api.Test;

class PlatformValidationTests {

  private final ObjectMapper json = new ObjectMapper();
  private final ContentValidation validation = new ContentValidation();

  @Test
  void rejectsNumericOverflowAndWrongNestedTypes() throws Exception {
    for (String extra : new String[] {
      "\"sortOrder\":18446744073709551616",
      "\"options\":42",
      "\"blocks\":[{\"type\":\"TEXT\",\"url\":123}]",
    }) {
      var payload = json.readTree("{\"titleRu\":\"Draft\"," + extra + "}");
      assertThrows(PlatformException.class, () ->
        validation.validate(ContentKind.SUBJECT, payload, false)
      );
    }
    var quiz = json.readTree(
      "{\"titleRu\":\"Quiz\",\"questions\":[{\"titleRu\":\"Q\",\"correctOptionId\":1,\"options\":[{\"id\":\"1\",\"textRu\":\"One\"},{\"id\":\"2\",\"textRu\":\"Two\"}]}]}"
    );
    assertThrows(PlatformException.class, () ->
      validation.validate(ContentKind.QUIZ, quiz, false)
    );
  }

  @Test
  void duplicateTrimmedCsvHeadersCannotOverwriteContent() {
    var parser = new ImportParser(json);
    var failure = assertThrows(PlatformException.class, () ->
      parser.parse(
        "input.csv",
        "key,kind,titleRu, titleRu\ns,SUBJECT,First,Second\n".getBytes(
          StandardCharsets.UTF_8
        )
      )
    );
    assertEquals("DUPLICATE_HEADER", failure.code());
  }

  @Test
  void acceptsOfficeDocumentsAndRejectsUnsafeArchives() throws Exception {
    var files = new FileValidation();
    String docx =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      pptx =
        "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    assertEquals(
      docx,
      files.validate("lesson.docx", docx, office("word/document.xml"))
    );
    assertEquals(
      pptx,
      files.validate("slides.pptx", pptx, office("ppt/presentation.xml"))
    );
    assertThrows(PlatformException.class, () ->
      files.validate(
        "lesson.docx",
        docx,
        office("word/document.xml", "word/vbaProject.bin")
      )
    );
    assertThrows(PlatformException.class, () ->
      files.validate(
        "lesson.docx",
        docx,
        office("word/document.xml", "../outside")
      )
    );
    assertThrows(PlatformException.class, () ->
      files.validate("lesson.exe", "application/pdf", "%PDF-1.4".getBytes())
    );
    assertThrows(PlatformException.class, () ->
      files.validate("lesson.pdf", "text/plain", "%PDF-1.4".getBytes())
    );
    assertThrows(PlatformException.class, () ->
      files.validate(
        "lesson.pdf",
        "application/pdf",
        new byte[20 * 1024 * 1024 + 1]
      )
    );
  }

  private byte[] office(String... entries) throws Exception {
    var output = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(output)) {
      zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
      zip.write("<Types/>".getBytes());
      zip.closeEntry();
      for (String entry : entries) {
        zip.putNextEntry(new ZipEntry(entry));
        zip.write("<Document/>".getBytes());
        zip.closeEntry();
      }
    }
    return output.toByteArray();
  }
}
