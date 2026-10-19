package cheatchecker.update;

/** The newest published version of a mod on GitHub, with its jar if the release has one. */
public final class ReleaseInfo {

    public final ModVersion version;
    /** The release page, or the repository's releases page when the version only exists as a tag. */
    public final String pageUrl;
    /** Null when the release has no jar to download. */
    public final String assetName;
    public final String assetUrl;
    public final long assetSize;
    /** ISO date ({@code 2026-10-19}) or null. */
    public final String publishedAt;

    public ReleaseInfo(ModVersion version, String pageUrl, String assetName, String assetUrl, long assetSize,
        String publishedAt) {
        this.version = version;
        this.pageUrl = pageUrl;
        this.assetName = assetName;
        this.assetUrl = assetUrl;
        this.assetSize = assetSize;
        this.publishedAt = publishedAt;
    }

    public boolean hasJar() {
        return assetUrl != null;
    }
}
