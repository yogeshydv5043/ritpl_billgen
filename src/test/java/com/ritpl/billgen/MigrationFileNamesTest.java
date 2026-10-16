package com.ritpl.billgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Flyway reads the file names in {@code db/migration} as the contract with every database that has ever
 * started, so the two ways to break a colleague's local development database both live in that directory:
 * two files claiming the same version, or a gap where a version was renamed away. A duplicate version is a
 * startup failure whose message names neither file clearly, and a renamed one leaves a database that
 * already applied the old name trying to apply a migration it may have half-applied.
 *
 * <p>Neither is caught by a schema test, because both are properties of the file set rather than of the
 * SQL. Checked here instead so the mistake has to survive a test run before it survives a pull.</p>
 */
class MigrationFileNamesTest {

    @Test
    void everyVersionAppearsOnceAndInOrder() throws IOException {
        List<Integer> versions = versions();
        assertTrue(!versions.isEmpty(), "the migrations are on the classpath for Flyway to find");
        assertEquals(new TreeSet<>(versions).size(), versions.size(),
                "no two migration files may claim the same version: " + versions);
        for (int i = 0; i < versions.size(); i++) {
            assertEquals(i + 1, versions.get(i),
                    "versions run from V1 with no gaps, so every database applies the same sequence");
        }
    }

    @Test
    void everyFileNameFollowsTheVersionAndDescriptionShape() throws IOException {
        List<String> names = files();
        for (String name : names) {
            assertTrue(name.matches("V\\d+__[a-z0-9_]+\\.sql"), name
                    + " must be V<version>__<underscored_description>.sql - a description with spaces or a"
                    + " stray dash changes how Flyway identifies the migration");
        }
    }

    @Test
    void noMigrationIsNamedAfterSomethingAlreadyApplied() throws IOException {
        List<String> descriptions = files().stream().map(name -> name.substring(name.indexOf('__') + 2))
                .toList();
        assertEquals(new java.util.HashSet<>(descriptions).size(), descriptions.size(),
                "two files describing the same change in different words is how the same table ends up "
                        + "altered twice");
    }

    private List<Integer> versions() throws IOException {
        List<Integer> versions = new ArrayList<>();
        for (String name : files()) {
            String head = name.substring(1, name.indexOf("__"));
            versions.add(Integer.parseInt(head));
        }
        versions.sort(Integer::compareTo);
        return versions;
    }

    private List<String> files() throws IOException {
        List<String> names = new ArrayList<>();
        for (Resource resource : new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/*.sql")) {
            String name = resource.getFilename();
            if (name != null) names.add(name);
        }
        assertTrue(names.size() > 1, "the classpath was found");
        return names;
    }
}
