package sundial;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.Level;

import cpw.mods.fml.common.FMLLog;
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

    /** Log the first player-event failure only — must not spam. */
    private static boolean playerEventFailureLogged = false;

    private static void logPlayerEventFailure(String where, Throwable failure) {
        if (playerEventFailureLogged) return;
        playerEventFailureLogged = true;
        FMLLog.log(
            Info.MODID,
            Level.ERROR,
            String.format("Sundial: %s failed (%s) — skipping this player event", where, failure.toString()));
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            EntityPlayer player = event.player;
            if (player instanceof EntityPlayerMP) {
                NetworkHandler.getInstance()
                    .sync((EntityPlayerMP) player);
            }
        } catch (Throwable failure) {
            logPlayerEventFailure("onLogin", failure);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        try {
            EntityPlayer player = event.player;
            if (player instanceof EntityPlayerMP) {
                NetworkHandler.getInstance()
                    .sync((EntityPlayerMP) player);
            }
        } catch (Throwable failure) {
            logPlayerEventFailure("onChangeDimension", failure);
        }
    }

}
