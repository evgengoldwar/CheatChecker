package cheatchecker.mixins;

import net.minecraft.launchwrapper.Launch;

import cheatchecker.update.PendingDeletions;
import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;

@IFMLLoadingPlugin.MCVersion("1.7.10")
public class EarlyMixinsLoader implements IFMLLoadingPlugin, IEarlyMixinLoader {

    public EarlyMixinsLoader() {
        // Before FML looks for mods: old jars left behind by an in-game update must be gone, or the mod loads twice.
        if (Launch.minecraftHome != null) {
            PendingDeletions.processOnStartup(Launch.minecraftHome);
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public String getMixinConfig() {
        return "mixins.cheatchecker.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return IMixins.getEarlyMixins(Mixins.class, loadedCoreMods);
    }
}
