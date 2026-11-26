package com.muletracer.api;

import com.muletracer.graph.TemporalGraph;
import com.muletracer.investigate.Investigation;
import com.muletracer.investigate.Investigator;
import com.muletracer.io.InvestigationJson;
import com.muletracer.io.Json;
import com.muletracer.model.Transaction;
import com.muletracer.trace.TraceConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Minimal REST API (JDK HttpServer, no frameworks) a bank's fraud desk or the 1930 helpline could call.
 *
 * <pre>
 * GET  /api/health
 * GET  /api/investigate?utr=UTR000000001,UTR000000002&amp;reportTime=1795156200
 *      -> trail, freeze orders, chokepoints, rings, watchlist (amounts in paise)
 * </pre>
 */
public final class TracerServer {

    private final HttpServer server;

    public TracerServer(TemporalGraph graph, int port) throws IOException {
        Investigator investigator = new Investigator(graph, TraceConfig.defaults());
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors())));

        server.createContext("/api/health", ex -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", "UP");
            m.put("accounts", graph.accountCount());
            m.put("transactions", graph.transactionCount());
            send(ex, 200, m);
        });

        server.createContext("/api/investigate", ex -> {
            try {
                Map<String, String> q = query(ex.getRequestURI().getRawQuery());
                String utrs = q.get("utr");
                if (utrs == null || utrs.isBlank()) {
                    send(ex, 400, Map.of("error", "query parameter 'utr' (comma-separated fraud UTRs) is required"));
                    return;
                }
                List<Transaction> fraud = new ArrayList<>();
                for (String utr : utrs.split(",")) {
                    Transaction t = graph.transaction(utr.trim());
                    if (t == null) {
                        send(ex, 404, Map.of("error", "unknown UTR " + utr.trim()));
                        return;
                    }
                    fraud.add(t);
                }
                long reportTime = q.containsKey("reportTime") ? Long.parseLong(q.get("reportTime"))
                        : System.currentTimeMillis() / 1000;
                Investigation inv = investigator.investigate(fraud, reportTime);
                send(ex, 200, InvestigationJson.toMap(inv));
            } catch (NumberFormatException e) {
                send(ex, 400, Map.of("error", "reportTime must be epoch seconds"));
            } catch (RuntimeException e) {
                send(ex, 500, Map.of("error", String.valueOf(e.getMessage())));
            }
        });
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null) {
            return out;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                out.put(URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static void send(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
