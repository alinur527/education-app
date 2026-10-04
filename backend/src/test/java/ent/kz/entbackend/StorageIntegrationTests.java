package ent.kz.entbackend;

import static org.junit.jupiter.api.Assertions.*;

import ent.kz.entbackend.platform.materials.LocalFileStorage;
import ent.kz.entbackend.platform.materials.S3CompatibleStorage;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class StorageIntegrationTests {

  // Disposable interoperability fixture, never deployed with the application.
  @Container
  static GenericContainer<?> s3 = new GenericContainer<>(
    "rustfs/rustfs@sha256:1803faef57627e2d9c2e7d89d655d712ddded5389040054987163043fecb6a3c"
  )
    .withEnv("RUSTFS_ACCESS_KEY", "test-access-key")
    .withEnv("RUSTFS_SECRET_KEY", "isolated-test-secret-only")
    .withExposedPorts(9000)
    .withCommand("/data")
    .waitingFor(Wait.forHttp("/health").forPort(9000));

  @Test
  void signedS3RoundtripRejectsWrongCredentials() throws Exception {
    String endpoint = "http://" + s3.getHost() + ":" + s3.getMappedPort(9000);
    var setup = s3.execInContainer("sh", "-c", "mkdir -p /data/test-materials");
    assertEquals(0, setup.getExitCode());
    var storage = new S3CompatibleStorage(
      endpoint,
      "test-materials",
      "us-east-1",
      "test-access-key",
      "isolated-test-secret-only"
    );
    String key = UUID.randomUUID().toString();
    byte[] bytes = "%PDF-1.4\nS3 interoperability fixture".getBytes(
      java.nio.charset.StandardCharsets.UTF_8
    );
    storage.put(key, bytes, "application/pdf");
    assertArrayEquals(bytes, storage.get(key));
    var invalid = new S3CompatibleStorage(
      endpoint,
      "test-materials",
      "us-east-1",
      "test-access-key",
      "wrong-secret"
    );
    assertThrows(IllegalStateException.class, () -> invalid.get(key));
    assertThrows(IllegalArgumentException.class, () -> storage.get("../test"));
    storage.delete(key);
    assertThrows(IllegalStateException.class, () -> storage.get(key));
  }

  @Test
  void localStorageRejectsOverwriteAndTraversal(@TempDir Path root) {
    var storage = new LocalFileStorage(root.toString());
    String key = UUID.randomUUID().toString();
    storage.put(key, new byte[] { 1, 2, 3 }, "application/pdf");
    assertThrows(IllegalStateException.class, () ->
      storage.put(key, new byte[] { 4 }, "application/pdf")
    );
    assertArrayEquals(new byte[] { 1, 2, 3 }, storage.get(key));
    assertThrows(IllegalStateException.class, () ->
      storage.get("../../outside")
    );
    storage.delete(key);
    assertFalse(java.nio.file.Files.exists(root.resolve(key)));
  }
}
