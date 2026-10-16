package com.ritpl.billgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Pins what the database credential settings actually resolve to, because a development default is
 * only worth having if it applies to the run people really do and the fallback never reaches a
 * deployment.
 *
 * <p>The chain, for both {@code application.yml} and {@code application-dev.yml}:
 * {@code SPRING_DATASOURCE_USERNAME} then {@code DB_USER} then {@code POSTGRES_USER} then
 * {@code postgres}, and the same shape for the password with {@code DB_PASSWORD} and
 * {@code POSTGRES_PASSWORD}. The explicit Spring property wins because it says what the application
 * should use; {@code DB_*} wins over {@code POSTGRES_*} because the latter pair is inherited from a
 * project-root {@code .env} that exists to configure the Compose container, and honouring only half
 * of it is how a local run ends up asking for a role nobody intended with a password that does not
 * belong to it.
 *
 * <p>Neither literal may appear in {@code application-prod.yml}: there every credential is a bare
 * variable reference, so a deployment that forgot a secret fails at startup instead of authenticating
 * as something an operator never chose. {@link #noProfilelessCredentialAndNoLiteralInAnyProfile()} and
 * {@link #productionNamesVariablesAndSuppliesNoFallback()} are the guards for that, and they belong in
 * the build because the alternative is a reviewer diffing YAML by eye.
 *
 * <p>No Spring context is started here on purpose: the point is the text of the configuration files,
 * and a context would resolve it through the environment of whoever ran the build.
 */
class DatabaseCredentialConfigurationTest {

    private static final String URL = "spring.datasource.url";
    private static final String USER = "spring.datasource.username";
    private static final String PASSWORD = "spring.datasource.password";

    private static final Pattern BARE_PLACEHOLDER = Pattern.compile("^\\$\\{[A-Za-z0-9_.]+}$");

    @Test
    void developmentFallsBackToTheLocalSuperuserAndTheExistingDatabase() {
        Map<String, Object> dev = load("application-dev.yml");
        assertEquals("jdbc:postgresql://localhost:5432/billgen", resolve(dev, URL, Map.of()),
                "the database name and port of the development configuration must not move: this is"
                        + " the same local database, opened by a different role");
        assertEquals("postgres", resolve(dev, USER, Map.of()));
        assertEquals("postgres", resolve(dev, PASSWORD, Map.of()));
    }

    @Test
    void theConfigurationWithoutAProfileResolvesToTheSameCredentials() {
        Map<String, Object> base = load("application.yml");
        Map<String, Object> dev = load("application-dev.yml");
        for (String key : new String[] {URL, USER, PASSWORD}) {
            assertEquals(resolve(dev, key, Map.of()), resolve(base, key, Map.of()),
                    key + " has to resolve identically with and without the dev profile, so whether"
                            + " local development works cannot depend on remembering to activate one");
        }
    }

    @Test
    void dbPasswordOverridesTheFallbackAndYieldsToTheExplicitDatasourcePassword() {
        Map<String, Object> dev = load("application-dev.yml");
        assertEquals("from-db-password", resolve(dev, PASSWORD, Map.of("DB_PASSWORD", "from-db-password")),
                "DB_PASSWORD is the one variable a developer should have to learn to point the app at"
                        + " a local cluster whose password is not the installer default");
        assertEquals("from-shell",
                resolve(dev, PASSWORD, Map.of("DB_PASSWORD", "ignored", "SPRING_DATASOURCE_PASSWORD",
                        "from-shell")),
                "the explicit Spring property stays the winner, or a stale DB_PASSWORD would override"
                        + " an intentional choice made in the shell or an IDE run configuration");
    }

    @Test
    void namingOneCredentialDoesNotHalfApplyAgainstTheInheritedComposePair() {
        Map<String, Object> dev = load("application-dev.yml");
        Map<String, String> fromEnvFile = Map.of("POSTGRES_USER", "billgen", "POSTGRES_PASSWORD",
                "billgen_dev_only");
        assertEquals("billgen", resolve(dev, USER, fromEnvFile),
                "a local run with the documented .env still authenticates as the role that file"
                        + " describes; the new fallback is a last resort, not a replacement");
        assertEquals("billgen_dev_only", resolve(dev, PASSWORD, fromEnvFile));
        assertEquals("postgres", resolve(dev, USER, merge(fromEnvFile, Map.of("DB_USER", "postgres"))),
                "DB_USER has to outrank POSTGRES_USER, otherwise a leftover .env pins the username and"
                        + " the developer cannot name the pair they actually have");
        assertEquals("mine", resolve(dev, PASSWORD,
                merge(fromEnvFile, Map.of("DB_PASSWORD", "mine"))));
    }

    @Test
    void theDatabaseNameAndPortAreStillConfigurable() {
        Map<String, Object> dev = load("application-dev.yml");
        assertEquals("jdbc:postgresql://localhost:5432/billgen_v10check",
                resolve(dev, URL, Map.of("POSTGRES_DB", "billgen_v10check")));
        assertEquals("jdbc:postgresql://localhost:5433/other",
                resolve(dev, URL, Map.of("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5433/other")),
                "a whole URL overrides the assembled one, which is how a second database is used for a"
                        + " check without putting the first at risk");
    }

    @Test
    void productionNamesVariablesAndSuppliesNoFallback() {
        Map<String, Object> prod = load("application-prod.yml");
        for (String key : new String[] {URL, USER, PASSWORD, "spring.data.redis.password"}) {
            String raw = raw(prod, key);
            assertTrue(BARE_PLACEHOLDER.matcher(raw).matches(),
                    key + " in the prod profile should be a bare variable reference with no default,"
                            + " and was: " + raw);
        }
        // A placeholder that carries a default is invisible at startup: it resolves, connects somewhere
        // nobody chose and leaves an operator hunting. Resolving against an empty environment proves the
        // prod file cannot do that, in either direction.
        for (String key : new String[] {USER, PASSWORD}) {
            try {
                resolve(raw(prod, key), Map.of());
                fail(key + " resolved with no environment set, so it carries a fallback");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().startsWith("Unresolved placeholder"),
                        "the failure has to be an unresolvable variable, not a parse error, and was: "
                                + expected.getMessage());
            }
        }
    }

    @Test
    void noProfilelessCredentialAndNoLiteralInAnyProfile() {
        for (String resource : new String[] {"application.yml", "application-dev.yml",
                "application-prod.yml"}) {
            forEachEntry(load(resource), resource, (key, value) -> {
                if (value == null || !key.toLowerCase(Locale.ROOT).contains("password")) {
                    return;
                }
                assertTrue(String.valueOf(value).startsWith("${"),
                        resource + " sets " + key + " to a literal; a credential belongs in the"
                                + " environment or the git-ignored .env, never in a tracked file");
            });
        }
        // The development files may carry the documented local fallback; the prod file may not, which
        // the test above covers by refusing to resolve there at all.
        assertTrue(resolve(load("application-dev.yml"), PASSWORD, Map.of()).length() > 0);
        assertFalse(raw(load("application-prod.yml"), PASSWORD).contains(":"),
                "the prod password must not even have a default separator");
    }

    @Test
    void theLocalExampleDoesNotAssignAnEmptyDbPassword() throws IOException {
        for (String line : Files.readAllLines(Path.of(".env.example"))) {
            assertFalse(line.trim().matches("^DB_(USER|PASSWORD)\\s*=\\s*$"),
                    ".env.example must not carry a blank DB_USER or DB_PASSWORD: a properties import"
                            + " makes that an empty value rather than an unset one, which would defeat"
                            + " the fallback it sits above");
        }
    }

    @Test
    void theComposeStackStillTellsTheApplicationWhichRoleToUse() throws IOException {
        Map<String, Object> compose;
        try (InputStream in = Files.newInputStream(Path.of("compose.yaml"))) {
            compose = new Yaml().load(in);
        }
        Map<?, ?> services = section(compose, "services");
        Map<?, ?> postgres = serviceEnvironment(services, "postgres");
        assertTrue(hasEntryNaming(postgres, "POSTGRES_USER"),
                "the postgres service has to keep creating its own role, so the development fallback to"
                        + " the superuser does not spread into the demo stack");
        assertTrue(hasEntryNaming(postgres, "POSTGRES_PASSWORD"),
                "and keep naming its password, or the demo container starts with an empty one");
        for (String app : new String[] {"billgen-a", "billgen-b"}) {
            Map<?, ?> appEnv = serviceEnvironment(services, app);
            assertTrue(hasEntryNaming(appEnv, "SPRING_DATASOURCE_USERNAME"),
                    app + " must be told its role explicitly rather than inherit a local default");
            assertTrue(hasEntryNaming(appEnv, "SPRING_DATASOURCE_PASSWORD"),
                    app + " must be told its password explicitly rather than inherit a local default");
        }
    }

    // ------------------------------------------------------------------------------------------
    // Placeholder resolution, the way Spring does it: the first colon separates the variable name
    // from a default that may itself contain placeholders, and a name with no value anywhere makes
    // the expression unresolvable rather than empty.
    // ------------------------------------------------------------------------------------------

    private static String resolve(Map<String, Object> root, String dotted, Map<String, String> env) {
        return resolve(raw(root, dotted), env);
    }

    private static String resolve(String value, Map<String, String> env) {
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        while (cursor < value.length()) {
            int open = value.indexOf("${", cursor);
            if (open < 0) {
                return out.append(value, cursor, value.length()).toString();
            }
            out.append(value, cursor, open);
            int end = open + 2;
            int depth = 1;
            while (end < value.length()) {
                if (value.startsWith("${", end)) {
                    depth++;
                    end += 2;
                } else if (value.charAt(end) == '}') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                    end++;
                } else {
                    end++;
                }
            }
            if (depth != 0) {
                throw new IllegalStateException("Unbalanced placeholder in: " + value);
            }
            String inner = value.substring(open + 2, end);
            int colon = inner.indexOf(':');
            String name = colon < 0 ? inner : inner.substring(0, colon);
            String fromEnv = env.get(name);
            if (fromEnv != null) {
                out.append(fromEnv);
            } else if (colon >= 0) {
                out.append(resolve(inner.substring(colon + 1), env));
            } else {
                throw new IllegalStateException("Unresolved placeholder: " + name + " in " + value);
            }
            cursor = end + 1;
        }
        return out.toString();
    }

    private static String raw(Map<String, Object> root, String dotted) {
        Object node = root;
        for (String part : dotted.split("\\.")) {
            assertTrue(node instanceof Map, "no " + dotted + " section in this configuration");
            node = ((Map<?, ?>) node).get(part);
        }
        assertTrue(node != null, "no " + dotted + " setting in this configuration");
        return String.valueOf(node);
    }

    private static Map<?, ?> section(Map<?, ?> parent, String key) {
        Object node = parent.get(key);
        assertTrue(node instanceof Map, "no " + key + " section");
        return (Map<?, ?>) node;
    }

    private static Map<String, String> merge(Map<String, String> first, Map<String, String> second) {
        Map<String, String> merged = new java.util.LinkedHashMap<>(first);
        merged.putAll(second);
        return merged;
    }

    private static Map<?, ?> serviceEnvironment(Map<?, ?> services, String service) {
        return section(section(services, service), "environment");
    }

    /** Compose writes a service environment either as a mapping or as a list of NAME=value entries. */
    private static boolean hasEntryNaming(Map<?, ?> environment, String name) {
        if (environment.containsKey(name)) {
            return true;
        }
        for (Object value : environment.values()) {
            if (String.valueOf(value).startsWith(name + "=")) {
                return true;
            }
        }
        return false;
    }

    private static void forEachEntry(Map<?, ?> node, String resource, BiConsumer<String, Object> visit) {
        for (Map.Entry<?, ?> entry : node.entrySet()) {
            Object value = entry.getValue();
            visit.accept(String.valueOf(entry.getKey()), value);
            if (value instanceof Map) {
                forEachEntry((Map<?, ?>) value, resource, visit);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String resource) {
        try (InputStream in = DatabaseCredentialConfigurationTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertTrue(in != null, resource + " is not on the classpath");
            Object root = new Yaml().load(in);
            assertTrue(root instanceof Map, resource + " did not parse into a mapping");
            return (Map<String, Object>) root;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
