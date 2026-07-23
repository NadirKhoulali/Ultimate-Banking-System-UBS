package net.austizz.ultimatebankingsystem.bank.safebox;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class IsolatedServerClassPath {
    private IsolatedServerClassPath() {
    }

    static ClassLoader childFirst(ClassLoader parent) {
        return new ChildFirstUrlClassLoader(urls(), parent);
    }

    static Path buildPath(String first, String... more) {
        return buildRoot().resolve(Path.of(first, more));
    }

    static Path productionClasses() {
        return buildPath("classes", "java", "main");
    }

    static Path loadedOrigin(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to resolve loaded class origin for " + type.getName(), exception);
        }
    }

    static Path productionClassFile(Class<?> type) {
        return productionClasses().resolve(type.getName().replace('.', File.separatorChar) + ".class");
    }

    static byte[] loadedClassBytes(Class<?> type) {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Loaded class resource is unavailable: " + resource);
            }
            return input.readAllBytes();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read loaded class bytes: " + resource, exception);
        }
    }

    static String serverClasspath() {
        StringBuilder out = new StringBuilder();
        for (URL url : urls()) {
            if (!out.isEmpty()) {
                out.append(File.pathSeparator);
            }
            try {
                out.append(Path.of(url.toURI()));
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to render classpath URL: " + url, exception);
            }
        }
        return out.toString();
    }

    private static URL[] urls() {
        List<URL> urls = new ArrayList<>();
        addUrl(urls, productionClasses());
        addRuntimeClasspath(urls);
        Path legacyClasspath = buildPath("moddev", "serverLegacyClasspath.txt");
        if (!Files.exists(legacyClasspath)) {
            legacyClasspath = buildPath("moddev", "gameTestServerLegacyClasspath.txt");
        }
        if (Files.exists(legacyClasspath)) {
            try {
                for (String line : Files.readAllLines(legacyClasspath)) {
                    if (!line.isBlank()) {
                        addUrl(urls, Path.of(line));
                    }
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to read server test classpath: " + legacyClasspath,
                        exception);
            }
        }
        addUrl(urls, buildPath("moddev", "artifacts", "neoforge-21.1.220.jar"));
        return urls.toArray(URL[]::new);
    }

    private static void addRuntimeClasspath(List<URL> urls) {
        String runtimeClasspath = System.getProperty("ubs.testRuntimeClasspath", "").trim();
        if (runtimeClasspath.isEmpty()) {
            return;
        }
        for (String entry : runtimeClasspath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                addUrl(urls, Path.of(entry));
            }
        }
    }

    private static Path buildRoot() {
        String configured = System.getProperty("ubs.buildDir", "").trim();
        if (!configured.isEmpty()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("ubs.projectDir", "."))
                .resolve("build").toAbsolutePath().normalize();
    }

    private static void addUrl(List<URL> urls, Path path) {
        try {
            urls.add(path.toUri().toURL());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to prepare server test classpath: " + path, exception);
        }
    }

    private static final class ChildFirstUrlClassLoader extends URLClassLoader {
        private ChildFirstUrlClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("net.minecraft.")
                    && !name.startsWith("net.neoforged.")
                    && !name.startsWith("net.austizz.ultimatebankingsystem.")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loaded = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
