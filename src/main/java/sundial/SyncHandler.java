package sundial;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class SyncHandler implements IMessageHandler<SyncMessage, IMessage> {

    @Override
    public IMessage onMessage(SyncMessage message, MessageContext context) {
        Sundial.proxy.sync(message);
        return null;
    }

}
