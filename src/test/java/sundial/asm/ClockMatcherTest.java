package sundial.asm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Pins the structural clock matcher.
 *
 * <p>
 * The matcher must find a clock by SHAPE (long get, +1, long set) and must never
 * treat the 32-bit world-age counter as a candidate. The day/moon site is then the
 * one guarded by {@code "doDaylightCycle"}.
 */
public class ClockMatcherTest {

    private static final String OWNER = "bjf";

    private static InsnList longIncrement() {
        InsnList list = new InsnList();
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OWNER, "J", "()J", false));
        list.add(new InsnNode(Opcodes.LCONST_1));
        list.add(new InsnNode(Opcodes.LADD));
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OWNER, "b", "(J)V", false));
        return list;
    }

    private static InsnList intIncrement() {
        InsnList list = new InsnList();
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OWNER, "I", "()I", false));
        list.add(new InsnNode(Opcodes.ICONST_1));
        list.add(new InsnNode(Opcodes.IADD));
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OWNER, "a", "(I)V", false));
        return list;
    }

    @Test
    public void findsTheLongIncrementByShape() {
        InsnList insns = new InsnList();
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(longIncrement());
        assertEquals(
            1,
            ClockMatcher.findLongIncrementWindows(insns)
                .size());
    }

    @Test
    public void ignoresTheIntWorldAgeIncrement() {
        InsnList insns = new InsnList();
        insns.add(intIncrement());
        insns.add(longIncrement());
        List<AbstractInsnNode[]> windows = ClockMatcher.findLongIncrementWindows(insns);
        assertEquals("only the long clock may match", 1, windows.size());
        assertEquals("()J", ((MethodInsnNode) windows.get(0)[0]).desc);
    }

    @Test
    public void daylightGuardSelectsTheDayClock() {
        InsnList insns = new InsnList();
        // world-age clock first, no gamerule string
        insns.add(intIncrement());
        // ... then the gated day clock
        insns.add(new LdcInsnNode("doDaylightCycle"));
        insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "bjf", "x", "()Lays;", false));
        insns.add(longIncrement());

        InsnList onlyLong = new InsnList();
        onlyLong.add(new LdcInsnNode("doDaylightCycle"));
        onlyLong.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "bjf", "x", "()Lays;", false));
        onlyLong.add(longIncrement());

        List<AbstractInsnNode[]> windows = ClockMatcher.findLongIncrementWindows(onlyLong);
        assertEquals(1, windows.size());
        assertTrue(ClockMatcher.isDaylightGuarded(onlyLong, windows.get(0)[0]));
    }

    @Test
    public void unguardedLongClockIsRejected() {
        InsnList insns = new InsnList();
        insns.add(longIncrement());
        List<AbstractInsnNode[]> windows = ClockMatcher.findLongIncrementWindows(insns);
        assertEquals(1, windows.size());
        assertFalse(
            "a long clock without the guard must not be patched",
            ClockMatcher.isDaylightGuarded(insns, windows.get(0)[0]));
    }

    @Test
    public void mismatchedOwnersAreNotAClockWindow() {
        InsnList insns = new InsnList();
        insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "bjf", "J", "()J", false));
        insns.add(new InsnNode(Opcodes.LCONST_1));
        insns.add(new InsnNode(Opcodes.LADD));
        insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "ays", "b", "(J)V", false));
        assertEquals(
            0,
            ClockMatcher.findLongIncrementWindows(insns)
                .size());
    }
}
