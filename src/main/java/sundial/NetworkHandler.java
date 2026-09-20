package sundial;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public class NetworkHandler {

    static private NetworkHandler instance = null;

    private SimpleNetworkWrapper channel;

    static public NetworkHandler getInstance() {
        if (instance == null) {
            instance = new NetworkHandler();
        }
        return instance;
    }

    public void register() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(Info.MODID);
        channel.registerMessage(SyncHandler.class, SyncMessage.class, 1, Side.CLIENT);
    }

    public void sync(World world) {
        int dimension = world.provider.dimensionId;
        channel.sendToDimension(new SyncMessage(dimension, WorldHandler.getScale(world)), dimension);
    }

    public void sync(EntityPlayerMP player) {
        World world = player.worldObj;
        int dimension = world.provider.dimensionId;
        channel.sendTo(new SyncMessage(dimension, WorldHandler.getScale(world)), player);
    }

}
