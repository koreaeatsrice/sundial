package cpw.mods.fml.relauncher;

import org.apache.logging.log4j.Level;

/**
 * Test-only stub for the FML log. The unit tests exercise
 * {@link sundial.asm.Transformer} outside a Forge runtime, and the transformer logs
 * through {@code FMLRelaunchLog.log(String, Level, String, Object...)} (varargs). The
 * stub has the real signature so the transformer classes link; messages are discarded.
 */
public class FMLRelaunchLog {

    public static void log(String modId, Level level, String message, Object... args) {
        // intentionally silent in tests
    }
}
