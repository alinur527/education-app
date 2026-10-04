package ent.kz.entbackend.platform.materials;

import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
  name = "app.storage.provider",
  havingValue = "local",
  matchIfMissing = true
)
public class LocalFileStorage implements StorageService {

  private final Path root;

  public LocalFileStorage(
    @Value("${app.storage.local-root:./data/materials}") String root
  ) {
    this.root = Path.of(root).toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.root);
    } catch (Exception e) {
      throw new IllegalStateException("Storage unavailable", e);
    }
  }

  private Path path(String key) {
    if (!key.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException(
      "Invalid storage key"
    );
    Path p = root.resolve(key).normalize();
    if (
      !p.startsWith(root) || Files.isSymbolicLink(p)
    ) throw new IllegalArgumentException("Unsafe path");
    return p;
  }

  public void put(String key, byte[] bytes, String mime) {
    Path temporary = null;
    try {
      Path target = path(key);
      temporary = Files.createTempFile(root, "upload-", ".tmp");
      Files.write(temporary, bytes);
      Files.move(temporary, target);
    } catch (Exception e) {
      throw new IllegalStateException("Storage write failed", e);
    } finally {
      if (temporary != null) try {
        Files.deleteIfExists(temporary);
      } catch (java.io.IOException ignored) {
        /* Orphan cleanup can remove temporary uploads. */
      }
    }
  }

  public byte[] get(String key) {
    try {
      return Files.readAllBytes(path(key));
    } catch (Exception e) {
      throw new IllegalStateException("Storage read failed", e);
    }
  }

  public void delete(String key) {
    try {
      Files.deleteIfExists(path(key));
    } catch (Exception e) {
      throw new IllegalStateException("Storage delete failed", e);
    }
  }
}
