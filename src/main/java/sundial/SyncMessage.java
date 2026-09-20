package sundial;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class SyncMessage implements IMessage {

    int dimension;

    double scale;

    @SuppressWarnings("unused")
    public SyncMessage() {}

    SyncMessage(int d, double s) {
        dimension = d;
        scale = s;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        dimension = buffer.readInt();
        scale = buffer.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(dimension);
        buffer.writeDouble(scale);
    }

}
