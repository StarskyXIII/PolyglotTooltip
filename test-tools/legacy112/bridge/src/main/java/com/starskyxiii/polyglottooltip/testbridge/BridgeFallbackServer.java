package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The bridge's WebSocket endpoint on blocking java.net sockets, used when Netty cannot open an NIO selector on
 * the machine (Windows refuses the selector's loopback connection while plain sockets still work). One thread
 * per connection; unfragmented text frames, as the runner's client sends them.
 */
final class BridgeFallbackServer {
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_FRAME = 1048576;
    private final ServerSocket server;
    private final ScheduledExecutorService timeouts = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "polyglot-bridge-timeouts"); thread.setDaemon(true); return thread;
    });
    private volatile boolean closed;

    BridgeFallbackServer(int port) throws IOException {
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 8);
        Thread acceptor = new Thread(this::accept, "polyglot-bridge-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    void close() {
        closed = true;
        try { server.close(); } catch (IOException ignored) { }
        timeouts.shutdownNow();
    }

    private void accept() {
        while (!closed) {
            Socket socket;
            try { socket = server.accept(); }
            catch (IOException error) {
                if (closed) return;
                try { Thread.sleep(200); } catch (InterruptedException interrupted) { return; }
                continue;
            }
            Thread worker = new Thread(() -> serve(socket), "polyglot-bridge-client");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private void serve(Socket socket) {
        Connection connection = new Connection(socket);
        try { connection.run(); }
        catch (IOException | RuntimeException ignored) { }
        finally { connection.close(); }
    }

    private final class Connection {
        private final Socket socket;
        private final AtomicInteger pending = new AtomicInteger();
        private final Set<CompletableFuture<JsonElement>> requests =
                Collections.newSetFromMap(new IdentityHashMap<CompletableFuture<JsonElement>, Boolean>());
        private InputStream in;
        private OutputStream out;

        Connection(Socket socket) { this.socket = socket; }

        void run() throws IOException {
            socket.setTcpNoDelay(true);
            in = new BufferedInputStream(socket.getInputStream());
            out = new BufferedOutputStream(socket.getOutputStream());
            if (!handshake()) return;
            sendText(BridgeServer.hello().toString());
            while (!closed) {
                Frame frame = readFrame();
                if (frame == null) return;
                if (frame.opcode == 8) { sendFrame(8, frame.payload); return; }
                if (frame.opcode == 9) { sendFrame(10, frame.payload); continue; }
                if (frame.opcode != 1) continue;
                if (!handle(new String(frame.payload, StandardCharsets.UTF_8))) return;
            }
        }

        private boolean handshake() throws IOException {
            String line, key = null;
            boolean first = true;
            while ((line = readLine()) != null && !line.isEmpty()) {
                if (first) { first = false; if (!line.startsWith("GET ")) return false; }
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Sec-WebSocket-Key")) key = line.substring(colon + 1).trim();
            }
            if (key == null) return false;
            String accept;
            try {
                accept = Base64.getEncoder().encodeToString(
                        MessageDigest.getInstance("SHA-1").digest((key + GUID).getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException error) { throw new IOException(error); }
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "
                    + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return true;
        }

        private String readLine() throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int value;
            while ((value = in.read()) != -1) {
                if (value == '\n') break;
                if (value != '\r') buffer.write(value);
                if (buffer.size() > 8192) throw new IOException("Header line too long");
            }
            return value == -1 && buffer.size() == 0 ? null : new String(buffer.toByteArray(), StandardCharsets.ISO_8859_1);
        }

        private Frame readFrame() throws IOException {
            int first = in.read();
            if (first < 0) return null;
            int second = in.read();
            if (second < 0) return null;
            int opcode = first & 0x0F;
            boolean masked = (second & 0x80) != 0;
            long length = second & 0x7F;
            if (length == 126) length = ((long) readByte() << 8) | readByte();
            else if (length == 127) { length = 0; for (int index = 0; index < 8; index++) length = (length << 8) | readByte(); }
            if (length > MAX_FRAME) throw new IOException("Frame too large");
            byte[] mask = new byte[4];
            if (masked) readFully(mask);
            byte[] payload = new byte[(int) length];
            readFully(payload);
            if (masked) for (int index = 0; index < payload.length; index++) payload[index] ^= mask[index & 3];
            return new Frame(opcode, payload);
        }

        private int readByte() throws IOException {
            int value = in.read();
            if (value < 0) throw new EOFException();
            return value;
        }

        private void readFully(byte[] buffer) throws IOException {
            int offset = 0;
            while (offset < buffer.length) {
                int count = in.read(buffer, offset, buffer.length - offset);
                if (count < 0) throw new EOFException();
                offset += count;
            }
        }

        private synchronized void sendText(String text) throws IOException { sendFrame(1, text.getBytes(StandardCharsets.UTF_8)); }

        private synchronized void sendFrame(int opcode, byte[] payload) throws IOException {
            out.write(0x80 | opcode);
            if (payload.length < 126) out.write(payload.length);
            else if (payload.length < 65536) { out.write(126); out.write(payload.length >> 8); out.write(payload.length & 0xFF); }
            else { out.write(127); for (int shift = 7; shift >= 0; shift--) out.write((int) (((long) payload.length >> (8 * shift)) & 0xFF)); }
            out.write(payload);
            out.flush();
        }

        /** One JSON-RPC request; false closes the connection, as the Netty handler does. */
        private boolean handle(String text) {
            if (pending.incrementAndGet() > 32) return false;
            JsonObject request;
            try {
                request = new JsonParser().parse(text).getAsJsonObject();
                if (!request.has("id") || !request.has("method")) throw new IllegalArgumentException("Invalid request");
            } catch (RuntimeException error) { pending.decrementAndGet(); return false; }
            JsonElement id = request.get("id");
            String method = request.get("method").getAsString();
            JsonObject params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();
            CompletableFuture<JsonElement> result;
            try { result = BridgeDispatch.call(method, params); }
            catch (RuntimeException error) { result = new CompletableFuture<>(); result.completeExceptionally(error); }
            synchronized (requests) { requests.add(result); }
            CompletableFuture<JsonElement> settled = result;
            ScheduledFuture<?> timeout = timeouts.schedule(
                    () -> settled.completeExceptionally(new IllegalStateException("Bridge operation timed out")),
                    BridgeServer.timeoutSeconds(method, Integer.getInteger("clientdevbridge.timeoutSeconds", 120),
                            Integer.getInteger("clientdevbridge.lifecycleTimeoutSeconds"),
                            System.getProperty("clientdevbridge.fixtureNonce", "")), TimeUnit.SECONDS);
            result.whenComplete((value, error) -> {
                synchronized (requests) { requests.remove(settled); }
                pending.decrementAndGet();
                timeout.cancel(false);
                try { sendText(BridgeServer.response(id, value, error).toString()); }
                catch (IOException failure) { close(); }
            });
            return true;
        }

        void close() {
            synchronized (requests) {
                for (CompletableFuture<JsonElement> request : requests) request.cancel(false);
                requests.clear();
            }
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private static final class Frame {
        final int opcode;
        final byte[] payload;
        Frame(int opcode, byte[] payload) { this.opcode = opcode; this.payload = payload; }
    }
}
