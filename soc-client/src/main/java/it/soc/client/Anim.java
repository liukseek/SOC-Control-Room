package it.soc.client;

import javax.swing.Timer;
import java.util.function.DoubleConsumer;

public final class Anim {

    private Anim() {
    }

    public static Timer tween(int durationMs, DoubleConsumer frame, Runnable done) {
        long start = System.nanoTime();

        Timer timer = new Timer(15, null);

        timer.addActionListener(e -> {
            double elapsed = (System.nanoTime() - start) / 1_000_000.0;
            double progress = Math.min(1.0, elapsed / durationMs);

            frame.accept(easeOutCubic(progress));

            if (progress >= 1.0) {
                timer.stop();

                if (done != null) {
                    done.run();
                }
            }
        });

        timer.start();
        return timer;
    }

    public static Timer delay(int ms, Runnable action) {
        Timer timer = new Timer(ms, e -> action.run());
        timer.setRepeats(false);
        timer.start();
        return timer;
    }

    public static double easeOutCubic(double t) {
        return 1 - Math.pow(1 - t, 3);
    }

    public static double easeOutBack(double t) {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }

    public static double approach(double value, double target, double factor) {
        double next = value + (target - value) * factor;
        return Math.abs(target - next) < 0.001 ? target : next;
    }
}
