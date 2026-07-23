package net.austizz.ultimatebankingsystem.test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class NeoForgeTestClassLoader {
    private static final String UBS_PACKAGE = "net.austizz.ultimatebankingsystem.";
    private static final String MAIN_ARTIFACT_PROPERTY = "ubs.testMainArtifact";
    private static final String BUILD_DIRECTORY_PROPERTY = "ubs.testBuildDir";
    private static final String RUNTIME_CLASSPATH_PROPERTY = "ubs.testRuntimeClasspath";
    private static final ClassLoader INSTANCE = new ChildFirstUrlClassLoader(classpathUrls(),
            NeoForgeTestClassLoader.class.getClassLoader());

    private NeoForgeTestClassLoader() {
    }

    public static ClassLoader get() {
        return INSTANCE;
    }

    private static URL[] classpathUrls() {
        List<URL> urls = new ArrayList<>();
        addUrl(urls, Path.of(requiredProperty(MAIN_ARTIFACT_PROPERTY)));
        String runtimeClasspath = requiredProperty(RUNTIME_CLASSPATH_PROPERTY);
        for (String entry : runtimeClasspath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                addUrl(urls, Path.of(entry));
            }
        }
        Path legacyClasspath = buildPath("moddev", "serverLegacyClasspath.txt");
        if (Files.exists(legacyClasspath)) {
            try {
                for (String line : Files.readAllLines(legacyClasspath)) {
                    if (!line.isBlank()) {
                        addUrl(urls, Path.of(line));
                    }
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to read NeoForge test classpath: " + legacyClasspath,
                        exception);
            }
        }
        return urls.toArray(URL[]::new);
    }

    private static String requiredProperty(String property) {
        String value = System.getProperty(property, "").trim();
        if (value.isBlank()) {
            throw new IllegalStateException("Missing required NeoForge test property: " + property);
        }
        return value;
    }

    private static Path buildPath(String first, String... more) {
        String configured = System.getProperty(BUILD_DIRECTORY_PROPERTY, "").trim();
        Path buildDirectory = configured.isBlank() ? projectPath("build") : Path.of(configured);
        return buildDirectory.resolve(Path.of(first, more));
    }

    private static Path projectPath(String first, String... more) {
        return Path.of(System.getProperty("ubs.projectDir", ".")).resolve(Path.of(first, more));
    }

    private static void addUrl(List<URL> urls, Path path) {
        try {
            urls.add(path.toUri().toURL());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to prepare NeoForge test classpath: " + path, exception);
        }
    }

    private static final class ChildFirstUrlClassLoader extends URLClassLoader {
        private ChildFirstUrlClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(UBS_PACKAGE)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
            if (!name.startsWith("net.minecraft.") && !name.startsWith("net.neoforged.")) {
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
