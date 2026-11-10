package com.apify.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A minimal HTTP/1.1 server on a local port, for tests that need a real network exchange (header
 * names, redirects, dropped connections) that {@link MockTransport} cannot show. It answers every
 * request through a handler and closes the connection after each response. It is built on {@link
 * ServerSocket} because the {@code com.sun.net.httpserver} module is not readable from the tests.
 */
final class TestHttpServer implements AutoCloseable {

  /** A request the server received. */
  record Received(
      String method, String path, String query, Map<String, String> headers, byte[] body) {

    /** The value of a header, whatever the case of its name, or null. */
    String header(String name) {
      return headers.get(name.toLowerCase(Locale.ROOT));
    }

    String bodyText() {
      return new String(body, StandardCharsets.UTF_8);
    }
  }

  /** A response to send; {@code dropConnection} closes the connection without answering. */
  record Reply(int status, Map<String, String> headers, byte[] body, boolean dropConnection) {

    static Reply of(int status, String contentType, String body) {
      return new Reply(
          status,
          Map.of("Content-Type", contentType),
          body.getBytes(StandardCharsets.UTF_8),
          false);
    }

    static Reply json(int status, String body) {
      return of(status, "application/json", body);
    }

    static Reply redirect(String location) {
      return new Reply(302, Map.of("Location", location), new byte[0], false);
    }

    static Reply drop() {
      return new Reply(0, Map.of(), new byte[0], true);
    }
  }

  private final ServerSocket socket;
  private final Function<Received, Reply> handler;
  private final List<Received> received = new CopyOnWriteArrayList<>();
  private final Thread acceptor;

  TestHttpServer(Function<Received, Reply> handler) throws IOException {
    this.socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    this.handler = handler;
    this.acceptor = new Thread(this::acceptLoop, "test-http-server");
    this.acceptor.setDaemon(true);
    this.acceptor.start();
  }

  /** The origin of the server, for example {@code http://127.0.0.1:41234}. */
  String url() {
    return "http://127.0.0.1:" + socket.getLocalPort();
  }

  /** The requests received so far, oldest first. */
  List<Received> received() {
    return received;
  }

  private void acceptLoop() {
    while (!socket.isClosed()) {
      try {
        Socket connection = socket.accept();
        Thread worker = new Thread(() -> serve(connection), "test-http-server-connection");
        worker.setDaemon(true);
        worker.start();
      } catch (IOException e) {
        return; // the socket was closed
      }
    }
  }

  private void serve(Socket connection) {
    try (connection) {
      InputStream in = connection.getInputStream();
      Received request = read(in);
      if (request == null) {
        return;
      }
      received.add(request);
      Reply reply = handler.apply(request);
      if (reply.dropConnection()) {
        return;
      }
      write(connection.getOutputStream(), reply);
    } catch (IOException e) {
      // The client went away; there is nobody to answer.
    }
  }

  private static Received read(InputStream in) throws IOException {
    String requestLine = readLine(in);
    if (requestLine == null || requestLine.isEmpty()) {
      return null;
    }
    String[] parts = requestLine.split(" ");
    Map<String, String> headers = new TreeMap<>();
    for (String line = readLine(in); line != null && !line.isEmpty(); line = readLine(in)) {
      int colon = line.indexOf(':');
      // Repeated headers are joined with a comma, so a duplicate sent by mistake stays visible.
      headers.merge(
          line.substring(0, colon).toLowerCase(Locale.ROOT),
          line.substring(colon + 1).trim(),
          (first, second) -> first + ", " + second);
    }
    int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
    byte[] body = in.readNBytes(length);
    String target = parts[1];
    int question = target.indexOf('?');
    String path = question < 0 ? target : target.substring(0, question);
    String query = question < 0 ? "" : target.substring(question + 1);
    return new Received(parts[0], path, query, headers, body);
  }

  private static String readLine(InputStream in) throws IOException {
    ByteArrayOutputStream line = new ByteArrayOutputStream();
    int b;
    while ((b = in.read()) != -1) {
      if (b == '\n') {
        break;
      }
      if (b != '\r') {
        line.write(b);
      }
    }
    return b == -1 && line.size() == 0 ? null : line.toString(StandardCharsets.UTF_8);
  }

  private static void write(OutputStream out, Reply reply) throws IOException {
    StringBuilder head = new StringBuilder("HTTP/1.1 " + reply.status() + " Reply\r\n");
    reply
        .headers()
        .forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
    head.append("Content-Length: ").append(reply.body().length).append("\r\n");
    head.append("Connection: close\r\n\r\n");
    out.write(head.toString().getBytes(StandardCharsets.UTF_8));
    out.write(reply.body());
    out.flush();
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }
}
