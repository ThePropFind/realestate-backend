package com.realestate.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards on the Flyway migration directory itself.
 *
 * The suite is unit-only — there is no database in CI — so these read the SQL as
 * text. That still catches the two mistakes that actually happen: two files
 * claiming the same version (Flyway refuses to start), and a seed migration that
 * is not idempotent (a re-run against a partially seeded environment duplicates
 * rows, and amenities have no unique constraint on name to stop it).
 */
class MigrationFileTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+)__.+\\.sql$");

    private static List<Path> migrationFiles() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".sql")).toList();
        }
    }

    @Test
    void everyMigrationFollowsTheNamingConventionAndHasAUniqueVersion() throws IOException {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();

        for (Path file : migrationFiles()) {
            String name = file.getFileName().toString();
            Matcher m = VERSIONED.matcher(name);
            assertThat(m.matches())
                    .as("migration %s must be named V{n}__{description}.sql", name)
                    .isTrue();
            if (!seen.add(m.group(1))) duplicates.add(name);
        }

        assertThat(duplicates).as("two migrations cannot share a version number").isEmpty();
    }

    /**
     * V18 seeds the land/farm amenities the post wizard's Features step offers on
     * plot and agricultural listings. It must stay idempotent and keep the 'land'
     * category the client filters on.
     */
    @Test
    void landAmenitySeedIsIdempotentAndCategorised() throws IOException {
        Path v18 = MIGRATIONS.resolve("V18__seed_land_amenities.sql");
        assertThat(Files.exists(v18)).as("V18 land amenity seed is missing").isTrue();

        String sql = Files.readString(v18, StandardCharsets.UTF_8);
        assertThat(sql).contains("INSERT INTO amenities");
        assertThat(sql).contains("WHERE NOT EXISTS");
        assertThat(sql).contains("'land'");
        // Water source, fencing, compound wall and power have dedicated columns on
        // properties — duplicating them as amenities would give sellers two places
        // to say the same thing and the detail page two sources of truth.
        assertThat(sql).doesNotContain("'Borewell'");
        assertThat(sql).doesNotContain("'Fencing'");
        assertThat(sql).doesNotContain("'Compound Wall'");
    }
}
