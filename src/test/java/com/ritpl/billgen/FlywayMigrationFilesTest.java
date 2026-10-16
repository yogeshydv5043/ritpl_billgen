package com.ritpl.billgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The files in {@code db/migration} are a contract with every database that has ever started, not just with
 * the next one. Three rules follow from that, and none of them is visible from the SQL itself:
 *
 * <ol>
 *   <li>one file per version, with no gaps - two files claiming a version stops Flyway before it reads
 *       either, and a renamed one leaves a database that applied the old name half-migrated;</li>
 *   <li>the exact {@code V<n>__under_scored_description.sql} shape, because the description is part of how
 *       Flyway identifies a migration;</li>
 *   <li>an applied version's bytes never change. Flyway stores a checksum per version, so editing an old
 *       migration does not update a database - it fails that database's startup with an unsatisfied
 *       dependency on {@code jpaSharedEM_entityManagerFactory}, which names nothing near its cause. The
 *       pinned values below are what makes that mistake fail a test instead of a colleague's machine.</li>
 * </ol>
 */
class FlywayMigrationFilesTest {

    /**
     * Every migration that has shipped, and the checksum Flyway records for its bytes. A version earns its
     * line here when the pull request that introduced it merges; until then its file is still being written
     * and must stay free to change. V10 and V12 are here precisely because both were edited after they had
     * been applied somewhere - the corrections now live in V14 and V13, and these files are frozen.
     */
    private static final Map<Integer, Integer> RELEASED_CHECKSUMS = Map.ofEntries(
            Map.entry(1, 2112754172),
            Map.entry(2, 1549890582),
            Map.entry(3, 1816248538),
            Map.entry(4, -2062716530),
            Map.entry(5, 367628607),
            Map.entry(6, 835591937),
            Map.entry(7, 824459935),
            Map.entry(8, 242178880),
            Map.entry(9, 1324042976),
            Map.entry(10, 1708328131),
            Map.entry(11, 614193874));

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
        for (String name : files()) {
            assertTrue(name.matches("V\\d+__[a-z0-9_]+\\.sql"), name
                    + " must be V<version>__<underscored_description>.sql - spaces or dashes in the name"
                    + " change how Flyway identifies the migration");
        }
    }

    @Test
    void noTwoFilesDescribeTheSameChange() throws IOException {
        List<String> descriptions = files().stream().map(name -> name.substring(name.indexOf("__") + 2))
                .toList();
        assertEquals(new HashSet<>(descriptions).size(), descriptions.size(),
                "two files describing the same change in different words is how one table ends up altered twice");
    }

    /** Flyway's own algorithm: CRC32 over the UTF-8 bytes of each line, separators excluded, signed. */
    @Test
    void aMigrationThatHasBeenAppliedIsNeverRewritten() throws IOException {
        for (Resource resource : resources()) {
            String name = resource.getFilename();
            assertNotNull(name, "a migration on the classpath has a name");
            int version = Integer.parseInt(name.substring(1, name.indexOf("__")));
            Integer expected = RELEASED_CHECKSUMS.get(version);
            if (expected == null) continue;
            try (InputStream in = resource.getInputStream()) {
                int actual = checksum(in.readAllBytes());
                assertEquals(expected, actual, "V" + version + " (" + name
                        + ") has been applied to other databases, so its bytes are part of their history."
                        + " Add V" + (highest() + 1) + " with the change instead of editing this file,"
                        + " and see docs/setup.md if your own development database is already in between.");
            }
        }
    }

    private static int checksum(byte[] bytes) {
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        CRC32 crc = new CRC32();
        for (String line : text.split("\n", -1)) {
            crc.update(line.endsWith("\r") ? line.substring(0, line.length() - 1)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    : line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return (int) crc.getValue();
    }

    private static int highest() throws IOException {
        return versions().get(versions().size() - 1);
    }

    private List<Integer> versions() throws IOException {
        List<Integer> versions = new ArrayList<>();
        for (String name : files()) versions.add(Integer.parseInt(name.substring(1, name.indexOf("__"))));
        versions.sort(Integer::compareTo);
        return versions;
    }

    private List<String> files() throws IOException {
        List<String> names = new ArrayList<>();
        for (Resource resource : resources()) {
            String name = resource.getFilename();
            if (name != null) names.add(name);
        }
        return names;
    }

    private Resource[] resources() throws IOException {
        Resource[] found = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/*.sql");
        assertTrue(found.length > 1, "db/migration was found on the classpath");
        return found;
    }
}
