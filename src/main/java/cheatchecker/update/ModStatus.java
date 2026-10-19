package cheatchecker.update;

import java.io.File;

/** What the update check found for one {@link TrackedMod}. */
public final class ModStatus {

    public enum State {
        /** Installed and the same as the newest release. */
        UP_TO_DATE,
        /** Installed, a newer release exists. */
        OUTDATED,
        /** Not installed. */
        MISSING,
        /** Installed and newer than any release (a dev build). */
        AHEAD,
        /** GitHub has no release or tag to compare with. */
        NO_RELEASES,
        /** GitHub could not be asked. */
        ERROR
    }

    public final TrackedMod mod;
    /** Null when the mod is not installed. */
    public final ModVersion installed;
    /** The jar (or folder, in a dev environment) the installed mod was loaded from; may be null. */
    public final File source;
    /** Null on {@link State#NO_RELEASES} and {@link State#ERROR}. */
    public final ReleaseInfo latest;
    public final State state;
    /** Why the check failed, on {@link State#ERROR}. */
    public final String error;

    /** Set once the newest jar has been downloaded this session; it is used after a restart. */
    public volatile boolean downloaded;

    public ModStatus(TrackedMod mod, ModVersion installed, File source, ReleaseInfo latest, State state,
        String error) {
        this.mod = mod;
        this.installed = installed;
        this.source = source;
        this.latest = latest;
        this.state = state;
        this.error = error;
    }

    /** Whether a click can download the newest jar for this mod. */
    public boolean canDownload() {
        return (state == State.OUTDATED || state == State.MISSING) && latest != null && latest.hasJar() && !downloaded;
    }
}
