package de.kodahosting.kodadash.managers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import de.kodahosting.kodadash.KodaDash;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures server console output and manages SSE streaming to dashboard clients.
 */
public class ConsoleManager {
    private final KodaDash plugin;
    private final LinkedList<JsonObject> buffer = new LinkedList<>();
    private final int maxLines;
    private final List<SseClient> sseClients = new CopyOnWriteArrayList<>();
    private final Handler logHandler;
    private final Gson gson = new Gson();
    private int totalLines = 0;

    private static class SseClient {
        final OutputStream stream;
        final HttpExchange exchange;

        SseClient(OutputStream stream, HttpExchange exchange) {
            this.stream = stream;
            this.exchange = exchange;
        }
    }

    public ConsoleManager(KodaDash plugin) {
        this.plugin = plugin;
        this.maxLines = plugin.getConfig().getInt("console-buffer-size", 500);

        logHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record == null || record.getMessage() == null) return;
                
                JsonObject json = new JsonObject();
                json.addProperty("index", totalLines);
                json.addProperty("timestamp", record.getMillis());
                json.addProperty("level", record.getLevel().getName());
                json.addProperty("message", formatMessage(record));

                synchronized (buffer) {
                    buffer.add(json);
                    totalLines++;
                    if (buffer.size() > maxLines) {
                        buffer.removeFirst();
                    }
                }

                broadcastSse(json);
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };

        Logger.getLogger("").addHandler(logHandler);
    }

    /**
     * Clean up resources on plugin disable.
     */
    public void cleanup() {
        Logger.getLogger("").removeHandler(logHandler);
        for (SseClient client : sseClients) {
            try {
                client.stream.close();
            } catch (IOException ignored) {}
        }
        sseClients.clear();
    }

    /**
     * Get all buffered console lines.
     */
    public List<JsonObject> getRecentLines() {
        synchronized (buffer) {
            return new LinkedList<>(buffer);
        }
    }

    /**
     * Get the last N console lines.
     */
    public List<JsonObject> getRecentLines(int count) {
        synchronized (buffer) {
            int size = buffer.size();
            int start = Math.max(0, size - count);
            return new LinkedList<>(buffer.subList(start, size));
        }
    }

    /**
     * Get console lines since a given index.
     */
    public List<JsonObject> getLinesSince(int startIndex) {
        synchronized (buffer) {
            if (buffer.isEmpty()) return new LinkedList<>();
            // The buffer is a sliding window - find the offset
            int firstIndex = totalLines - buffer.size();
            int offset = startIndex - firstIndex;
            if (offset < 0) offset = 0;
            if (offset >= buffer.size()) return new LinkedList<>();
            return new LinkedList<>(buffer.subList(offset, buffer.size()));
        }
    }

    /**
     * Get total number of lines captured since plugin start.
     */
    public int getTotalLines() {
        return totalLines;
    }

    /**
     * Register an SSE listener stream for real-time console updates.
     */
    public void registerSseListener(OutputStream stream, HttpExchange exchange) {
        sseClients.add(new SseClient(stream, exchange));
    }

    /**
     * Register an SSE listener stream.
     */
    public void addListener(OutputStream stream) {
        sseClients.add(new SseClient(stream, null));
    }

    /**
     * Remove an SSE listener.
     */
    public void removeListener(OutputStream stream) {
        sseClients.removeIf(client -> client.stream == stream);
    }

    /**
     * Get the number of connected SSE clients.
     */
    public int getConnectedClients() {
        return sseClients.size();
    }

    /**
     * Broadcast a console line to all connected SSE clients.
     */
    private void broadcastSse(JsonObject json) {
        String event = "data: " + gson.toJson(json) + "\n\n";
        byte[] bytes = event.getBytes(StandardCharsets.UTF_8);
        for (SseClient client : sseClients) {
            try {
                client.stream.write(bytes);
                client.stream.flush();
            } catch (IOException e) {
                // Client disconnected
                sseClients.remove(client);
            }
        }
    }

    /**
     * Execute a server command on the main thread.
     * Checks against blocked commands list.
     */
    public void executeCommand(String command) {
        if (command == null || command.trim().isEmpty()) return;

        List<String> blocked = plugin.getConfig().getStringList("blocked-commands");
        String cmdBase = command.trim().split("\\s+")[0].toLowerCase();
        for (String b : blocked) {
            if (cmdBase.equalsIgnoreCase(b.toLowerCase())) {
                plugin.getLogger().warning("Blocked command attempt via dashboard: " + command);
                return;
            }
        }

        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    /**
     * Format a log record message, handling parameter substitution.
     */
    private String formatMessage(LogRecord record) {
        String message = record.getMessage();
        if (message == null) return "";
        if (record.getParameters() != null && record.getParameters().length > 0) {
            try {
                return String.format(message, record.getParameters());
            } catch (Exception e) {
                return message;
            }
        }
        return message;
    }
}
