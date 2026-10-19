package cheatchecker.update;

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
    /** Null on {@link State#NO_RELEASES} and {@link State#ERROR}. */
    public final ReleaseInfo latest;
    public final State state;
    /** Why the check failed, on {@link State#ERROR}. */
    public final String error;

    public ModStatus(TrackedMod mod, ModVersion installed, ReleaseInfo latest, State state,
        String error) {
        this.mod = mod;
        this.installed = installed;
        this.latest = latest;
        this.state = state;
        this.error = error;
    }
}
