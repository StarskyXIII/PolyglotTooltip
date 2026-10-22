package com.starskyxiii.polyglottooltip.testbridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import java.util.concurrent.CompletableFuture;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class BridgeServer {
    private final int port;
    private NioEventLoopGroup accept, workers;
    private Channel channel;
    private BridgeFallbackServer fallback;

    public BridgeServer(int port) {
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Invalid bridge port");
        this.port = port;
    }
    public int port() { return port; }

    public void start() throws InterruptedException {
        try {
            accept = new NioEventLoopGroup(1);
            workers = new NioEventLoopGroup(1);
            channel = new ServerBootstrap().group(accept, workers).channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, 2 * 1024 * 1024)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel socket) {
                            socket.pipeline().addLast(new HttpServerCodec(), new HttpObjectAggregator(1048576),
                                    new WebSocketServerProtocolHandler("/", null, true, 1048576), new Handler());
                        }
                    }).bind("127.0.0.1", port).sync().channel();
            return;
        } catch (RuntimeException | LinkageError error) {
            // Windows sometimes refuses the loopback connection an NIO selector needs while plain sockets work.
            org.apache.logging.log4j.LogManager.getLogger("PolyglotDevBridge")
                    .warn("Netty cannot listen here ({}); serving the bridge on blocking sockets", String.valueOf(error.getCause() == null ? error : error.getCause()));
            if (accept != null) accept.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            if (workers != null) workers.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            accept = workers = null;
        }
        try { fallback = new BridgeFallbackServer(port); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Bridge cannot listen on 127.0.0.1:" + port, failure); }
    }

    public void close() {
        if (channel != null) channel.close();
        if (accept != null) accept.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        if (workers != null) workers.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        if (fallback != null) fallback.close();
    }

    /** The greeting both transports send once a client is connected. */
    static JsonObject hello() {
        JsonObject hello = new JsonObject();
        hello.addProperty("jsonrpc", "2.0");
        hello.addProperty("method", "hello");
        JsonObject params = new JsonObject();
        params.addProperty("protocol", 1);
        params.addProperty("minecraft", "1.12.2");
        params.addProperty("implementation", "polyglot-devbridge");
        hello.add("params", params);
        return hello;
    }

    /** A JSON-RPC response: the result, or the error with up to three causes and twelve frames each. */
    static JsonObject response(JsonElement id, JsonElement value, Throwable error) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        if (error == null) { response.add("result", value); return response; }
        JsonObject failure = new JsonObject();
        failure.addProperty("code", -32000);
        failure.addProperty("message", error.getClass().getSimpleName() + ": " + error.getMessage());
        com.google.gson.JsonArray trace = new com.google.gson.JsonArray();
        Throwable cause = error;
        for (int depth = 0; depth < 3 && cause != null; depth++, cause = cause.getCause()) {
            trace.add(cause.toString()); int count = 0;
            for (StackTraceElement element : cause.getStackTrace()) { if (count++ >= 12) break; trace.add(element.toString()); }
        }
        failure.add("data", trace);
        response.add("error", failure);
        return response;
    }

    static int timeoutSeconds(String method, int normalSeconds, Integer lifecycleSeconds, String fixtureNonce) {
        int normal = Math.max(120, Math.min(600, normalSeconds));
        if (lifecycleSeconds == null || lifecycleSeconds < 601 || lifecycleSeconds > 3600
                || fixtureNonce == null || fixtureNonce.isEmpty()
                || !("lifecycle.adversary".equals(method) || "lifecycle.preparation".equals(method))) return normal;
        return lifecycleSeconds;
    }

    private static final class Handler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
        private final AtomicInteger pending = new AtomicInteger();
        private final Set<CompletableFuture<JsonElement>> requests =
                Collections.newSetFromMap(new IdentityHashMap<CompletableFuture<JsonElement>, Boolean>());

        @Override public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
            if (event == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                context.writeAndFlush(new TextWebSocketFrame(hello().toString()));
            }
            super.userEventTriggered(context, event);
        }

        @Override protected void channelRead0(ChannelHandlerContext context, TextWebSocketFrame frame) {
            if (pending.incrementAndGet() > 32) {
                context.close();
                return;
            }
            JsonObject request;
            try {
                request = new JsonParser().parse(frame.text()).getAsJsonObject();
                if (!request.has("id") || !request.has("method")) throw new IllegalArgumentException("Invalid request");
            } catch (RuntimeException error) {
                pending.decrementAndGet();
                context.close();
                return;
            }
            JsonElement id = request.get("id");
            JsonObject params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();
            CompletableFuture<JsonElement> result = BridgeDispatch.call(request.get("method").getAsString(), params);
            requests.add(result);
            io.netty.util.concurrent.ScheduledFuture<?> timeout = context.executor().schedule(
                    () -> result.completeExceptionally(new IllegalStateException("Bridge operation timed out")),
                    timeoutSeconds(request.get("method").getAsString(), Integer.getInteger("clientdevbridge.timeoutSeconds", 120),
                            Integer.getInteger("clientdevbridge.lifecycleTimeoutSeconds"),
                            System.getProperty("clientdevbridge.fixtureNonce", "")), TimeUnit.SECONDS);
            result.whenComplete((value, error) -> context.executor().execute(() -> {
                requests.remove(result);
                pending.decrementAndGet();
                timeout.cancel(false);
                JsonObject response = response(id, value, error);
                if (context.channel().isActive() && context.channel().isWritable()) {
                    context.writeAndFlush(new TextWebSocketFrame(response.toString()));
                } else context.close();
            }));
        }

        @Override public void channelInactive(ChannelHandlerContext context) throws Exception {
            for (CompletableFuture<JsonElement> request : requests) request.cancel(false);
            requests.clear();
            super.channelInactive(context);
        }

        @Override public void exceptionCaught(ChannelHandlerContext context, Throwable error) { context.close(); }
    }
}
