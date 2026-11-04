package sundial.asm;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Structural locator for the vanilla "clock advances by one" idiom.
 *
 * <p>
 * Both of the clocks in {@code World.tick()} are written as
 *
 * <pre>
 *     thing.getClock()        // invokevirtual ()J
 *     LCONST_1
 *     LADD
 *     thing.setClock(long)    // invokevirtual (J)V
 * </pre>
 *
 * so the shape alone identifies a candidate site without trusting any method
 * name — obfuscated or MCP. A 32-bit ({@code I}) counter does NOT match: the
 * descriptor filter is {@code ()J} / {@code (J)V}. That is the rule that keeps
 * Sundial off the world-age clock: diluting that one made the client's day clock
 * vanish while the moon phase stopped advancing.
 *
 * <p>
 * Kept free of Minecraft/FML types on purpose so it can be unit tested.
 */
public final class ClockMatcher {

    /** How far back to look for the gamerule guard string. */
    public static final int GUARD_SCAN = 40;

    /** The string constant that guards the client's day/moon increment. */
    public static final String DAYLIGHT_GUARD = "doDaylightCycle";

    private ClockMatcher() {}

    /**
     * Every {@code getLong(); LCONST_1; LADD; setLong(long)} window in {@code insns},
     * as the four instruction nodes, in method order.
     */
    public static List<AbstractInsnNode[]> findLongIncrementWindows(InsnList insns) {
        List<AbstractInsnNode[]> windows = new ArrayList<AbstractInsnNode[]>();
        for (int i = 0; i + 3 < insns.size(); i++) {
            AbstractInsnNode getterNode = insns.get(i);
            AbstractInsnNode one = insns.get(i + 1);
            AbstractInsnNode add = insns.get(i + 2);
            AbstractInsnNode setterNode = insns.get(i + 3);
            if (getterNode.getOpcode() != Opcodes.INVOKEVIRTUAL || one.getOpcode() != Opcodes.LCONST_1
                || add.getOpcode() != Opcodes.LADD
                || setterNode.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                continue;
            }
            MethodInsnNode getter = (MethodInsnNode) getterNode;
            MethodInsnNode setter = (MethodInsnNode) setterNode;
            if (!"()J".equals(getter.desc) || !"(J)V".equals(setter.desc)) continue;
            if (!getter.owner.equals(setter.owner)) continue;
            windows.add(new AbstractInsnNode[] { getterNode, one, add, setterNode });
        }
        return windows;
    }

    /**
     * True when the gamerule string {@code "doDaylightCycle"} appears within
     * {@link #GUARD_SCAN} instructions before the window — i.e. this is the
     * daylight-gated day/moon increment, not some other long counter.
     */
    public static boolean isDaylightGuarded(InsnList insns, AbstractInsnNode windowStart) {
        int seen = 0;
        for (AbstractInsnNode n = windowStart.getPrevious(); n != null
            && seen < GUARD_SCAN; n = n.getPrevious(), seen++) {
            if (!(n instanceof LdcInsnNode)) continue;
            if (DAYLIGHT_GUARD.equals(((LdcInsnNode) n).cst)) return true;
        }
        return false;
    }
}
