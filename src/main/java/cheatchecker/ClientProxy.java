package cheatchecker;

import net.minecraftforge.client.ClientCommandHandler;

import cheatchecker.client.UpdateCommand;
import cheatchecker.client.UpdateNotifier;
import cheatchecker.update.UpdateChecker;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        FMLCommonHandler.instance()
            .bus()
            .register(new UpdateNotifier());
        ClientCommandHandler.instance.registerCommand(new UpdateCommand());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        if (Config.enableUpdateCheck) {
            // In the background while the game finishes loading; the report shows on entering a world.
            UpdateChecker.startAsync(null);
        }
    }
}
