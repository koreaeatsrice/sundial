package sundial;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.logging.log4j.Level;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class WorldHandler {

    static private WorldHandler instance = null;

    static private final Map<World, IWorldData> worlds = new HashMap<World, IWorldData>();

    /** Global default scale — /timescale set applies to ALL dimensions; new worlds inherit it. */
    static private volatile double globalScale = 1.0;

    /** One-line warning when tick() itself throws (must never crash the server tick). */
    static private boolean tickFailureLogged = false;

    static public WorldHandler getInstance() {
        if (instance == null) {
            instance = new WorldHandler();
        }
        return instance;
    }

    static public double getScale(World world) {
        IWorldData wd = worlds.get(world);
        return wd == null ? 1.0 : wd.getScale(); // null-world sender (console) -> vanilla scale
    }

    /**
     * Apply a scale to EVERY loaded dimension and remember it as the global
     * default so worlds that load later inherit it. One log line per command —
     * no per-tick spam.
     */
    static public void setScale(World world, double scale) {
        globalScale = scale;
        synchronized (worlds) {
            for (IWorldData wd : worlds.values()) {
                wd.setScale(scale);
                wd.setTime(0.0); // restart the fractional accumulator on any scale change
            }
        }
        FMLLog.log(Info.MODID, Level.INFO, String.format("Set time scale to %s (all dimensions)", scale));
    }

    /** Push the current scale to every loaded dimension's clients. */
    static public void syncAll() {
        synchronized (worlds) {
            for (World w : worlds.keySet()) {
                NetworkHandler.getInstance()
                    .sync(w);
            }
        }
    }

    /**
     * Replaces the vanilla "+1 tick per tick" daylight-cycle increment (our ASM
     * transformer swaps it for this call, on both server and client worlds).
     *
     * Smooth /time-add semantics: a per-dimension fractional accumulator; only
     * WHOLE ticks are ever applied, and the world clock NEVER moves backwards.
     * scale == 1.0 behaves exactly like vanilla (+1 per tick); scale == 0.0
     * freezes the clock; scale 0.3333 = +1 tick every 3rd tick (3x longer days).
     * External time changes (sleeping, commands, other mods) are simply carried
     * forward — the accumulator is never reset against the world clock, which
     * removes the old "3 forward, 2 back" stutter entirely.
     */
    @SuppressWarnings("unused")
    static public long tick(World world) {
        try {
            IWorldData wd = worlds.get(world);
            if (wd == null) return world.getWorldTime();

            double scale = wd.getScale();
            if (Double.isNaN(scale) || Double.isInfinite(scale)) {
                wd.setScale(1.0); // poison guard: any poisoned runtime value falls back to vanilla
                scale = 1.0;
            }
            if (scale == 1.0) {
                wd.setTime(0.0);
                return world.getWorldTime() + 1; // vanilla behavior
            }

            double acc = wd.getTime() + scale;
            long whole = (long) Math.floor(acc);
            if (whole < 0) whole = 0; // clamp: never decrease, whatever the scale math does
            if (whole > 1_000_000L) whole = 1_000_000L; // cap: double->long saturation can never wrap the clock
            wd.setTime(acc - whole);

            if (whole > 0) {
                return world.getWorldTime() + whole;
            }
            return world.getWorldTime();
        } catch (Throwable t) {
            // This call sits INSIDE the vanilla server tick (ASM-inserted): an
            // uncaught exception here would crash the tick. Fail safe: stall the
            // clock this tick, log once.
            if (!tickFailureLogged) {
                tickFailureLogged = true;
                FMLLog.log(
                    Info.MODID,
                    Level.ERROR,
                    String.format("Sundial: tick() failed (%s) — clock stalled for this tick", t.toString()));
            }
            try {
                return world.getWorldTime();
            } catch (Throwable t2) {
                return 0L;
            }
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onLoad(WorldEvent.Load event) {
        World world = event.world;
        IWorldData wd = world.isRemote ? WorldDataClient.get(world) : WorldDataServer.get(world);
        worlds.put(world, wd);
        // Adopt a saved non-vanilla scale as the global default (Overworld loads first).
        if (globalScale == 1.0 && wd.getScale() != 1.0) {
            globalScale = wd.getScale();
        }
        // New worlds inherit the global scale — time dilation everywhere.
        if (globalScale != 1.0 && wd.getScale() != globalScale) {
            wd.setScale(globalScale);
            wd.setTime(0.0);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onUnload(WorldEvent.Unload event) {
        worlds.remove(event.world);
    }

}
