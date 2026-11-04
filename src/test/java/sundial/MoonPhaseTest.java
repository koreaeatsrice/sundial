package sundial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the 1.7.10 lunar rule and the invariants Sundial must preserve under time
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

    /**
     * The mod's fractional accumulator, reproduced exactly from
     * {@link WorldHandler#tick}: only whole ticks are ever applied and the clock
     * never moves backwards.
     */
    static final class DilatedClock {

        private double acc = 0.0d;
        private long worldTime = 0L;

        long tick(double scale) {
            double next = acc + scale;
            long whole = (long) Math.floor(next);
            if (whole < 0) whole = 0;
            acc = next - whole;
            worldTime += whole;
            return worldTime;
        }

        long worldTime() {
            return worldTime;
        }
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

    /** A dilated day is still 24000 GAME ticks long, whatever the real-time scale. */
    @Test
    public void aDayLastsTwentyFourThousandGameTicks() {
        for (double scale : new double[] { 0.1d, 0.25d, 0.3333d, 0.5d, 1.0d, 2.0d, 10.0d }) {
            DilatedClock clock = new DilatedClock();
            long start = clock.worldTime();
            while (clock.worldTime() - start < DAY) {
                clock.tick(scale);
            }
            assertEquals(
                "scale " + scale + " must still advance 24000 game ticks per day",
                DAY,
                clock.worldTime() - start);
        }
    }

    /** 200 real ticks at scale 0.25 advance the world clock by exactly 50. */
    @Test
    public void twoHundredTicksAtQuarterSpeedAdvanceFifty() {
        DilatedClock clock = new DilatedClock();
        for (int i = 0; i < 200; i++) {
            clock.tick(0.25d);
        }
        assertEquals(50L, clock.worldTime());
    }

    /** Phase changes exactly ON multiples of 24000, never one tick early. */
    @Test
    public void phaseFlipsAtExactDayBoundaries() {
        for (long day = 1; day < 50; day++) {
            long boundary = day * DAY;
            assertEquals("one tick before day " + day, (int) Math.floorMod(day - 1, 8L), moonPhase(boundary - 1));
            assertEquals("on day boundary " + day, (int) Math.floorMod(day, 8L), moonPhase(boundary));
            assertEquals(moonPhase(boundary), moonPhase(boundary + DAY - 1));
        }
    }

    /**
     * The invariant: at every tick, under any scale, the phase is STILL a pure
     * function of the day index — i.e. it tracks the in-game day count and cannot
     * stall or skip because of dilation.
     */
    @Test
    public void phaseTracksDayIndexUnderDilation() {
        for (double scale : new double[] { 0.3333d, 0.5d, 1.0d, 2.0d, 10.0d }) {
            DilatedClock clock = new DilatedClock();
            for (int tick = 0; tick < 400_000; tick++) {
                long worldTime = clock.tick(scale);
                assertEquals(
                    "scale " + scale + " tick " + tick,
                    (int) Math.floorMod(dayIndex(worldTime), 8L),
                    moonPhase(worldTime));
            }
            long expectedDays = (long) Math.floor(400_000 * scale / DAY);
            assertTrue(
                "scale " + scale + " days=" + dayIndex(clock.worldTime()) + " expected~" + expectedDays,
                Math.abs(dayIndex(clock.worldTime()) - expectedDays) <= 1);
        }
    }
}
