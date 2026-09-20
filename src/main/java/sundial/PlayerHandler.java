package sundial;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

public class PlayerHandler {

    static private PlayerHandler instance;

    static public PlayerHandler getInstance() {
        if (instance == null) {
            instance = new PlayerHandler();
        }
        return instance;
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        EntityPlayer player = event.player;
        if (player instanceof EntityPlayerMP) {
            NetworkHandler.getInstance()
                .sync((EntityPlayerMP) player);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        EntityPlayer player = event.player;
        if (player instanceof EntityPlayerMP) {
            NetworkHandler.getInstance()
                .sync((EntityPlayerMP) player);
        }
    }

}
