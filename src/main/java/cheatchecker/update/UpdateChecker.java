package cheatchecker.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
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

    /** A check asked for sooner than this after the last one reuses its results (GitHub allows 60 requests/hour). */
    private static final long REUSE_MS = 60_000;

    private static volatile List<ModStatus> results;
    private static volatile boolean running;
    private static volatile long finishedAt;
    /** Waiting for the running check; guarded by the class lock. */
    private static final List<Callback> CALLBACKS = new ArrayList<>();

    private UpdateChecker() {}

    /** The last finished check, or null if none has finished yet. */
    public static List<ModStatus> getResults() {
        return results;
    }

    public static boolean isRunning() {
        return running;
    }

    /**
     * Starts a check, or joins the one already running. {@code onDone} (may be null) runs on the checker thread with
     * the results; a check finished less than a minute ago is not repeated, its results are passed at once.
     */
    public static void startAsync(Callback onDone) {
        List<ModStatus> recent = results;
        if (recent != null && System.currentTimeMillis() - finishedAt < REUSE_MS) {
            if (onDone != null) {
                onDone.onDone(recent);
            }
            return;
        }
        synchronized (UpdateChecker.class) {
            if (onDone != null) {
                CALLBACKS.add(onDone);
            }
            if (running) {
                return;
            }
            running = true;
        }
        final Map<String, ModContainer> loaded = Loader.instance()
            .getIndexedModList();
        Thread thread = new Thread(() -> {
            try {
                List<ModStatus> list = new ArrayList<>();
                String offline = null;
                for (TrackedMod mod : TrackedMod.values()) {
                    ModStatus status = check(mod, loaded.get(mod.modId), offline);
                    if (status.state == ModStatus.State.ERROR && status.offline) {
                        // No connection or out of requests: asking about the other mods would fail the same way.
                        offline = status.error;
                    }
                    list.add(status);
                }
                results = Collections.unmodifiableList(list);
                finishedAt = System.currentTimeMillis();
                log(list);
            } catch (Throwable t) {
                CheatChecker.LOG.error("Update check failed", t);
            }
            List<Callback> waiting;
            synchronized (UpdateChecker.class) {
                running = false;
                waiting = new ArrayList<>(CALLBACKS);
                CALLBACKS.clear();
            }
            List<ModStatus> done = results;
            if (done != null) {
                for (Callback callback : waiting) {
                    callback.onDone(done);
                }
            }
        }, "CheatChecker update check");
        thread.setDaemon(true);
        thread.start();
    }

    private static ModStatus check(TrackedMod mod, ModContainer container, String offline) {
        ModVersion installed = null;
        if (container != null) {
            installed = ModVersion
                .parse(mod == TrackedMod.CHEAT_CHECKER ? Tags.VERSION : container.getVersion());
        }
        if (offline != null) {
            return new ModStatus(mod, installed, null, ModStatus.State.ERROR, offline, true);
        }
        ReleaseInfo latest;
        try {
            latest = fetchLatest(mod);
        } catch (Exception e) {
            CheatChecker.LOG.warn("Could not check {} for updates: {}", mod.displayName, e.toString());
            boolean noConnection = e instanceof UnknownHostException || e instanceof ConnectException
                || e instanceof SocketTimeoutException
                || e instanceof RateLimitException;
            // Our own messages read fine alone; for anything else the exception type says what went wrong.
            String error = e.getClass() == IOException.class || e instanceof RateLimitException ? e.getMessage()
                : e.toString();
            return new ModStatus(mod, installed, null, ModStatus.State.ERROR, error, noConnection);
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
        return new ModStatus(mod, installed, latest, state, null, false);
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
            String date = str(release, "published_at");
            best = new ReleaseInfo(
                version,
                str(release, "html_url"),
                date != null && date.length() >= 10 ? date.substring(0, 10) : null);
        }
        if (best != null) {
            return best;
        }
        JsonArray tags = getJson(API + TrackedMod.OWNER + "/" + mod.repo + "/tags?per_page=100").getAsJsonArray();
        for (JsonElement element : tags) {
            ModVersion version = ModVersion.parse(str(element.getAsJsonObject(), "name"));
            if (version.isParsed() && (best == null || version.compareTo(best.version) > 0)) {
                best = new ReleaseInfo(version, mod.releasesUrl(), null);
            }
        }
        return best;
    }

    private static JsonElement getJson(String url) throws IOException {
        HttpURLConnection connection = open(url);
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            int code = connection.getResponseCode();
            if (code == 403 || code == 429) {
                throw new RateLimitException("GitHub rate limit, try later (HTTP " + code + ")");
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
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
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

    private static final class RateLimitException extends IOException {

        RateLimitException(String message) {
            super(message);
        }
    }

    public interface Callback {

        void onDone(List<ModStatus> results);
    }
}
