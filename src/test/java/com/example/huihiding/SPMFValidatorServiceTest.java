package com.example.huihiding;

import com.example.huihiding.service.SPMFValidatorService;
import com.example.huihiding.service.SPMFValidatorService.ValidationMetrics;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SPMFValidatorServiceTest {

    @Test
    void calculateMetrics_shouldReturnExpectedHfMcAc() {
        SPMFValidatorService service = new SPMFValidatorService();

        Set<String> original = Set.of("1 2", "2 3", "5");
        Set<String> sanitized = Set.of("2 3", "4 5");
        Set<String> sensitive = Set.of("1 2");

        ValidationMetrics metrics = service.calculateMetrics(original, sanitized, sensitive);

        assertEquals(0, metrics.hidingFailure());
        assertEquals(1, metrics.missingCost()); // "5" was non-sensitive and removed
        assertEquals(1, metrics.artificialCost()); // "4 5" was newly generated
    }

    @Test
    void integration_demoRunMlhuiminer_whenPathsAreProvided() throws Exception {
        String spmfJar = System.getProperty("spmf.jar");
        String originalDb = System.getProperty("spmf.original.db");
        String sanitizedDb = System.getProperty("spmf.sanitized.db");
        String taxonomy = System.getProperty("spmf.taxonomy");
        String sensitive = System.getProperty("spmf.sensitive");
        String minUtil = System.getProperty("spmf.minutil");

        Assumptions.assumeTrue(spmfJar != null && !spmfJar.isBlank());
        Assumptions.assumeTrue(originalDb != null && !originalDb.isBlank());
        Assumptions.assumeTrue(sanitizedDb != null && !sanitizedDb.isBlank());
        Assumptions.assumeTrue(taxonomy != null && !taxonomy.isBlank());
        Assumptions.assumeTrue(sensitive != null && !sensitive.isBlank());
        Assumptions.assumeTrue(minUtil != null && !minUtil.isBlank());

        Path spmfJarPath = Path.of(spmfJar);
        Path originalDbPath = Path.of(originalDb);
        Path sanitizedDbPath = Path.of(sanitizedDb);
        Path taxonomyPath = Path.of(taxonomy);
        Path sensitivePath = Path.of(sensitive);
        double threshold = Double.parseDouble(minUtil);

        Assumptions.assumeTrue(Files.exists(spmfJarPath));
        Assumptions.assumeTrue(Files.exists(originalDbPath));
        Assumptions.assumeTrue(Files.exists(sanitizedDbPath));
        Assumptions.assumeTrue(Files.exists(taxonomyPath));
        Assumptions.assumeTrue(Files.exists(sensitivePath));

        SPMFValidatorService service = new SPMFValidatorService();
        ValidationMetrics metrics = service.validate(
                spmfJarPath,
                originalDbPath,
                sanitizedDbPath,
                taxonomyPath,
                threshold,
                service.parseSensitiveItemsets(sensitivePath),
                Path.of("target", "spmf-validation-test")
        );

        // Main expected privacy objective
        assertEquals(0, metrics.hidingFailure());
    }
}
