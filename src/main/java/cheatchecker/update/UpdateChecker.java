package cheatchecker.update;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cheatchecker.CheatChecker;
import cheatchecker.Tags;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/**
 * Asks the GitHub API for the newest release of every {@link TrackedMod} on a background thread and compares it with
 * the installed version.
 */
public final class UpdateChecker {

    private static final String API = "https://api.github.com/repos/";
    private static final int TIMEOUT_MS = 10_000;

    private static volatile List<ModStatus> results;
    private static volatile boolean running;

    private UpdateChecker() {}

    /** The last finished check, or null if none has finished yet. */
    public static List<ModStatus> getResults() {
        return results;
    }

    public static boolean isRunning() {
        return running;
    }

    public static ModStatus find(TrackedMod mod) {
        List<ModStatus> list = results;
        if (list != null) {
            for (ModStatus status : list) {
                if (status.mod == mod) {
                    return status;
                }
            }
        }
        return null;
    }

    /**
     * Starts a check unless one is running. {@code onDone} runs on the checker thread with the results.
     *
     * @return false if a check was already running
     */
    public static synchronized boolean startAsync(final Callback onDone) {
        if (running) {
            return false;
        }
        running = true;
        final Map<String, ModContainer> loaded = Loader.instance()
            .getIndexedModList();
        Thread thread = new Thread(() -> {
            List<ModStatus> list = new ArrayList<>();
            try {
                for (TrackedMod mod : TrackedMod.values()) {
                    list.add(check(mod, loaded.get(mod.modId)));
                }
                results = Collections.unmodifiableList(list);
                log(list);
            } catch (Throwable t) {
                CheatChecker.LOG.error("Update check failed", t);
            } finally {
                running = false;
            }
            if (onDone != null && results != null) {
                onDone.onDone(results);
            }
        }, "CheatChecker update check");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private static ModStatus check(TrackedMod mod, ModContainer container) {
        ModVersion installed = null;
        File source = null;
        if (container != null) {
            installed = ModVersion
                .parse(mod == TrackedMod.CHEAT_CHECKER ? Tags.VERSION : container.getVersion());
            source = container.getSource();
        }
        ReleaseInfo latest;
        try {
            latest = fetchLatest(mod);
        } catch (Exception e) {
            CheatChecker.LOG.warn("Could not check {} for updates: {}", mod.displayName, e.toString());
            return new ModStatus(mod, installed, source, null, ModStatus.State.ERROR, e.getMessage());
        }
        ModStatus.State state;
        if (latest == null) {
            state = ModStatus.State.NO_RELEASES;
        } else if (installed == null) {
            state = ModStatus.State.MISSING;
        } else {
            int cmp = installed.compareTo(latest.version);
            state = cmp == 0 ? ModStatus.State.UP_TO_DATE
                : cmp < 0 ? ModStatus.State.OUTDATED : ModStatus.State.AHEAD;
        }
        return new ModStatus(mod, installed, source, latest, state, null);
    }

    /** The highest-versioned published release, else the highest tag, else null. */
    private static ReleaseInfo fetchLatest(TrackedMod mod) throws IOException {
        ReleaseInfo best = null;
        JsonArray releases = getJson(API + TrackedMod.OWNER + "/" + mod.repo + "/releases?per_page=30")
            .getAsJsonArray();
        for (JsonElement element : releases) {
            JsonObject release = element.getAsJsonObject();
            if (bool(release, "draft")) {
                continue;
            }
            ModVersion version = ModVersion.parse(str(release, "tag_name"));
            if (!version.isParsed() || (best != null && version.compareTo(best.version) <= 0)) {
                continue;
            }
            JsonObject jar = pickJar(release.getAsJsonArray("assets"));
            String date = str(release, "published_at");
            best = new ReleaseInfo(
                version,
                str(release, "html_url"),
                jar == null ? null : str(jar, "name"),
                jar == null ? null : str(jar, "browser_download_url"),
                jar == null ? 0 : jar.get("size")
                    .getAsLong(),
                date != null && date.length() >= 10 ? date.substring(0, 10) : null);
        }
        if (best != null) {
            return best;
        }
        JsonArray tags = getJson(API + TrackedMod.OWNER + "/" + mod.repo + "/tags?per_page=100").getAsJsonArray();
        for (JsonElement element : tags) {
            ModVersion version = ModVersion.parse(str(element.getAsJsonObject(), "name"));
            if (version.isParsed() && (best == null || version.compareTo(best.version) > 0)) {
                best = new ReleaseInfo(version, mod.releasesUrl(), null, null, 0, null);
            }
        }
        return best;
    }

    /** The mod's own jar among a release's assets: not the dev, sources, api or javadoc one. */
    private static JsonObject pickJar(JsonArray assets) {
        JsonObject best = null;
        if (assets == null) {
            return null;
        }
        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            String name = str(asset, "name");
            if (name == null || !name.endsWith(".jar")) {
                continue;
            }
            String lower = name.toLowerCase();
            if (lower.endsWith("-dev.jar") || lower.endsWith("-sources.jar")
                || lower.endsWith("-api.jar")
                || lower.endsWith("-javadoc.jar")
                || lower.endsWith("-dev-preshadow.jar")) {
                continue;
            }
            if (best == null || name.length() < str(best, "name").length()) {
                best = asset;
            }
        }
        return best;
    }

    private static JsonElement getJson(String url) throws IOException {
        HttpURLConnection connection = open(url);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        int code = connection.getResponseCode();
        if (code == 403 || code == 429) {
            throw new IOException("GitHub rate limit, try later (HTTP " + code + ")");
        }
        if (code == 404) {
            throw new IOException("repository not found (HTTP 404)");
        }
        if (code != 200) {
            throw new IOException("HTTP " + code);
        }
        try (InputStream in = connection.getInputStream();
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader);
        } finally {
            connection.disconnect();
        }
    }

    static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "CheatChecker/" + Tags.VERSION);
        return connection;
    }

    private static String str(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private static void log(List<ModStatus> list) {
        CheatChecker.LOG.info("Mod update check:");
        for (ModStatus status : list) {
            CheatChecker.LOG.info(
                "  {} : installed {}, latest {} -> {}",
                status.mod.displayName,
                status.installed == null ? "-" : status.installed,
                status.latest == null ? "-" : status.latest.version,
                status.state);
        }
    }

    public interface Callback {

        void onDone(List<ModStatus> results);
    }
}
