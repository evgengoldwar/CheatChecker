package cheatchecker;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    private static final String CATEGORY_UPDATES = "updates";

    public static boolean enableUpdateCheck = true;
    public static boolean showOnEveryWorldJoin = true;
    public static boolean showUpToDate = true;
    public static boolean onlyShowWhenSomethingToDo = false;
    public static boolean allowInGameInstall = true;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        enableUpdateCheck = configuration.getBoolean(
            "enableUpdateCheck",
            CATEGORY_UPDATES,
            enableUpdateCheck,
            "Check GitHub for new versions of evgengoldwar's mods when the game starts.");
        showOnEveryWorldJoin = configuration.getBoolean(
            "showOnEveryWorldJoin",
            CATEGORY_UPDATES,
            showOnEveryWorldJoin,
            "Show the update report every time you enter a world, not only the first time per game session.");
        showUpToDate = configuration
            .getBoolean("showUpToDate", CATEGORY_UPDATES, showUpToDate, "List mods that are already up to date.");
        onlyShowWhenSomethingToDo = configuration.getBoolean(
            "onlyShowWhenSomethingToDo",
            CATEGORY_UPDATES,
            onlyShowWhenSomethingToDo,
            "Only show the report when a mod can be updated or installed.");
        allowInGameInstall = configuration.getBoolean(
            "allowInGameInstall",
            CATEGORY_UPDATES,
            allowInGameInstall,
            "Allow the report's buttons to download jars into the mods folder (otherwise they open the release page).");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
