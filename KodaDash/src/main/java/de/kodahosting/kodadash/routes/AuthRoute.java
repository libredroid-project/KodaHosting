package de.kodahosting.kodadash.routes;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import de.kodahosting.kodadash.KodaDash;
import de.kodahosting.kodadash.server.RouteHandler;

import java.io.IOException;

/**
 * Handles authentication for the KodaDash API.
 * This route does NOT require pre-authentication (it IS the auth endpoint).
 */
public class AuthRoute extends RouteHandler {

    public AuthRoute(KodaDash plugin) {
        super(plugin);
    }

    @Override
    protected boolean requireAuth() {
        return false;
    }

    @Override
    protected void handlePost(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        if (body == null || body.trim().isEmpty()) {
            sendError(exchange, 400, "Missing request body");
            return;
        }

        try {
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();

            if (!json.has("token")) {
                sendError(exchange, 400, "Missing token");
                return;
            }

            String token = json.get("token").getAsString();
            String password = json.has("password") ? json.get("password").getAsString() : null;
            String ipAddress = exchange.getRemoteAddress().getAddress().getHostAddress();

            boolean isValid = plugin.getAuthManager().validate(token, password, ipAddress);

            if (!isValid) {
                sendError(exchange, 401, "Invalid credentials");
                return;
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("serverName", plugin.getServer().getName() + " " + plugin.getServer().getVersion());
            response.addProperty("hasPassword", plugin.getAuthManager().hasPassword());

            sendJson(exchange, 200, response);

        } catch (Exception e) {
            plugin.getLogger().warning("Auth error: " + e.getMessage());
            sendError(exchange, 400, "Invalid JSON format");
        }
    }
}
