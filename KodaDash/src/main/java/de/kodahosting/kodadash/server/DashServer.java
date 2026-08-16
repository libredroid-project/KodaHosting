package de.kodahosting.kodadash.server;

import com.sun.net.httpserver.HttpServer;
import de.kodahosting.kodadash.KodaDash;
import de.kodahosting.kodadash.routes.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP server for the KodaDash dashboard.
 * Uses JDK's built-in HttpServer for zero external dependencies.
 */
public class DashServer {
    private final KodaDash plugin;
    private HttpServer server;
    private ExecutorService executor;

    public DashServer(KodaDash plugin) {
        this.plugin = plugin;
    }

    /**
     * Start the dashboard HTTP server with all API routes.
     */
    public void start() {
        int port = plugin.getConfig().getInt("port", 7867);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // API routes
            server.createContext("/api/auth", new AuthRoute(plugin));
            server.createContext("/api/server", new ServerRoute(plugin));
            server.createContext("/api/console", new ConsoleRoute(plugin));
            server.createContext("/api/players", new PlayersRoute(plugin));
            server.createContext("/api/files", new FilesRoute(plugin));
            server.createContext("/api/settings", new SettingsRoute(plugin));
            server.createContext("/api/plugins", new PluginsRoute(plugin));

            // Static web UI files (SPA fallback)
            server.createContext("/", new StaticHandler(plugin));
            server.createContext("/resourcepack.zip", new ResourcepackRoute(plugin));

            executor = Executors.newFixedThreadPool(10);
            server.setExecutor(executor);
            server.start();
            plugin.getLogger().info("KodaDash dashboard started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start dashboard server: " + e.getMessage());
        }
    }

    /**
     * Gracefully stop the HTTP server.
     */
    public void stop() {
        if (server != null) {
            server.stop(3);
            plugin.getLogger().info("Dashboard server stopped.");
        }
        if (executor != null) {
            executor.shutdown();
        }
    }

    /**
     * @return The configured dashboard port.
     */
    public int getPort() {
        return plugin.getConfig().getInt("port", 7867);
    }
}
