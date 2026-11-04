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
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

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
        for (MethodNode method : node.methods) {
            if (method.name.equals(METHOD) && method.desc.equals("()V")) {
                patchWorldClientTick(method, obfuscated);
            }
        }
        return saveClass(node);
    }

    private byte[] transformWorldServer(byte[] bytes, boolean obfuscated) {
        final String METHOD = obfuscated ? "b" : "tick";
        ClassNode node = readClass(bytes);
        for (MethodNode method : node.methods) {
            if (method.name.equals(METHOD) && method.desc.equals("()V")) {
                patchWorldServerTick(method, obfuscated);
            }
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
     * {@code setWorldTime(getWorldTime() + 1)} — obf {@code b(J()+1)} at offsets
     * 26-33 of the real client jar;</li>
     * <li>the world-age clock — {@code func_82738_a(getTotalWorldTime() + 1)} — obf
     * {@code a(I()+1)} at offsets 4-11.</li>
     * </ol>
     * In the OBFUSCATED namespace {@code getWorldTime} is {@code J} and
     * {@code setWorldTime} is {@code b}; {@code I}/{@code a} are
     * {@code getTotalWorldTime}/{@code func_82738_a}. The day/moon clock is the one
     * {@code World.getMoonPhase()} and {@code getCelestialAngle()} read
     * ({@code worldTime/24000 % 8}), so THAT is the increment Sundial must dilute —
     * the world-age clock stays at +1/tick.
     * <p>
     * An earlier build shipped {@code I}/{@code a} here (labelled getWorldTime/
     * setWorldTime) and then "removed" the {@code J}/{@code b} increment: in
     * production that diluted the world-age clock and DELETED the day/moon clock, so
     * the client clock — and the moon phase derived from it — no longer advanced
     * with the world. The names are now the day-clock ones.
     */
    private void patchWorldClientTick(MethodNode method, boolean obfuscated) {
        final String WORLD_CLIENT = obfuscated ? "bjf" : "net/minecraft/client/multiplayer/WorldClient";
        final String GET_WORLD_TIME = obfuscated ? "J" : "getWorldTime";
        final String SET_WORLD_TIME = obfuscated ? "b" : "setWorldTime";
        final String TICK_DESC = obfuscated ? "(Lahb;)J" : "(Lnet/minecraft/world/World;)J";
        InsnList search = new InsnList();
        search.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD_CLIENT, GET_WORLD_TIME, "()J", false));
        search.add(new InsnNode(Opcodes.LCONST_1));
        search.add(new InsnNode(Opcodes.LADD));
        search.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD_CLIENT, SET_WORLD_TIME, "(J)V", false));
        InsnList replace = new InsnList();
        replace.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "sundial/WorldHandler", "tick", TICK_DESC, false));
        replace.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD_CLIENT, SET_WORLD_TIME, "(J)V", false));
        if (patchInstructions(method.instructions, search, replace)) {
            FMLRelaunchLog.log(Info.MODID, Level.INFO, "Successfully patched WorldClient.tick()");
        } else {
            FMLRelaunchLog.log(Info.MODID, Level.ERROR, "Failed to patch WorldClient.tick()");
        }
    }

    private void patchWorldServerTick(MethodNode method, boolean obfuscated) {
        final String WORLD_SERVER = obfuscated ? "mt" : "net/minecraft/world/WorldServer";
        final String WORLD_INFO_FIELD = obfuscated ? "x" : "worldInfo";
        final String WORLD_INFO_DESC = obfuscated ? "Lays;" : "Lnet/minecraft/world/storage/WorldInfo;";
        final String WORLD_INFO = obfuscated ? "ays" : "net/minecraft/world/storage/WorldInfo";
        final String GET_WORLD_TIME = obfuscated ? "g" : "getWorldTime";
        final String SET_WORLD_TIME = obfuscated ? "c" : "setWorldTime";
        final String TICK_DESC = obfuscated ? "(Lahb;)J" : "(Lnet/minecraft/world/World;)J";
        InsnList search = new InsnList();
        search.add(new FieldInsnNode(Opcodes.GETFIELD, WORLD_SERVER, WORLD_INFO_FIELD, WORLD_INFO_DESC));
        search.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD_INFO, GET_WORLD_TIME, "()J", false));
        search.add(new InsnNode(Opcodes.LCONST_1));
        search.add(new InsnNode(Opcodes.LADD));
        search.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD_INFO, SET_WORLD_TIME, "(J)V", false));
        InsnList replace = new InsnList();
        replace.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "sundial/WorldHandler", "tick", TICK_DESC, false));
        replace.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD_INFO, SET_WORLD_TIME, "(J)V", false));
        if (patchInstructions(method.instructions, search, replace)) {
            FMLRelaunchLog.log(Info.MODID, Level.INFO, "Successfully patched WorldServer.tick()");
        } else {
            FMLRelaunchLog.log(Info.MODID, Level.ERROR, "Failed to patch WorldServer.tick()");
        }
    }

    private boolean patchInstructions(InsnList subject, InsnList search, InsnList replace) {
        List<AbstractInsnNode> found = new ArrayList<AbstractInsnNode>();
        int subjectSize = subject.size();
        int searchSize = search.size();
        for (int i = 0; i < subjectSize; i++) {
            found.clear();
            for (int j = 0; j < searchSize && i + j < subjectSize; j++) {
                AbstractInsnNode a1 = subject.get(i + j);
                AbstractInsnNode a2 = search.get(j);
                if (a1.getOpcode() != a2.getOpcode()) {
                    break;
                }
                switch (a2.getOpcode()) {
                    case Opcodes.DUP:
                    case Opcodes.LADD:
                    case Opcodes.LCONST_1:
                        found.add(a1);
                        continue;
                    case Opcodes.ALOAD:
                        if (((VarInsnNode) a1).var == ((VarInsnNode) a2).var) {
                            found.add(a1);
                            continue;
                        }
                        break;
                    case Opcodes.NEW:
                        if (((TypeInsnNode) a1).desc.equals(((TypeInsnNode) a2).desc)) {
                            found.add(a1);
                            continue;
                        }
                        break;
                    case Opcodes.GETFIELD:
                        FieldInsnNode f1 = (FieldInsnNode) a1;
                        FieldInsnNode f2 = (FieldInsnNode) a2;
                        if (f1.owner.equals(f2.owner) && f1.name.equals(f2.name) && f1.desc.equals(f2.desc)) {
                            found.add(a1);
                            continue;
                        }
                        break;
                    case Opcodes.INVOKESPECIAL:
                    case Opcodes.INVOKEVIRTUAL:
                        MethodInsnNode m1 = (MethodInsnNode) a1;
                        MethodInsnNode m2 = (MethodInsnNode) a2;
                        if (m1.owner.equals(m2.owner) && m1.name.equals(m2.name)
                            && m1.desc.equals(m2.desc)
                            && m1.itf == m2.itf) {
                            found.add(a1);
                            continue;
                        }
                        break;
                }
                break;
            }
            if (found.size() == searchSize) {
                break;
            }
        }
        if (found.size() == searchSize) {
            subject.insert(found.get(0), replace);
            for (AbstractInsnNode node : found) {
                subject.remove(node);
            }
            return true;
        } else {
            return false;
        }
    }

}
