package ent.kz.entbackend.platform.materials;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** clamd INSTREAM protocol: NUL command, unsigned network-order chunks and zero terminator. */
public final class ClamAvScanner implements MalwareScanner {

  private final String host;
  private final int port, timeout;

  public ClamAvScanner(String host, int port, int timeout) {
    if (
      host.isBlank() ||
      port < 1 ||
      port > 65535 ||
      timeout < 100 ||
      timeout > 60000
    ) throw new IllegalArgumentException("Invalid scanner configuration");
    this.host = host;
    this.port = port;
    this.timeout = timeout;
  }

  @Override
  public Result scan(byte[] bytes, String mime) {
    try (
      Socket socket = new Socket();
      ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()
    ) {
      socket.connect(
        new InetSocketAddress(host, port),
        Math.min(timeout, 2000)
      );
      socket.setSoTimeout(timeout);
      Future<String> response = executor.submit(() -> {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
        for (int offset = 0; offset < bytes.length; offset += 8192) {
          int size = Math.min(8192, bytes.length - offset);
          out.writeInt(size);
          out.write(bytes, offset, size);
        }
        out.writeInt(0);
        out.flush();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        InputStream in = socket.getInputStream();
        int next;
        while ((next = in.read()) != -1 && next != 0 && next != '\n') {
          if (result.size() >= 4096) throw new IOException(
            "Scanner response too long"
          );
          result.write(next);
        }
        return result.toString(StandardCharsets.UTF_8).trim();
      });
      String result;
      try {
        result = response.get(timeout, TimeUnit.MILLISECONDS);
      } catch (Exception e) {
        socket.close();
        response.cancel(true);
        return new Result(Status.SCAN_FAILED, "clamav", "SCANNER_UNAVAILABLE");
      }
      if (result.equals("stream: OK")) return new Result(
        Status.CLEAN,
        "clamav",
        "CLEAN"
      );
      if (
        result.startsWith("stream: ") && result.endsWith(" FOUND")
      ) return new Result(Status.INFECTED, "clamav", "MALWARE_DETECTED");
      return new Result(Status.SCAN_FAILED, "clamav", "SCANNER_ERROR");
    } catch (Exception e) {
      return new Result(Status.SCAN_FAILED, "clamav", "SCANNER_UNAVAILABLE");
    }
  }
}
