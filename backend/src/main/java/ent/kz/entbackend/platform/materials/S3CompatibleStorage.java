package ent.kz.entbackend.platform.materials;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Minimal path-style S3 transport using AWS Signature V4; credentials stay server-side. */
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class S3CompatibleStorage implements StorageService {

  private final URI endpoint;
  private final String bucket, region, access, secret;
  private final HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build();

  public S3CompatibleStorage(
    @Value("${app.storage.s3.endpoint}") String endpoint,
    @Value("${app.storage.s3.bucket}") String bucket,
    @Value("${app.storage.s3.region:us-east-1}") String region,
    @Value("${app.storage.s3.access-key}") String access,
    @Value("${app.storage.s3.secret-key}") String secret
  ) {
    this.endpoint = URI.create(endpoint.replaceAll("/+$", ""));
    this.bucket = bucket;
    this.region = region;
    this.access = access;
    this.secret = secret;
    if (
      !bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") ||
      this.endpoint.getHost() == null ||
      this.endpoint.getUserInfo() != null ||
      !this.endpoint.getPath().isEmpty()
    ) throw new IllegalArgumentException("Invalid S3 endpoint/bucket");
  }

  public void put(String key, byte[] bytes, String mime) {
    send("PUT", key, bytes, mime);
  }

  public byte[] get(String key) {
    return send("GET", key, new byte[0], null);
  }

  public void delete(String key) {
    send("DELETE", key, new byte[0], null);
  }

  private byte[] send(String method, String key, byte[] body, String mime) {
    if (!key.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException(
      "Invalid storage key"
    );
    try {
      URI uri = URI.create(endpoint + "/" + bucket + "/" + key);
      String host = uri.getRawAuthority();
      String date = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
          .withZone(ZoneOffset.UTC)
          .format(Instant.now()),
        day = date.substring(0, 8),
        hash = sha(body),
        headers =
          "host:" +
          host +
          "\nx-amz-content-sha256:" +
          hash +
          "\nx-amz-date:" +
          date +
          "\n",
        signed = "host;x-amz-content-sha256;x-amz-date";
      String canonical =
          method +
          "\n" +
          uri.getRawPath() +
          "\n\n" +
          headers +
          "\n" +
          signed +
          "\n" +
          hash,
        scope = day + "/" + region + "/s3/aws4_request";
      byte[] signing = hmac(
        hmac(
          hmac(
            hmac(("AWS4" + secret).getBytes(StandardCharsets.UTF_8), day),
            region
          ),
          "s3"
        ),
        "aws4_request"
      );
      String signature = HexFormat.of().formatHex(
        hmac(
          signing,
          "AWS4-HMAC-SHA256\n" +
            date +
            "\n" +
            scope +
            "\n" +
            sha(canonical.getBytes(StandardCharsets.UTF_8))
        )
      );
      var request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(30))
        .header("x-amz-date", date)
        .header("x-amz-content-sha256", hash)
        .header(
          "Authorization",
          "AWS4-HMAC-SHA256 Credential=" +
            access +
            "/" +
            scope +
            ", SignedHeaders=" +
            signed +
            ", Signature=" +
            signature
        )
        .method(
          method,
          body.length == 0
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofByteArray(body)
        );
      if (mime != null) request.header("Content-Type", mime);
      var response = client.send(
        request.build(),
        HttpResponse.BodyHandlers.ofByteArray()
      );
      if (
        response.statusCode() < 200 || response.statusCode() >= 300
      ) throw new IllegalStateException(
        "S3 operation failed: " + response.statusCode()
      );
      return response.body();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Storage interrupted", e);
    } catch (Exception e) {
      throw new IllegalStateException("Storage operation failed", e);
    }
  }

  private static String sha(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(
      MessageDigest.getInstance("SHA-256").digest(bytes)
    );
  }

  private static byte[] hmac(byte[] key, String value) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
  }
}
