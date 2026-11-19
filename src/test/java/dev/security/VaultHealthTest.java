package dev.security;

import dev.model.PasswordEntry;
import dev.security.VaultHealth.Flag;
import dev.security.VaultHealth.Grade;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VaultHealthTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 12, 0);
    private static final String STRONG_A = "k7#Qv9!xLm2$Rt8@Zp4^";
    private static final String STRONG_B = "Hn3&wD6*Yc1%Be5(Ua0)";

    private static PasswordEntry entry(String title, String pwd, LocalDateTime modified) {
        PasswordEntry e = new PasswordEntry(title, "demo", pwd, "", "");
        e.setModified(modified);
        return e;
    }

    @Test
    void emptyVaultHasNoGradeAndZeroScore() {
        VaultHealth.Result r = VaultHealth.analyze(List.of(), NOW, 365);
        assertEquals(0, r.score());
        assertEquals(Grade.NONE, r.grade());
    }

    @Test
    void healthyVaultScoresFullMarks() {
        VaultHealth.Result r = VaultHealth.analyze(List.of(
                entry("A", STRONG_A, NOW.minusDays(10)), entry("B", STRONG_B, NOW.minusDays(20))), NOW, 365);
        assertEquals(100, r.score());
        assertEquals(Grade.EXCELLENT, r.grade());
        assertEquals(2, r.healthy());
        assertEquals(0, r.issues());
    }

    @Test
    void flagsWeakReusedOldAndEmpty() {
        PasswordEntry weak = entry("Débil", "123456", NOW.minusDays(5));
        PasswordEntry r1 = entry("Rep1", STRONG_A, NOW.minusDays(5));
        PasswordEntry r2 = entry("Rep2", STRONG_A, NOW.minusDays(5));
        PasswordEntry old = entry("Vieja", STRONG_B, NOW.minusDays(500));
        PasswordEntry empty = entry("Vacía", "", NOW.minusDays(5));
        VaultHealth.Result r = VaultHealth.analyze(List.of(weak, r1, r2, old, empty), NOW, 365);

        assertTrue(r.of(weak).has(Flag.WEAK));
        assertTrue(r.of(r1).has(Flag.REUSED) && r.of(r2).has(Flag.REUSED));
        assertTrue(r.of(old).has(Flag.OLD));
        assertTrue(r.of(empty).has(Flag.EMPTY));
        assertEquals(1, r.weak());
        assertEquals(2, r.reused());
        assertEquals(1, r.old());
        assertEquals(1, r.empty());
        assertEquals(0, r.healthy());
    }

    @Test
    void scoreIsAverageOfEntryHealth() {
        // una sana (1,0) y una vacía (0,0) => 50
        VaultHealth.Result r = VaultHealth.analyze(List.of(
                entry("A", STRONG_A, NOW), entry("B", "", NOW)), NOW, 365);
        assertEquals(50, r.score());
        assertEquals(Grade.FAIR, r.grade());
    }

    @Test
    void reusedPenaltyIsAppliedToBothEntries() {
        VaultHealth.Result r = VaultHealth.analyze(List.of(
                entry("A", STRONG_A, NOW), entry("B", STRONG_A, NOW)), NOW, 365);
        assertEquals(65, r.score()); // 1 - 0,35
        assertEquals(Grade.FAIR, r.grade());
    }

    @Test
    void veryWeakCostsMoreThanWeakAndStrengthBucketsCountNonEmpty() {
        PasswordEntry veryWeak = entry("A", "123456", NOW);
        PasswordEntry empty = entry("B", "", NOW);
        PasswordEntry strong = entry("C", STRONG_A, NOW);
        VaultHealth.Result r = VaultHealth.analyze(List.of(veryWeak, empty, strong), NOW, 365);
        assertEquals(0.4, r.of(veryWeak).health(), 1e-9);
        int sum = 0;
        for (int n : r.strength()) sum += n;
        assertEquals(2, sum);
    }

    @Test
    void countsTotpAndGradeBoundaries() {
        PasswordEntry e = entry("A", STRONG_A, NOW);
        e.setTotpSecret("JBSWY3DPEHPK3PXP");
        assertEquals(1, VaultHealth.analyze(List.of(e), NOW, 365).withTotp());
        assertEquals(Grade.EXCELLENT, VaultHealth.gradeFor(90, 3));
        assertEquals(Grade.GOOD, VaultHealth.gradeFor(89, 3));
        assertEquals(Grade.GOOD, VaultHealth.gradeFor(75, 3));
        assertEquals(Grade.FAIR, VaultHealth.gradeFor(74, 3));
        assertEquals(Grade.FAIR, VaultHealth.gradeFor(50, 3));
        assertEquals(Grade.CRITICAL, VaultHealth.gradeFor(49, 3));
    }
}
