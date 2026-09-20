package sundial;

import net.minecraft.world.World;

import org.apache.logging.log4j.Level;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.FMLLog;

public class ClientProxy extends Proxy {

    @Override
    void sync(SyncMessage message) {
        World world = FMLClientHandler.instance()
            .getClient().theWorld;
        if (world == null) {
            // Login/dimension-change race: the client world is not ready yet — the
            // next sync packet (or PlayerLoggedIn sync) arrives after the world
            // exists, so dropping this one is safe.
            FMLLog.log(Info.MODID, Level.DEBUG, "Sundial: sync dropped (client world not ready)");
            return;
        }
        int dimension = world.provider.dimensionId;
        if (dimension == message.dimension) {
            WorldHandler.setScale(world, message.scale);
        } else {
            FMLLog.log(
                Info.MODID,
                Level.ERROR,
                String.format("Dimension mismatch during sync (client: %d, server: %d)", dimension, message.dimension));
        }
    }

}
