package dev.view;

import dev.persistence.AppPrefs;

import javax.swing.Timer;
import java.util.function.DoubleConsumer;

/** Animaciones cortas y sobrias. Con «reducir movimiento» el estado final se aplica al instante. */
public final class Motion {

    private Motion() {}

    public static boolean enabled() {
        return !AppPrefs.reduceMotion();
    }

    /** ease-out cúbico (la curva del estilo: sin rebote). */
    public static double easeOut(double t) {
        double u = 1 - Math.max(0, Math.min(1, t));
        return 1 - u * u * u;
    }

    /** Ejecuta {@code step(0..1)} durante {@code ms} milisegundos; si el movimiento está reducido, salta a 1 de una vez. */
    public static void run(int ms, DoubleConsumer step, Runnable done) {
        if (!enabled() || ms <= 0) {
            step.accept(1);
            if (done != null) done.run();
            return;
        }
        long start = System.nanoTime();
        Timer t = new Timer(16, null);
        t.addActionListener(e -> {
            double p = Math.min(1, (System.nanoTime() - start) / (ms * 1_000_000.0));
            step.accept(easeOut(p));
            if (p >= 1) {
                t.stop();
                if (done != null) done.run();
            }
        });
        t.start();
    }
}
