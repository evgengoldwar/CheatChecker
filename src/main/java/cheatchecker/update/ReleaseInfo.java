package cheatchecker.update;

/** The newest published version of a mod on GitHub. */
public final class ReleaseInfo {

    public final ModVersion version;
    /** The release page, or the repository's releases page when the version only exists as a tag. */
    public final String pageUrl;
    /** ISO date ({@code 2026-10-19}) or null. */
    public final String publishedAt;

    public ReleaseInfo(ModVersion version, String pageUrl, String publishedAt) {
        this.version = version;
        this.pageUrl = pageUrl;
        this.publishedAt = publishedAt;
    }
}
