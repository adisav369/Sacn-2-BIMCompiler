package com.bim.designer.api;

import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minimal RFC 6455 WebSocket handler — handshake + text frames + broadcast.
 *
 * <p>No external dependency. Works with JDK {@code com.sun.net.httpserver}.
 * Supports text frames only (opcode 0x1), ping/pong, and close.
 *
 * // Implementing BIM_Designer_UserGuide.md §13 — Witness: W-WS-1
 */
public class WebSocketHandler {

    private static final Logger LOG = Logger.getLogger(WebSocketHandler.class.getName());
    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-5AB5DC40B64E";

    private final ClientRegistry registry = new ClientRegistry();

    /** Thread-safe set of connected WebSocket output streams for broadcast. */
    static class ClientRegistry {
        private final Set<OutputStream> clients = ConcurrentHashMap.newKeySet();

        void add(OutputStream os) { clients.add(os); }
        void remove(OutputStream os) { clients.remove(os); }

        /** Broadcast a text frame to all connected clients. */
        void broadcast(String message) {
            byte[] frame = encodeTextFrame(message);
            for (OutputStream os : clients) {
                try {
                    synchronized (os) {
                        os.write(frame);
                        os.flush();
                    }
                } catch (IOException e) {
                    clients.remove(os);
                }
            }
        }
    }

    public ClientRegistry getRegistry() {
        return registry;
    }

    /**
     * Perform the WebSocket upgrade handshake, then enter the read loop.
     * This method blocks until the client disconnects.
     *
     * @param exchange the HTTP exchange with Upgrade: websocket headers
     * @param onMessage callback for each text message received
     */
    public void upgrade(HttpExchange exchange, MessageHandler onMessage) throws IOException {
        String key = exchange.getRequestHeaders().getFirst("Sec-WebSocket-Key");
        if (key == null) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        // Compute accept hash: Base64(SHA-1(key + MAGIC))
        String accept;
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update((key + WS_MAGIC).getBytes(StandardCharsets.UTF_8));
            accept = Base64.getEncoder().encodeToString(sha1.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 not available", e);
        }

        // Send 101 Switching Protocols via raw socket (HttpExchange can't do 101)
        // We need to hijack the underlying socket from the exchange
        exchange.getResponseHeaders().set("Upgrade", "websocket");
        exchange.getResponseHeaders().set("Connection", "Upgrade");
        exchange.getResponseHeaders().set("Sec-WebSocket-Accept", accept);
        exchange.sendResponseHeaders(101, -1);

        // After 101, the exchange's streams become the raw WebSocket connection
        OutputStream rawOut = exchange.getResponseBody();
        InputStream rawIn = exchange.getRequestBody();

        registry.add(rawOut);
        try {
            readLoop(rawIn, rawOut, onMessage);
        } finally {
            registry.remove(rawOut);
        }
    }

    /**
     * Perform WebSocket upgrade on a raw socket (bypassing HttpExchange).
     * Used when HttpServer can't properly handle 101 upgrades.
     *
     * @param socket    the connected socket
     * @param existingIn the InputStream already used to read HTTP headers (must be reused
     *                   to avoid losing buffered data from a BufferedInputStream)
     * @param key       the Sec-WebSocket-Key from the client
     * @param onMessage callback for each text message received
     */
    public void upgradeRawSocket(Socket socket, InputStream existingIn, String key, MessageHandler onMessage) throws IOException {
        String accept;
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update((key + WS_MAGIC).getBytes(StandardCharsets.UTF_8));
            accept = Base64.getEncoder().encodeToString(sha1.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 not available", e);
        }

        OutputStream out = new BufferedOutputStream(socket.getOutputStream());
        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + accept + "\r\n" +
                "\r\n";
        out.write(response.getBytes(StandardCharsets.US_ASCII));
        out.flush();

        // Reuse existingIn — do NOT create new BufferedInputStream(socket.getInputStream())
        // because existingIn may have buffered data past the HTTP headers
        registry.add(out);
        try {
            readLoop(existingIn, out, onMessage);
        } finally {
            registry.remove(out);
        }
    }

    private void readLoop(InputStream in, OutputStream out, MessageHandler onMessage) throws IOException {
        while (true) {
            String text = readFrame(in);
            if (text == null) break;  // connection closed

            String response = onMessage.handle(text);
            if (response != null) {
                byte[] frame = encodeTextFrame(response);
                synchronized (out) {
                    out.write(frame);
                    out.flush();
                }
            }
        }
    }

    /**
     * Read one WebSocket frame. Returns the text payload, or null on close/EOF.
     * Handles: text (0x1), ping (0x9), pong (0xA), close (0x8).
     * Client frames are always masked per RFC 6455 §5.1.
     */
    static String readFrame(InputStream in) throws IOException {
        int b0 = in.read();
        if (b0 == -1) return null;

        int opcode = b0 & 0x0F;
        int b1 = in.read();
        if (b1 == -1) return null;

        boolean masked = (b1 & 0x80) != 0;
        long payloadLen = b1 & 0x7F;

        if (payloadLen == 126) {
            int hi = in.read(), lo = in.read();
            if (hi == -1 || lo == -1) return null;
            payloadLen = (hi << 8) | lo;
        } else if (payloadLen == 127) {
            payloadLen = 0;
            for (int i = 0; i < 8; i++) {
                int b = in.read();
                if (b == -1) return null;
                payloadLen = (payloadLen << 8) | b;
            }
        }

        byte[] maskKey = new byte[4];
        if (masked) {
            if (in.readNBytes(maskKey, 0, 4) < 4) return null;
        }

        if (payloadLen > 1_000_000) return null;  // reject oversized frames

        byte[] payload = in.readNBytes((int) payloadLen);
        if (payload.length < payloadLen) return null;

        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= maskKey[i % 4];
            }
        }

        return switch (opcode) {
            case 0x1 -> new String(payload, StandardCharsets.UTF_8);  // text
            case 0x8 -> null;  // close
            case 0x9 -> {      // ping → pong
                // pong not needed for our use case, just ignore
                yield readFrame(in);  // read next frame
            }
            case 0xA -> readFrame(in);  // pong → ignore, read next
            default -> readFrame(in);   // skip unknown opcodes
        };
    }

    /** Encode a text message as a WebSocket frame (server→client, unmasked). */
    static byte[] encodeTextFrame(String text) {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        int len = payload.length;

        ByteArrayOutputStream buf = new ByteArrayOutputStream(len + 10);
        buf.write(0x81);  // FIN + text opcode

        if (len < 126) {
            buf.write(len);
        } else if (len < 65536) {
            buf.write(126);
            buf.write((len >> 8) & 0xFF);
            buf.write(len & 0xFF);
        } else {
            buf.write(127);
            for (int i = 7; i >= 0; i--) {
                buf.write((int) ((len >> (8 * i)) & 0xFF));
            }
        }

        buf.writeBytes(payload);
        return buf.toByteArray();
    }

    /** Callback for incoming WebSocket text messages. */
    @FunctionalInterface
    public interface MessageHandler {
        /** Handle a text message. Return response text, or null for no response. */
        String handle(String message);
    }
}
