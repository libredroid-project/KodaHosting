package eu.kodanetwork.mchost.util;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.tukaani.xz.XZInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class TarXzUtil {

    public static void extract(String tarXzPath, String destDir) throws IOException {
        File destFolder = new File(destDir);
        if (!destFolder.exists()) {
            destFolder.mkdirs();
        }

        try (FileInputStream fis = new FileInputStream(tarXzPath);
             BufferedInputStream bis = new BufferedInputStream(fis);
             XZInputStream xzIn = new XZInputStream(bis);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(xzIn)) {

            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry) tarIn.getNextEntry()) != null) {
                File outputFile = new File(destDir, entry.getName());
                
                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else if (entry.isSymbolicLink()) {
                    outputFile.getParentFile().mkdirs();
                    try {
                        java.nio.file.Path link = outputFile.toPath();
                        java.nio.file.Path target = java.nio.file.Paths.get(entry.getLinkName());
                        if (java.nio.file.Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                            java.nio.file.Files.delete(link);
                        }
                        java.nio.file.Files.createSymbolicLink(link, target);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    outputFile.getParentFile().mkdirs();
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = tarIn.read(buffer)) != -1) {
                            bos.write(buffer, 0, length);
                        }
                    }
                    
                    // Preserve executable permissions
                    if (entry.getMode() == 0100755 || entry.getMode() == 0755 || entry.getName().endsWith(".so") || entry.getName().contains("/bin/")) {
                        outputFile.setExecutable(true, false);
                    }
                }
            }
        }
    }
}
