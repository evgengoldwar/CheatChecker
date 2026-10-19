package cheatchecker.update;

/**
 * The mods whose updates are checked: each with its mod id (as {@code @Mod(modid)} declares it) and its GitHub
 * repository under {@link #OWNER}.
 */
public enum TrackedMod {

    WAYFARERS_MAP("wayfarmap", "Wayfarer's Map", "Wayfarer-s-Map"),
    LAG_FINDER("lagfinder", "LagFinder", "LagFinder"),
    INFO_HUD("infohud", "InfoHUD", "InfoHUD"),
    NEI_EXTEND("neiextend", "NEIExtend", "NEIExtend"),
    CRAFT_INSPECTOR("craftinspector", "CraftInspector", "CraftInspector"),
    MULTIBLOCK_VISUALIZER("multiblockvisualizer", "MultiblockVisualizer", "MultiblockVisualizer"),
    CHEAT_CHECKER("cheatchecker", "CheatChecker", "CheatChecker");

    public static final String OWNER = "evgengoldwar";

    public final String modId;
    public final String displayName;
    public final String repo;

    TrackedMod(String modId, String displayName, String repo) {
        this.modId = modId;
        this.displayName = displayName;
        this.repo = repo;
    }

    public String repoUrl() {
        return "https://github.com/" + OWNER + "/" + repo;
    }

    public String releasesUrl() {
        return repoUrl() + "/releases";
    }

    public static TrackedMod byModId(String modId) {
        for (TrackedMod mod : values()) {
            if (mod.modId.equalsIgnoreCase(modId)) {
                return mod;
            }
        }
        return null;
    }
}
