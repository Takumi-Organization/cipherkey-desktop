package dev.security;

import dev.model.PasswordEntry;
import dev.security.PasswordStrength.Level;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Salud de la bóveda: una puntuación 0–100 y un desglose por entrada. Reutiliza {@link PasswordAudit} y
 * {@link PasswordStrength}; no toca ni guarda contraseñas, solo cuenta.
 *
 * <p>Cada entrada parte de 1,0 y pierde: vacía 1,0 · muy débil 0,6 · débil 0,4 · repetida 0,35 · antigua 0,15.
 * La puntuación es la media de las entradas por 100.
 */
public final class VaultHealth {
    public enum Flag { WEAK, REUSED, OLD, EMPTY }

    public enum Grade {
        NONE("Sin datos"), CRITICAL("Crítica"), FAIR("Mejorable"), GOOD("Buena"), EXCELLENT("Excelente");

        private final String label;

        Grade(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Estado de una entrada: problemas detectados y fuerza de su contraseña. */
    public record EntryHealth(Set<Flag> flags, Level level, double bits, double health) {
        public boolean has(Flag f) {
            return flags.contains(f);
        }

        public boolean isHealthy() {
            return flags.isEmpty();
        }
    }

    /** {@code strength[i]} = nº de entradas con el nivel {@code Level.values()[i]} (las vacías no cuentan). */
    public record Result(int score, Grade grade, int total, int weak, int reused, int old, int empty, int withTotp,
                         int[] strength, Map<String, EntryHealth> byUuid, PasswordAudit.Report report) {
        public EntryHealth of(PasswordEntry e) {
            return byUuid.get(e.getUuid());
        }

        public int healthy() {
            int n = 0;
            for (EntryHealth h : byUuid.values()) if (h.isHealthy()) n++;
            return n;
        }

        public int issues() {
            return report.total();
        }
    }

    private VaultHealth() {}

    public static Result analyze(List<PasswordEntry> entries, LocalDateTime now, int oldDays) {
        PasswordAudit.Report report = PasswordAudit.analyze(entries, now, oldDays);
        Set<String> weak = uuids(report.weak()), reused = uuids(report.reused()), old = uuids(report.old()), empty = uuids(report.empty());

        Map<String, EntryHealth> byUuid = new LinkedHashMap<>();
        int[] strength = new int[Level.values().length];
        double sum = 0;
        int totp = 0;
        for (PasswordEntry e : entries) {
            EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
            if (weak.contains(e.getUuid())) flags.add(Flag.WEAK);
            if (reused.contains(e.getUuid())) flags.add(Flag.REUSED);
            if (old.contains(e.getUuid())) flags.add(Flag.OLD);
            if (empty.contains(e.getUuid())) flags.add(Flag.EMPTY);

            PasswordStrength.Result r = PasswordStrength.evaluate(e.getPassword());
            if (!flags.contains(Flag.EMPTY)) strength[r.level().ordinal()]++;

            double penalty = 0;
            if (flags.contains(Flag.EMPTY)) penalty += 1.0;
            if (flags.contains(Flag.WEAK)) penalty += r.level() == Level.VERY_WEAK ? 0.6 : 0.4;
            if (flags.contains(Flag.REUSED)) penalty += 0.35;
            if (flags.contains(Flag.OLD)) penalty += 0.15;
            double health = Math.max(0, 1 - penalty);
            sum += health;
            if (e.getTotpSecret() != null && !e.getTotpSecret().isBlank()) totp++;
            byUuid.put(e.getUuid(), new EntryHealth(Collections.unmodifiableSet(flags), r.level(), r.bits(), health));
        }

        int total = entries.size();
        int score = total == 0 ? 0 : (int) Math.round(100 * sum / total);
        return new Result(score, gradeFor(score, total), total, weak.size(), reused.size(), old.size(), empty.size(), totp,
                strength, Collections.unmodifiableMap(byUuid), report);
    }

    public static Grade gradeFor(int score, int total) {
        if (total == 0) return Grade.NONE;
        if (score >= 90) return Grade.EXCELLENT;
        if (score >= 75) return Grade.GOOD;
        if (score >= 50) return Grade.FAIR;
        return Grade.CRITICAL;
    }

    private static Set<String> uuids(List<PasswordAudit.Finding> findings) {
        Set<String> s = new HashSet<>();
        for (PasswordAudit.Finding f : findings) s.add(f.entry().getUuid());
        return s;
    }
}
