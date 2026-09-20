package sundial;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

public class WorldDataServer extends WorldSavedData implements IWorldData {

    private double scale = 1;

    private double time = 0;

    static public WorldDataServer get(World world) {
        WorldSavedData wsd = world.perWorldStorage.loadData(WorldDataServer.class, Info.MODID);
        WorldDataServer wd;
        boolean create = wsd == null || !(wsd instanceof WorldDataServer);
        if (create) {
            wd = new WorldDataServer();
            world.perWorldStorage.setData(Info.MODID, wd);
        } else {
            wd = (WorldDataServer) wsd;
        }
        wd.time = 0.0; // fresh fractional accumulator on load (world clock is position source)
        if (create) {
            wd.setDirty(true);
        }
        return wd;
    }

    private WorldDataServer() {
        super(Info.MODID);
    }

    @SuppressWarnings("unused")
    public WorldDataServer(String id) {
        super(id);
    }

    private static final double MAX_SCALE = 1_000_000.0; // far above real use; keeps the clock far from long overflow

    public double getScale() {
        return scale;
    }

    public void setScale(double s) {
        if (s != s) { // NaN -> vanilla
            s = 1.0;
        }
        if (s < 0) {
            s = 0;
        }
        if (s > MAX_SCALE) {
            s = MAX_SCALE; // unbounded scales would saturate (long)floor and wrap the clock negative
        }
        if (s != scale) {
            scale = s;
            this.setDirty(true);
        }
    }

    public double getTime() {
        return time;
    }

    public void setTime(double t) {
        time = t;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        if (compound.hasKey("scale")) {
            this.setScale(compound.getDouble("scale"));
        } // else: legacy/corrupt data — keep default 1.0 (vanilla) instead of freezing the clock
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        compound.setDouble("scale", this.getScale());
    }

}
