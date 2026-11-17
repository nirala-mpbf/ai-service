package com.example.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Local test double for an OpenAI-compatible model gateway and MCP. */
final class HttpBackend implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final ObjectMapper json = new ObjectMapper();

    private static Map<String, Object> workflowSchema(String operation) {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "metadata",
                        Map.of(
                                "type",
                                "object",
                                "properties",
                                Map.of(
                                        "operation", Map.of("const", operation),
                                        "referenceId", Map.of("type", "string")))));
    }

    final AtomicInteger initializations = new AtomicInteger(), toolCalls = new AtomicInteger();
    final List<JsonNode> mcpRequests = new CopyOnWriteArrayList<>();
    final List<String> modelRequests = new CopyOnWriteArrayList<>();
    final List<String> errors = new CopyOnWriteArrayList<>();

    HttpBackend() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(workers);
        server.createContext("/mcp", exchange -> {
            try {
                if (!exchange.getRequestMethod().equals("POST")) {
                    exchange.sendResponseHeaders(405, -1);
                    exchange.close();
                    return;
                }
                JsonNode input = json.readTree(exchange.getRequestBody());
                if (!input.has("id")) {
                    exchange.sendResponseHeaders(202, -1);
                    exchange.close();
                    return;
                }
                Object result = switch (input.path("method").asText()) {
                    case "initialize" -> {
                        initializations.incrementAndGet();
                        yield Map.of(
                                "protocolVersion",
                                input.path("params").path("protocolVersion").asText(),
                                "capabilities",
                                Map.of("tools", Map.of()),
                                "serverInfo",
                                Map.of("name", "test", "version", "1"));
                    }
                    case "tools/list" ->
                        Map.of(
                                "tools",
                                List.of(
                                        Map.of(
                                                "name",
                                                "processWorkflowCommand",
                                                "description",
                                                "Get status",
                                                "inputSchema",
                                                Map.of(
                                                        "type",
                                                        "object",
                                                        "properties",
                                                        Map.of(
                                                                "body",
                                                                Map.of(
                                                                        "oneOf",
                                                                        List.of(
                                                                                workflowSchema("GET_STATE"),
                                                                                workflowSchema("SUBMIT_EVENT"))))),
                                                "annotations",
                                                Map.of("readOnlyHint", false)),
                                        Map.of(
                                                "name",
                                                "verify_otp",
                                                "description",
                                                "Verify OTP",
                                                "inputSchema",
                                                Map.of("type", "object", "properties", Map.of()),
                                                "annotations",
                                                Map.of("readOnlyHint", false))));
                    case "tools/call" -> {
                        toolCalls.incrementAndGet();
                        mcpRequests.add(input.path("params"));
                        yield Map.of(
                                "content",
                                List.of(Map.of(
                                        "type",
                                        "text",
                                        "text",
                                        "status:"
                                                + input.path("params")
                                                        .path("arguments")
                                                        .path("body")
                                                        .path("metadata")
                                                        .path("referenceId")
                                                        .asText())),
                                "isError",
                                false);
                    }
                    default -> Map.of();
                };
                byte[] body = json.writeValueAsBytes(Map.of("jsonrpc", "2.0", "id", input.get("id"), "result", result));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } catch (Exception error) {
                errors.add(error.toString());
                exchange.close();
            }
        });
        server.createContext("/v1", exchange -> {
            try {
                JsonNode input = json.readTree(exchange.getRequestBody());
                modelRequests.add(input.toString());
                String query = "", prompt = "";
                boolean hasToolResult = false;
                String toolResult = "";
                for (JsonNode message : input.path("messages")) {
                    if (message.path("role").asText().equals("user"))
                        query = message.path("content").asText();
                    if (message.path("role").asText().equals("system"))
                        prompt = message.path("content").asText();
                    if (message.path("role").asText().equals("tool")) {
                        hasToolResult = true;
                        toolResult = message.path("content").toString();
                    }
                }
                boolean callTool = input.path("tools").size() > 0 && !hasToolResult;
                Object delta = callTool
                        ? Map.of(
                                "role",
                                "assistant",
                                "tool_calls",
                                List.of(Map.of(
                                        "index",
                                        0,
                                        "id",
                                        "call-" + query,
                                        "type",
                                        "function",
                                        "function",
                                        Map.of(
                                                "name",
                                                query.equals("no-reference") ? "verify_otp" : "processWorkflowCommand",
                                                "arguments",
                                                json.writeValueAsString(
                                                        query.equals("no-reference")
                                                                ? Map.of()
                                                                : Map.of(
                                                                        "body",
                                                                        Map.of(
                                                                                "metadata",
                                                                                        Map.of(
                                                                                                "operation",
                                                                                                "GET_STATE",
                                                                                                "requestId",
                                                                                                "req-" + query,
                                                                                                "idempotencyKey",
                                                                                                "idem-" + query,
                                                                                                "referenceId",
                                                                                                "WRONG"),
                                                                                "payload", Map.of())))))))
                        : Map.of("role", "assistant", "content", toolResult);
                Map<String, Object> chunk = Map.of(
                        "id",
                        "test",
                        "object",
                        "chat.completion.chunk",
                        "created",
                        1,
                        "model",
                        "test-model",
                        "choices",
                        List.of(Map.of("index", 0, "delta", delta, "finish_reason", "")));
                byte[] body = ("data: "
                                + json.writeValueAsString(chunk)
                                + "\n\ndata: "
                                + json.writeValueAsString(Map.of(
                                        "id",
                                        "test",
                                        "object",
                                        "chat.completion.chunk",
                                        "created",
                                        1,
                                        "model",
                                        "test-model",
                                        "choices",
                                        List.of(Map.of(
                                                "index",
                                                0,
                                                "delta",
                                                Map.of(),
                                                "finish_reason",
                                                callTool ? "tool_calls" : "stop"))))
                                + "\n\ndata: [DONE]\n\n")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } catch (Exception error) {
                errors.add(error.toString());
                exchange.close();
            }
        });
        server.start();
    }

    String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        workers.shutdownNow();
    }
}
