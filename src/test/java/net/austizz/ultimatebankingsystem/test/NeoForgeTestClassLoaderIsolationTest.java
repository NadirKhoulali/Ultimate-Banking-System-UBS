package net.austizz.ultimatebankingsystem.test;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForgeTestClassLoaderIsolationTest {
    private static final List<String> REPRESENTATIVE_PRODUCTION_CLASSES = List.of(
            "net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService",
            "net.austizz.ultimatebankingsystem.network.ClientPayloadHandlers",
            "net.austizz.ultimatebankingsystem.network.OwnerPcPremiseActionResponseClientHandler"
    );

    @Test
    void representativeProductionClassesLoadFromConfiguredJar() throws Exception {
        Path configured = requiredPath("ubs.testMainArtifact");
        assertTrue(Files.isRegularFile(configured), "configured main artifact must be a jar file");

        for (String className : REPRESENTATIVE_PRODUCTION_CLASSES) {
            Class<?> productionClass = Class.forName(className, true, NeoForgeTestClassLoader.get());
            Path loadedFrom = Path.of(productionClass.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath().normalize();
            assertEquals(configured, loadedFrom, className);
        }
    }

    @Test
    void ubsClassesMissingFromConfiguredJarDoNotFallBackToParent() throws Exception {
        String testClassName = NeoForgeTestClassLoaderIsolationTest.class.getName();
        assertSame(NeoForgeTestClassLoaderIsolationTest.class,
                Class.forName(testClassName, false, getClass().getClassLoader()));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName(testClassName, false, NeoForgeTestClassLoader.get()));
    }

    private static Path requiredPath(String property) {
        String value = System.getProperty(property, "").trim();
        assertTrue(!value.isBlank(), property + " must be configured by Gradle");
        return Path.of(value).toAbsolutePath().normalize();
    }
}
