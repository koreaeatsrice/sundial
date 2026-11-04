package sundial.asm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Bytecode regression tests for the ASM matcher, run against synthetic classes built
 * in the OBFUSCATED namespace the production runtime sees.
 *
 * <p>
 * The regression this pins: {@code WorldClient.tick()} (obf {@code bjf.b()}) advances
 * TWO clocks — the day/moon clock {@code setWorldTime(getWorldTime()+1)} (obf
 * {@code b(J()+1)}) and the world-age clock {@code func_82738_a(getTotalWorldTime()+1)}
 * (obf {@code a(I()+1)}). {@code World.getMoonPhase()} and {@code getCelestialAngle()}
 * read the DAY clock, so that is the one the transformer must dilute. An earlier build
 * had the two obf method codes swapped: it diluted the world-age clock and deleted the
 * day clock, leaving the client's lunar phase (and the sky) driven by the wrong counter.
 */
public class TransformerTest {

    // ---- synthetic obfuscated classes ----

    private static byte[] worldClientClass() {
        ClassNode cn = base("bjf");
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, "b", "()V", null, null);
        // world-age clock: this.a(this.I() + 1) -> MUST be left alone (stays +1/tick)
        m.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(call("bjf", "I", "()J"));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.LCONST_1));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.LADD));
        m.instructions.add(call("bjf", "a", "(J)V"));
        // day/moon clock: this.b(this.J() + 1) -> MUST be replaced by WorldHandler.tick()
        m.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(call("bjf", "J", "()J"));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.LCONST_1));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.LADD));
        m.instructions.add(call("bjf", "b", "(J)V"));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
        cn.methods.add(m);
        return write(cn);
    }

    private static byte[] worldServerClass() {
        ClassNode cn = base("mt");
        cn.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PRIVATE, "x", "Lays;", null, null));
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, "b", "()V", null, null);
        m.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "mt", "x", "Lays;"));
        m.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "mt", "x", "Lays;"));
        m.instructions.add(call("ays", "g", "()J"));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.LCONST_1));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.LADD));
        m.instructions.add(call("ays", "c", "(J)V"));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
        cn.methods.add(m);
        return write(cn);
    }

    private static byte[] minecraftServerClass() {
        ClassNode cn = base("mcserver");
        cn.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PRIVATE, "tickCounter", "I", null, null));
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
        m.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "mcserver", "tickCounter", "I"));
        m.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 20));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IREM));
        org.objectweb.asm.tree.LabelNode done = new org.objectweb.asm.tree.LabelNode();
        m.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFNE, done));
        m.instructions.add(done);
        m.instructions.add(new LdcInsnNode("timeSync"));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
        cn.methods.add(m);
        return write(cn);
    }

    private static ClassNode base(String name) {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V1_6;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = name;
        cn.superName = "java/lang/Object";
        return cn;
    }

    private static MethodInsnNode call(String owner, String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, desc, false);
    }

    private static byte[] write(ClassNode cn) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static MethodNode findMethod(byte[] bytes, String name) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        for (MethodNode m : cn.methods) {
            if (m.name.equals(name)) return m;
        }
        throw new AssertionError("no method " + name);
    }

    private static boolean hasCall(MethodNode m, String owner, String name, String desc) {
        for (AbstractInsnNode n : m.instructions.toArray()) {
            if (n instanceof MethodInsnNode) {
                MethodInsnNode mi = (MethodInsnNode) n;
                if (mi.owner.equals(owner) && mi.name.equals(name) && mi.desc.equals(desc)) return true;
            }
        }
        return false;
    }

    // ---- tests ----

    @Test
    public void worldClientDilutesTheDayMoonClockAndLeavesTheAgeClock() {
        Transformer t = new Transformer();
        byte[] out = t.transform("bjf", "net.minecraft.client.multiplayer.WorldClient", worldClientClass());
        MethodNode b = findMethod(out, "b");

        // the day/moon clock is now driven by Sundial.tick
        assertTrue("must call WorldHandler.tick", hasCall(b, "sundial/WorldHandler", "tick", "(Lahb;)J"));
        // ...and still written back through the day clock setter
        assertTrue("must call bjf.b(J)V", hasCall(b, "bjf", "b", "(J)V"));

        // the day/moon +1 form must be GONE (replaced, not merely removed)
        assertFalse("day-clock +1 must be replaced", isAddOneForm(b, "J", "b"));

        // the world-age increment must be UNTOUCHED
        assertTrue("age clock getter must remain", hasCall(b, "bjf", "I", "()J"));
        assertTrue("age clock setter must remain", hasCall(b, "bjf", "a", "(J)V"));
        assertTrue("age clock +1 form must remain", isAddOneForm(b, "I", "a"));
    }

    @Test
    public void worldServerDilutesTheDayClock() {
        Transformer t = new Transformer();
        byte[] out = t.transform("mt", "net.minecraft.world.WorldServer", worldServerClass());
        MethodNode b = findMethod(out, "b");
        assertTrue(hasCall(b, "sundial/WorldHandler", "tick", "(Lahb;)J"));
        assertTrue(hasCall(b, "ays", "c", "(J)V"));
        assertFalse(isAddOneForm(b, "g", "c"));
    }

    @Test
    public void minecraftServerBroadcastsEveryTick() {
        Transformer t = new Transformer();
        byte[] out = t.transform("mcserver", "net.minecraft.server.MinecraftServer", minecraftServerClass());
        MethodNode tick = findMethod(out, "tick");
        boolean sawIconst1BeforeIrem = false;
        List<AbstractInsnNode> insns = java.util.Arrays.asList(tick.instructions.toArray());
        for (int i = 0; i + 1 < insns.size(); i++) {
            if (insns.get(i)
                .getOpcode() == Opcodes.ICONST_1
                && insns.get(i + 1)
                    .getOpcode() == Opcodes.IREM) {
                sawIconst1BeforeIrem = true;
            }
        }
        assertTrue("BIPUSH 20 must become ICONST_1", sawIconst1BeforeIrem);
        assertFalse("BIPUSH 20 must be gone", hasBipush20(tick));
    }

    /** true when {@code getter()J; LCONST_1; LADD; setter(J)V} is still contiguous. */
    private static boolean isAddOneForm(MethodNode m, String getter, String setter) {
        AbstractInsnNode[] a = m.instructions.toArray();
        for (int i = 0; i + 3 < a.length; i++) {
            if (a[i] instanceof MethodInsnNode) {
                MethodInsnNode g = (MethodInsnNode) a[i];
                if (g.name.equals(getter) && g.desc.equals("()J")
                    && a[i + 1].getOpcode() == Opcodes.LCONST_1
                    && a[i + 2].getOpcode() == Opcodes.LADD
                    && a[i + 3] instanceof MethodInsnNode
                    && ((MethodInsnNode) a[i + 3]).name.equals(setter)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasBipush20(MethodNode m) {
        for (AbstractInsnNode n : m.instructions.toArray()) {
            if (n instanceof IntInsnNode && n.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) n).operand == 20) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void matcherIsIdempotent() {
        Transformer t = new Transformer();
        byte[] once = t.transform("bjf", "net.minecraft.client.multiplayer.WorldClient", worldClientClass());
        byte[] twice = t.transform("bjf", "net.minecraft.client.multiplayer.WorldClient", once);
        assertEquals("must not double-patch", 1, countTickCalls(findMethod(twice, "b")));
        assertTrue("age clock must survive a re-transform", hasCall(findMethod(twice, "b"), "bjf", "a", "(J)V"));
    }

    private static int countTickCalls(MethodNode m) {
        int n = 0;
        for (AbstractInsnNode a : m.instructions.toArray()) {
            if (a instanceof MethodInsnNode) {
                MethodInsnNode mi = (MethodInsnNode) a;
                if (mi.owner.equals("sundial/WorldHandler") && mi.name.equals("tick")) n++;
            }
        }
        return n;
    }
}
