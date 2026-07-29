package de.kodahosting.kodadash.routes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import de.kodahosting.kodadash.KodaDash;
import de.kodahosting.kodadash.server.RouteHandler;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonPrimitive;

/**
 * Handles reading and updating server.properties.
 */
public class SettingsRoute extends RouteHandler {

    public SettingsRoute(KodaDash plugin) {
        super(plugin);
        // server.properties is in the server root directory
    }

    private File getServerPropertiesFile() {
        return new File(".", "server.properties").getAbsoluteFile();
    }

    @Override
    protected void handleGet(HttpExchange exchange) throws IOException {
        File serverPropertiesFile = getServerPropertiesFile();
        if (!serverPropertiesFile.exists()) {
            sendError(exchange, 404, "server.properties not found");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(serverPropertiesFile))) {
            JsonObject properties = new JsonObject();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();
                    properties.addProperty(key, value);
                }
            }

            JsonObject response = new JsonObject();
            response.add("properties", properties);
            sendJson(exchange, 200, response);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to read server.properties: " + e.getMessage());
            sendError(exchange, 500, "Failed to read server.properties");
        }
    }

    @Override
    protected void handlePost(HttpExchange exchange) throws IOException {
        File serverPropertiesFile = getServerPropertiesFile();
        if (!serverPropertiesFile.exists()) {
            sendError(exchange, 404, "server.properties not found");
            return;
        }

        String body = readBody(exchange);
        if (body == null || body.trim().isEmpty()) {
            sendError(exchange, 400, "Missing request body");
            return;
        }

        try {
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            if (!json.has("properties")) {
                sendError(exchange, 400, "Missing 'properties' object");
                return;
            }

            JsonObject newProperties = json.getAsJsonObject("properties");
            List<String> lines = new ArrayList<>();
            JsonArray updatedKeys = new JsonArray();

            // Read all lines, preserving comments and empty lines
            try (BufferedReader reader = new BufferedReader(new FileReader(serverPropertiesFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        lines.add(line);
                        continue;
                    }

                    int equalsIndex = line.indexOf('=');
                    if (equalsIndex > 0) {
                        String key = line.substring(0, equalsIndex).trim();
                        if (newProperties.has(key)) {
                            String newValue = newProperties.get(key).getAsString();
                            lines.add(key + "=" + newValue);
                            updatedKeys.add(new JsonPrimitive(key));
                            newProperties.remove(key);
                        } else {
                            lines.add(line);
                        }
                    } else {
                        lines.add(line);
                    }
                }
            }

            // Append any new properties that weren't in the file
            for (Map.Entry<String, com.google.gson.JsonElement> entry : newProperties.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue().getAsString();
                lines.add(key + "=" + value);
                updatedKeys.add(new JsonPrimitive(key));
            }

            // Write back to file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(serverPropertiesFile))) {
                for (String outLine : lines) {
                    writer.write(outLine);
                    writer.newLine();
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.add("updated", updatedKeys);
            response.addProperty("requiresRestart", true);

            sendJson(exchange, 200, response);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update server.properties: " + e.getMessage());
            sendError(exchange, 400, "Failed to update server.properties: " + e.getMessage());
        }
    }
}
