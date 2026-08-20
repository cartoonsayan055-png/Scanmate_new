package org.gradle.wrapper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal wrapper-compatible fallback launcher for this project ZIP.
 * It preserves the normal org.gradle.wrapper.GradleWrapperMain entry point used by gradlew/gradlew.bat.
 * Prefer an installed Gradle first, otherwise download the distribution declared in gradle-wrapper.properties.
 */
public final class GradleWrapperMain {
    private GradleWrapperMain() {}

    public static void main(String[] args) throws Exception {
        File root = projectRoot();
        File systemGradle = findSystemGradle();
        if (systemGradle != null) {
            exec(systemGradle, args, root);
            return;
        }

        File propsFile = new File(root, "gradle/wrapper/gradle-wrapper.properties");
        if (!propsFile.isFile()) {
            throw new IOException("Missing gradle/wrapper/gradle-wrapper.properties");
        }
        Properties props = new Properties();
        try (FileInputStream input = new FileInputStream(propsFile)) {
            props.load(input);
        }
        String distributionUrl = props.getProperty("distributionUrl", "").replace("\\:", ":");
        if (distributionUrl.isEmpty()) {
            throw new IOException("distributionUrl is missing from gradle-wrapper.properties");
        }

        File fallbackHome = new File(root, ".gradle-wrapper-fallback");
        if (!fallbackHome.exists() && !fallbackHome.mkdirs()) {
            throw new IOException("Unable to create " + fallbackHome);
        }
        String fileName = distributionUrl.substring(distributionUrl.lastIndexOf('/') + 1);
        String distName = fileName.endsWith(".zip") ? fileName.substring(0, fileName.length() - 4) : fileName;
        File gradleBin = new File(new File(fallbackHome, distName), isWindows() ? "bin/gradle.bat" : "bin/gradle");
        if (!gradleBin.isFile()) {
            File zipFile = new File(fallbackHome, fileName);
            try {
                download(distributionUrl, zipFile);
            } catch (IOException e) {
                throw new IOException("Unable to download Gradle distribution. Check internet access or install Gradle 8.9 locally.", e);
            }
            unzip(zipFile, fallbackHome.toPath());
            if (!isWindows()) gradleBin.setExecutable(true);
        }
        exec(gradleBin, args, root);
    }

    private static File projectRoot() throws IOException {
        String userDir = System.getProperty("user.dir", ".");
        File dir = new File(userDir).getCanonicalFile();
        while (dir != null) {
            if (new File(dir, "settings.gradle.kts").isFile() || new File(dir, "settings.gradle").isFile()) {
                return dir;
            }
            dir = dir.getParentFile();
        }
        return new File(userDir).getCanonicalFile();
    }

    private static File findSystemGradle() {
        String path = System.getenv("PATH");
        if (path == null || path.isEmpty()) return null;
        String gradleName = isWindows() ? "gradle.bat" : "gradle";
        for (String entry : path.split(File.pathSeparator)) {
            File candidate = new File(entry, gradleName);
            if (candidate.isFile() && candidate.canExecute()) return candidate;
        }
        return null;
    }

    private static void download(String url, File target) throws IOException {
        System.err.println("Downloading Gradle distribution: " + url);
        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(60_000);
        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static void unzip(File zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path resolved = targetDir.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(targetDir)) {
                    throw new IOException("Blocked unsafe ZIP entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    try (FileOutputStream output = new FileOutputStream(resolved.toFile())) {
                        byte[] buffer = new byte[32 * 1024];
                        int read;
                        while ((read = zip.read(buffer)) >= 0) {
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static void exec(File gradleBinary, String[] args, File workingDir) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(gradleBinary.getAbsolutePath());
        for (String arg : args) command.add(arg);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir);
        builder.inheritIO();
        Process process = builder.start();
        int exit = process.waitFor();
        if (exit != 0) System.exit(exit);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
