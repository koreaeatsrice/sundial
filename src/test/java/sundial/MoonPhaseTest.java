package sundial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the 1.7.10 lunar rule and the invariant Sundial must preserve under time
 * dilation.
 *
 * <p>
 * Vanilla: {@code WorldProvider.getMoonPhase(long worldTime) =
 * (int) (worldTime / 24000L % 8L + 8L) % 8} and the sky/clock read the SAME counter,
 * so the phase is a pure function of the in-game day index
 * ({@code worldTime / 24000}). A player expects one phase step per day, in order,
 * no skips. Sundial dilates that clock (a day is {@code 1/scale} longer in real
 * time) but must not change the phase-per-day relationship.
 */
public class MoonPhaseTest {

    private static final long DAY = 24000L;

    /** The real 1.7.10 formula, copied verbatim. */
    static int moonPhase(long worldTime) {
        return (int) (worldTime / DAY % 8L + 8L) % 8;
    }

    /** The day index the sun/clock/moon all share. */
    static long dayIndex(long worldTime) {
        return Math.floorDiv(worldTime, DAY);
    }

    @Test
    public void phaseEqualsDayIndexModEight() {
        for (long day = 0; day < 200; day++) {
            long start = day * DAY;
            assertEquals("day " + day, (int) Math.floorMod(day, 8L), moonPhase(start));
            // constant across the whole day (changes at the boundary, not mid-day)
            assertEquals(moonPhase(start), moonPhase(start + DAY - 1));
        }
    }

    @Test
    public void phaseAdvancesByOneEachDayWithoutSkips() {
        int prev = moonPhase(0);
        for (long day = 1; day < 100; day++) {
            int now = moonPhase(day * DAY);
            assertEquals("no skip on day " + day, (prev + 1) % 8, now);
            prev = now;
        }
    }

    /**
     * The mod's fractional accumulator, reproduced exactly from
     * {@link WorldHandler#tick}. Runs the dilated clock for N ticks and asserts the
     * moon phase is STILL a pure function of the day index (i.e. it tracks the in-game
     * day count) at every sampled tick.
     */
    @Test
    public void phaseTracksDayIndexUnderDilation() {
        for (double scale : new double[] { 0.3333d, 0.5d, 1.0d, 2.0d, 10.0d }) {
            double acc = 0.0d;
            long worldTime = 0L;
            for (int tick = 0; tick < 400_000; tick++) {
                acc += scale;
                long whole = (long) Math.floor(acc);
                if (whole < 0) whole = 0;
                acc -= whole;
                worldTime += whole;

                // the invariant: whatever the scale, phase == dayIndex % 8
                assertEquals(
                    "scale " + scale + " tick " + tick,
                    (int) Math.floorMod(dayIndex(worldTime), 8L),
                    moonPhase(worldTime));
            }
            // dilation sanity: the day count scales with 'scale' (vanilla ~= 1/scale longer days)
            long expectedDays = (long) Math.floor(400_000 * scale / DAY);
            assertTrue(
                "scale " + scale + " days=" + dayIndex(worldTime) + " expected~" + expectedDays,
                Math.abs(dayIndex(worldTime) - expectedDays) <= 1);
        }
    }
}
