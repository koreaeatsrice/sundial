package sundial;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

class TimescaleCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "timescale";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "commands.timescale.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.addChatMessage(
                new ChatComponentTranslation("commands.timescale.get", WorldHandler.getScale(sender.getEntityWorld())));
        } else if (args.length == 2 && args[0].equals("set")) {
            double scale;
            try {
                scale = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                scale = -1;
            }
            if (scale != scale || Double.isInfinite(scale) || scale < 0) { // NaN / Infinity / negative
                scale = -1;
            }
            if (scale >= 0) {
                World world = sender.getEntityWorld();
                if (world == null) { // console sender without a world
                    ChatComponentTranslation component = new ChatComponentTranslation(getCommandUsage(sender));
                    component.getChatStyle()
                        .setColor(EnumChatFormatting.RED);
                    sender.addChatMessage(component);
                    return;
                }
                WorldHandler.setScale(world, scale);
                NetworkHandler.getInstance()
                    .sync(world);
                sender.addChatMessage(
                    new ChatComponentTranslation(
                        "commands.timescale.set",
                        WorldHandler.getScale(sender.getEntityWorld())));
            } else {
                ChatComponentTranslation component = new ChatComponentTranslation("commands.timescale.invalid");
                component.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(component);
            }
        } else {
            ChatComponentTranslation component = new ChatComponentTranslation(getCommandUsage(sender));
            component.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(component);
        }
    }

}
