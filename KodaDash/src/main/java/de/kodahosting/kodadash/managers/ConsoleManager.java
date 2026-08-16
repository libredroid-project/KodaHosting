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
import java.util.logging.Logger;
import java.util.logging.LogRecord;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Captures server console output and manages SSE streaming to dashboard clients.
 */
public class ConsoleManager {
    private final KodaDash plugin;
    private final LinkedList<JsonObject> buffer = new LinkedList<>();
    private final int maxLines;
    private final List<SseClient> sseClients = new CopyOnWriteArrayList<>();
    private final Gson gson = new Gson();
    private static int totalLines = 0;
    private static ConsoleManager instance;
    private static boolean appenderAttached = false;
    private static Object log4jAppenderProxy;
    private static Object log4jRootLogger;
    private static Handler logHandler;

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
        instance = this;
        this.maxLines = plugin.getConfig().getInt("console-buffer-size", 500);

        if (appenderAttached) return;
        appenderAttached = true;
        
        logHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record == null || record.getMessage() == null) return;
                if (instance != null) instance.appendLine(record.getMillis(), record.getLevel().getName(), formatMessage(record));
            }
            @Override public void flush() {}
            @Override public void close() throws SecurityException {}
        };

        try {
            Class<?> logManagerClass = Class.forName("org.apache.logging.log4j.LogManager");
            log4jRootLogger = logManagerClass.getMethod("getRootLogger").invoke(null);
            Class<?> appenderInterface = Class.forName("org.apache.logging.log4j.core.Appender");
            
            log4jAppenderProxy = Proxy.newProxyInstance(
                appenderInterface.getClassLoader(),
                new Class<?>[]{appenderInterface},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if (name.equals("append") && args.length == 1) {
                            Object event = args[0];
                            try {
                                Object messageObj = event.getClass().getMethod("getMessage").invoke(event);
                                String formatted = (String) messageObj.getClass().getMethod("getFormattedMessage").invoke(messageObj);
                                String level = event.getClass().getMethod("getLevel").invoke(event).toString();
                                long time = (long) event.getClass().getMethod("getTimeMillis").invoke(event);
                                if (instance != null) instance.appendLine(time, level, formatted);
                            } catch (Exception e) {}
                            return null;
                        } else if (name.equals("getName")) {
                            return "KodaDashAppender";
                        } else if (name.equals("isStarted")) {
                            return true;
                        } else if (name.equals("isStopped")) {
                            return false;
                        } else if (name.equals("getState")) {
                            try {
                                Class<?> stateClass = Class.forName("org.apache.logging.log4j.core.LifeCycle$State");
                                return Enum.valueOf((Class<Enum>) stateClass, "STARTED");
                            } catch (Exception e) {
                                return null;
                            }
                        } else if (name.equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        } else if (name.equals("equals")) {
                            return proxy == args[0];
                        }
                        return null;
                    }
                }
            );
            
            log4jRootLogger.getClass().getMethod("addAppender", appenderInterface).invoke(log4jRootLogger, log4jAppenderProxy);
        } catch (Throwable t) {
            // Fallback to java.util.logging if Log4j2 is not available
            Logger.getLogger("").addHandler(logHandler);
        }
    }

    private void appendLine(long time, String level, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("index", totalLines);
        json.addProperty("timestamp", time);
        json.addProperty("level", level);
        json.addProperty("message", message);

        synchronized (buffer) {
            buffer.add(json);
            totalLines++;
            if (buffer.size() > maxLines) {
                buffer.removeFirst();
            }
        }
        broadcastSse(json);
    }

    /**
     * Clean up resources on plugin disable.
     */
    public void cleanup() {
        instance = null;
        // Don't try to remove the appender, let the static appender continue running
        // but it will safely do nothing because instance is null.
        
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
