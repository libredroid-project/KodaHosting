package de.kodahosting.kodadash.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.kodahosting.kodadash.KodaDash;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Secure file operations within the server directory.
 * All paths are validated to prevent path traversal attacks.
 */
public class FileManager {
    private final KodaDash plugin;
    private final File rootDir;

    public FileManager(KodaDash plugin) {
        this.plugin = plugin;
        // Server root is the working directory
        this.rootDir = new File(".").getAbsoluteFile();
    }

    /**
     * Check if a path is safe to access (within server dir, not blocked).
     */
    public boolean isPathSafe(String path) {
        try {
            File file = new File(rootDir, path);
            String canonicalRoot = rootDir.getCanonicalPath();
            String canonicalFile = file.getCanonicalPath();
            if (!canonicalFile.startsWith(canonicalRoot)) return false;

            List<String> blocked = plugin.getConfig().getStringList("blocked-paths");
            for (String b : blocked) {
                File blockedFile = new File(rootDir, b);
                try {
                    if (canonicalFile.equals(blockedFile.getCanonicalPath())) return false;
                    // Also block if it's inside a blocked directory
                    if (canonicalFile.startsWith(blockedFile.getCanonicalPath() + File.separator)) return false;
                } catch (IOException ignored) {}
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get file or directory info. Returns appropriate JSON for files vs directories.
     * Used by FilesRoute for unified GET handling.
     */
    public JsonObject getFileOrDirectory(String path) throws IOException, SecurityException {
        if (!isPathSafe(path)) throw new SecurityException("Access denied: blocked path");
        File file = new File(rootDir, path);
        if (!file.exists()) return null;

        JsonObject result = new JsonObject();

        if (file.isDirectory()) {
            result.addProperty("type", "directory");
            result.addProperty("path", path.isEmpty() ? "/" : path);
            result.add("entries", listDirectory(path));
        } else {
            result.addProperty("type", "file");
            result.addProperty("path", path);
            result.addProperty("size", file.length());

            if (file.length() > 1024 * 1024) {
                result.addProperty("tooLarge", true);
            } else {
                try {
                    String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    result.addProperty("content", content);
                    result.addProperty("tooLarge", false);
                } catch (Exception e) {
                    // Binary file or encoding issue
                    result.addProperty("tooLarge", true);
                    result.addProperty("binary", true);
                }
            }
        }

        return result;
    }

    /**
     * List directory contents as JSON array.
     */
    public JsonArray listDirectory(String path) throws IOException {
        if (!isPathSafe(path)) throw new SecurityException("Access denied: blocked path");
        File dir = new File(rootDir, path);
        if (!dir.exists() || !dir.isDirectory()) throw new IOException("Not a directory");

        JsonArray arr = new JsonArray();
        File[] files = dir.listFiles();
        if (files != null) {
            // Sort: directories first, then alphabetically
            java.util.Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

            for (File f : files) {
                // Skip hidden system files
                if (f.getName().startsWith(".")) continue;
                
                JsonObject obj = new JsonObject();
                obj.addProperty("name", f.getName());
                obj.addProperty("isDirectory", f.isDirectory());
                obj.addProperty("size", f.isFile() ? f.length() : 0);
                obj.addProperty("lastModified", f.lastModified());
                arr.add(obj);
            }
        }
        return arr;
    }

    /**
     * Read file content as string.
     */
    public String readFile(String path) throws IOException {
        if (!isPathSafe(path)) throw new SecurityException("Access denied: blocked path");
        File file = new File(rootDir, path);
        if (!file.exists() || !file.isFile()) throw new IOException("Not a file");
        if (file.length() > 1024 * 1024) throw new IOException("File too large (max 1MB)");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * Write content to a file. Creates parent directories if needed.
     *
     * @return true if successful
     */
    public boolean writeFile(String path, String content) throws IOException {
        if (!isPathSafe(path)) throw new SecurityException("Access denied: blocked path");
        File file = new File(rootDir, path);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return true;
    }

    /**
     * Write Base64 content to a file. Creates parent directories if needed.
     */
    public boolean writeBase64File(String path, String base64) throws IOException {
        if (!isPathSafe(path)) throw new SecurityException("Access denied: blocked path");
        File file = new File(rootDir, path);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        byte[] decoded = java.util.Base64.getDecoder().decode(base64);
        Files.write(file.toPath(), decoded);
        return true;
    }

    /**
     * Delete a file or empty directory.
     */
    public void deleteFile(String path) throws IOException {
        if (!isPathSafe(path)) throw new SecurityException("Access denied: blocked path");
        File file = new File(rootDir, path);
        if (!file.exists()) throw new IOException("File not found");
        if (file.isDirectory() && file.list() != null && file.list().length > 0) {
            throw new IOException("Directory not empty");
        }
        Files.delete(file.toPath());
    }

    /**
     * Delete a file or directory (alias for route compatibility).
     *
     * @return true if deleted successfully
     */
    public boolean deleteFileOrDirectory(String path) {
        try {
            deleteFile(path);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Create a directory (and parent directories).
     *
     * @return true if created successfully
     */
    public boolean createDirectory(String path) {
        try {
            if (!isPathSafe(path)) return false;
            File file = new File(rootDir, path);
            Files.createDirectories(file.toPath());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get file size in bytes.
     */
    public long getFileSize(String path) throws IOException {
        if (!isPathSafe(path)) throw new SecurityException("Access denied: blocked path");
        File file = new File(rootDir, path);
        return file.length();
    }
}
