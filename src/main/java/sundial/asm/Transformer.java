package sundial.asm;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.Level;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cpw.mods.fml.relauncher.FMLRelaunchLog;
import sundial.Info;

public class Transformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        boolean obfuscated = !name.equals(transformedName);
        if (transformedName.equals("net.minecraft.client.multiplayer.WorldClient")) {
            return transformWorldClient(bytes, obfuscated);
        } else if (transformedName.equals("net.minecraft.world.WorldServer")) {
            return transformWorldServer(bytes, obfuscated);
        } else if (transformedName.equals("net.minecraft.server.MinecraftServer")) {
            return transformMinecraftServer(bytes);
        } else {
            return bytes;
        }
    }

    /**
     * Sundial: the server broadcasts the world clock to clients only once per
     * second (vanilla `tickCounter % 20 == 0`). With a slowed clock, modless
     * clients drift ahead between broadcasts and get snapped back every second
     * — the visible time skip. Patching the modulo gate to every tick reduces
     * the correction to at most 1 tick, which is invisible. The match is
     * NAME-INDEPENDENT: BIPUSH 20 right after an IREM, within a few
     * instructions of the "timeSync" profiler string (that string survives all
     * obfuscation, unlike field/method names).
     */
    private byte[] transformMinecraftServer(byte[] bytes) {
        ClassNode node = readClass(bytes);
        int patched = 0;
        for (MethodNode method : node.methods) {
            patched += patchTimeUpdateCadence(method);
        }
        if (patched > 0) {
            FMLRelaunchLog
                .log(Info.MODID, Level.INFO, "Patched time-update cadence to every tick (" + patched + " site(s))");
        } else {
            FMLRelaunchLog.log(Info.MODID, Level.WARN, "Time-update cadence site NOT found (clock sync stays 1/s)");
        }
        return saveClass(node);
    }

    private int patchTimeUpdateCadence(MethodNode method) {
        int count = 0;
        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode n = method.instructions.get(i);
            if (n.getOpcode() != Opcodes.BIPUSH || !(n instanceof IntInsnNode)) continue;
            if (((IntInsnNode) n).operand != 20) continue;
            AbstractInsnNode prev = n.getPrevious();
            AbstractInsnNode next = n.getNext();
            // bytecode order is: GETFIELD(tickCounter); BIPUSH 20; IREM; IFNE
            if (prev == null || prev.getOpcode() != Opcodes.GETFIELD) continue;
            if (next == null || next.getOpcode() != Opcodes.IREM) continue;
            boolean timeSync = false;
            AbstractInsnNode f = n;
            for (int j = 0; j < 14 && f != null; j++) {
                f = f.getNext();
                if (f instanceof LdcInsnNode) {
                    Object c = ((LdcInsnNode) f).cst;
                    if (c instanceof String && "timeSync".equals(c)) {
                        timeSync = true;
                        break;
                    }
                }
            }
            if (!timeSync) continue;
            method.instructions.set(n, new InsnNode(Opcodes.ICONST_1)); // % 1 == 0 -> send every tick
            count++;
        }
        return count;
    }

    private byte[] transformWorldClient(byte[] bytes, boolean obfuscated) {
        final String METHOD = obfuscated ? "b" : "tick";
        ClassNode node = readClass(bytes);
        boolean patched = false;
        for (MethodNode method : node.methods) {
            if (method.name.equals(METHOD) && method.desc.equals("()V")) {
                patched = patchWorldClientTick(method, obfuscated) || patched;
            }
        }
        if (!patched) {
            // Hand the class back untouched. A wrongly patched clock is worse than
            // vanilla: diluting the world-age counter makes the moon stand still.
            return bytes;
        }
        return saveClass(node);
    }

    private byte[] transformWorldServer(byte[] bytes, boolean obfuscated) {
        final String METHOD = obfuscated ? "b" : "tick";
        ClassNode node = readClass(bytes);
        boolean patched = false;
        for (MethodNode method : node.methods) {
            if (method.name.equals(METHOD) && method.desc.equals("()V")) {
                patched = patchWorldServerTick(method, obfuscated) || patched;
            }
        }
        if (!patched) {
            return bytes;
        }
        return saveClass(node);
    }

    private ClassNode readClass(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        return node;
    }

    private byte[] saveClass(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    /**
     * Vanilla {@code WorldClient.tick()} (obf {@code bjf.b()}) advances TWO clocks:
     * <ol>
     * <li>the day/moon clock — the doDaylightCycle-gated
     * {@code setWorldTime(getWorldTime() + 1)} — obf {@code b(J()+1)};</li>
     * <li>the world-age clock — {@code func_82738_a(getTotalWorldTime() + 1)} — obf
     * {@code a(I()+1)}.</li>
     * </ol>
     * The day/moon clock is the one {@code World.getMoonPhase()} and
     * {@code getCelestialAngle()} read ({@code worldTime/24000 % 8}), so THAT is the
     * increment Sundial must dilute — the world-age clock stays at +1/tick.
     * <p>
     * The site is found by SHAPE ({@link ClockMatcher}: long get, +1, long set) and
     * then narrowed to the one preceded by the {@code "doDaylightCycle"} string. No
     * method name is trusted, so an obfuscation/mapping change cannot silently point
     * this at another counter. If the shape or the guard is not found exactly once we
     * log an ERROR and leave the class vanilla.
     */
    private boolean patchWorldClientTick(MethodNode method, boolean obfuscated) {
        final String TICK_DESC = obfuscated ? "(Lahb;)J" : "(Lnet/minecraft/world/World;)J";
        List<AbstractInsnNode[]> windows = ClockMatcher.findLongIncrementWindows(method.instructions);
        List<AbstractInsnNode[]> guarded = new ArrayList<AbstractInsnNode[]>();
        for (AbstractInsnNode[] window : windows) {
            if (ClockMatcher.isDaylightGuarded(method.instructions, window[0])) {
                guarded.add(window);
            }
        }
        if (guarded.size() != 1) {
            FMLRelaunchLog.log(
                Info.MODID,
                Level.ERROR,
                String.format(
                    "Failed to patch WorldClient.tick(): expected exactly one doDaylightCycle-guarded long increment, found %d (of %d long increment(s)) — class left vanilla",
                    guarded.size(),
                    windows.size()));
            return false;
        }
        replaceIncrement(method.instructions, guarded.get(0), TICK_DESC);
        FMLRelaunchLog.log(Info.MODID, Level.INFO, "Successfully patched WorldClient.tick()");
        return true;
    }

    /**
     * Server side: {@code WorldServer.tick()} also has TWO long clocks — the
     * unconditional {@code setTotalWorldTime(getTotalWorldTime()+1)} and the
     * doDaylightCycle-gated {@code setWorldTime(getWorldTime()+1)} on
     * {@code WorldInfo}. Same structural rule and the same guard as the client, so
     * the day clock is the only candidate and the world-age clock is never touched.
     * No guard match (or several) means no patch at all.
     */
    private boolean patchWorldServerTick(MethodNode method, boolean obfuscated) {
        final String TICK_DESC = obfuscated ? "(Lahb;)J" : "(Lnet/minecraft/world/World;)J";
        List<AbstractInsnNode[]> windows = ClockMatcher.findLongIncrementWindows(method.instructions);
        List<AbstractInsnNode[]> guarded = new ArrayList<AbstractInsnNode[]>();
        for (AbstractInsnNode[] window : windows) {
            if (ClockMatcher.isDaylightGuarded(method.instructions, window[0])) {
                guarded.add(window);
            }
        }
        if (guarded.size() != 1) {
            FMLRelaunchLog.log(
                Info.MODID,
                Level.ERROR,
                String.format(
                    "Failed to patch WorldServer.tick(): expected exactly one doDaylightCycle-guarded long increment, found %d (of %d long increment(s)) — class left vanilla",
                    guarded.size(),
                    windows.size()));
            return false;
        }
        replaceIncrement(method.instructions, guarded.get(0), TICK_DESC);
        FMLRelaunchLog.log(Info.MODID, Level.INFO, "Successfully patched WorldServer.tick()");
        return true;
    }

    /** Swap {@code get();+1} for {@code WorldHandler.tick(world)} and keep the setter. */
    private void replaceIncrement(InsnList insns, AbstractInsnNode[] window, String tickDesc) {
        MethodInsnNode setter = (MethodInsnNode) window[3];
        InsnList replace = new InsnList();
        replace.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "sundial/WorldHandler", "tick", tickDesc, false));
        replace.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, setter.owner, setter.name, setter.desc, false));
        insns.insert(window[0], replace);
        for (int i = window.length - 1; i >= 0; i--) {
            insns.remove(window[i]);
        }
    }
}
