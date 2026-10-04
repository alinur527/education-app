package ent.kz.entbackend.platform.materials;

import static ent.kz.entbackend.platform.PlatformException.require;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import org.springframework.stereotype.Component;

@Component
public class FileValidation {

  private static final Map<String, String> TYPES = Map.of(
    "pdf",
    "application/pdf",
    "docx",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "pptx",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "png",
    "image/png",
    "jpg",
    "image/jpeg",
    "jpeg",
    "image/jpeg",
    "webp",
    "image/webp",
    "txt",
    "text/plain",
    "md",
    "text/markdown"
  );

  public String validate(String name, String supplied, byte[] bytes) {
    require(
      name != null &&
        !name.isBlank() &&
        name.length() <= 255 &&
        !name.contains("..") &&
        !name.matches(".*[\\\\/:\\p{Cntrl}].*"),
      "INVALID_FILE_NAME"
    );
    require(
      bytes.length > 0 && bytes.length <= 20 * 1024 * 1024,
      "FILE_TOO_LARGE"
    );
    String ext = name
        .substring(name.lastIndexOf('.') + 1)
        .toLowerCase(Locale.ROOT),
      mime = TYPES.get(ext);
    require(mime != null, "FILE_TYPE_FORBIDDEN");
    require(
      supplied != null &&
        (supplied.equals(mime) ||
          (ext.equals("md") && supplied.equals("text/plain"))),
      "MIME_MISMATCH"
    );
    String head = new String(
      bytes,
      0,
      Math.min(bytes.length, 12),
      StandardCharsets.ISO_8859_1
    );
    switch (ext) {
      case "pdf" -> require(head.startsWith("%PDF-"), "INVALID_FILE_CONTENT");
      case "png" -> require(
        bytes.length > 8 &&
          (bytes[0] & 255) == 137 &&
          head.substring(1).startsWith("PNG\r\n\u001a\n"),
        "INVALID_FILE_CONTENT"
      );
      case "jpg", "jpeg" -> require(
        bytes.length > 3 &&
          (bytes[0] & 255) == 255 &&
          (bytes[1] & 255) == 216 &&
          (bytes[2] & 255) == 255,
        "INVALID_FILE_CONTENT"
      );
      case "webp" -> require(
        head.startsWith("RIFF") && head.endsWith("WEBP"),
        "INVALID_FILE_CONTENT"
      );
      case "docx", "pptx" -> office(bytes, ext);
      default -> {
        try {
          StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes));
          require(
            !head.startsWith("MZ") &&
              !new String(bytes, StandardCharsets.UTF_8).contains("\0"),
            "INVALID_FILE_CONTENT"
          );
        } catch (CharacterCodingException e) {
          require(false, "INVALID_UTF8");
        }
      }
    }
    return mime;
  }

  private void office(byte[] bytes, String ext) {
    boolean types = false,
      document = false;
    int count = 0;
    long total = 0;
    try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry e;
      byte[] buffer = new byte[8192];
      while ((e = zip.getNextEntry()) != null) {
        require(
          ++count <= 500 &&
            !e.getName().contains("..") &&
            !e.getName().startsWith("/") &&
            !e.getName().contains("\\") &&
            !e.getName().toLowerCase(Locale.ROOT).contains("vbaproject"),
          "UNSAFE_DOCUMENT"
        );
        types |= e.getName().equals("[Content_Types].xml");
        document |= e
          .getName()
          .equals(
            ext.equals("docx") ? "word/document.xml" : "ppt/presentation.xml"
          );
        int n;
        while ((n = zip.read(buffer)) != -1) {
          total += n;
          require(total <= 50 * 1024 * 1024, "DOCUMENT_TOO_LARGE");
        }
      }
      require(types && document, "INVALID_FILE_CONTENT");
    } catch (IOException e) {
      require(false, "INVALID_FILE_CONTENT");
    }
  }
}
