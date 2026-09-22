package sundial;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = Info.MODID, name = "Sundial", version = Info.VERSION, acceptableRemoteVersions = "*")
public class Sundial {

    @SidedProxy(clientSide = "sundial.ClientProxy", serverSide = "sundial.Proxy")
    static public Proxy proxy;

    @Mod.EventHandler
    @SuppressWarnings("unused")
    public void onPreInit(FMLPreInitializationEvent event) {
        NetworkHandler.getInstance()
            .register();
    }

    @Mod.EventHandler
    @SuppressWarnings("unused")
    public void onInit(FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(PlayerHandler.getInstance());
        MinecraftForge.EVENT_BUS.register(WorldHandler.getInstance());
    }

    @Mod.EventHandler
    @SuppressWarnings("unused")
    public void onServerStart(FMLServerStartingEvent event) {
        event.registerServerCommand(new TimescaleCommand());
    }

    @Mod.EventHandler
    @SuppressWarnings("unused")
    public void onServerStarted(FMLServerStartedEvent event) {
        // Every boot-loaded dimension is known now; make the Overworld's saved
        // scale the global value and enforce it everywhere (load order safe).
        WorldHandler.getInstance()
            .enforceGlobal();
    }

}
