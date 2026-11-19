package dev.model;

import dev.security.VaultHealth;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntryQueryTest {
    private static PasswordEntry e(String title, String user, String url, String cat, String notes) {
        PasswordEntry p = new PasswordEntry(title, user, "k7#Qv9!xLm2$Rt8@Zp4^", url, notes);
        p.setCategory(cat);
        return p;
    }

    private static VaultHealth.EntryHealth h(PasswordEntry p) {
        return VaultHealth.analyze(List.of(p), LocalDateTime.now(), 365).of(p);
    }

    @Test
    void freeTextNeedsEveryWordAnywhere() {
        PasswordEntry p = e("Correo Norte", "ana", "https://mail.demo.test", "Trabajo", "buzón principal");
        assertTrue(EntryQuery.parse("correo trabajo").matches(p, h(p)));
        assertTrue(EntryQuery.parse("BUZÓN").matches(p, h(p)));
        assertFalse(EntryQuery.parse("correo banco").matches(p, h(p)));
        assertTrue(EntryQuery.parse("  ").isEmpty());
    }

    @Test
    void fieldOperatorsOnlyLookAtTheirField() {
        PasswordEntry p = e("Tienda", "ana", "https://shop.demo.test", "Compras", "");
        assertTrue(EntryQuery.parse("cat:compras").matches(p, h(p)));
        assertTrue(EntryQuery.parse("categoria:compras").matches(p, h(p)));
        assertTrue(EntryQuery.parse("user:an").matches(p, h(p)));
        assertTrue(EntryQuery.parse("url:shop").matches(p, h(p)));
        assertFalse(EntryQuery.parse("cat:tienda").matches(p, h(p))); // «tienda» está en el título, no en la categoría
    }

    @Test
    void quotedValuesAllowSpacesAndNegationInverts() {
        PasswordEntry p = e("Foro", "ana", "", "Redes sociales", "");
        assertTrue(EntryQuery.parse("cat:\"redes sociales\"").matches(p, h(p)));
        assertFalse(EntryQuery.parse("-cat:redes").matches(p, h(p)));
        assertTrue(EntryQuery.parse("-cat:banca").matches(p, h(p)));
    }

    @Test
    void isOperatorsUseHealthAndEntryFlags() {
        PasswordEntry weak = e("A", "u", "", "", "");
        weak.setPassword("123456");
        PasswordEntry strong = e("B", "u", "", "", "");
        strong.setFavorite(true);
        strong.setTotpSecret("JBSWY3DPEHPK3PXP");
        assertTrue(EntryQuery.parse("is:weak").matches(weak, h(weak)));
        assertFalse(EntryQuery.parse("is:weak").matches(strong, h(strong)));
        assertTrue(EntryQuery.parse("is:2fa is:fav").matches(strong, h(strong)));
        assertTrue(EntryQuery.parse("-is:2fa").matches(weak, h(weak)));
        assertFalse(EntryQuery.parse("is:old").matches(strong, h(strong)));
    }

    @Test
    void halfTypedOperatorDoesNotFilterAndUnknownOperatorIsText() {
        PasswordEntry p = e("Nube", "u", "", "", "");
        assertTrue(EntryQuery.parse("cat:").matches(p, h(p)));
        PasswordEntry q = e("web:app", "u", "", "", "");
        assertTrue(EntryQuery.parse("web:app").matches(q, h(q)));
    }

    @Test
    void toggleTokenAddsAndRemoves() {
        String t = EntryQuery.toggleToken("banco", "is:weak");
        assertEquals("banco is:weak", t);
        assertTrue(EntryQuery.hasToken(t, "is:weak"));
        assertEquals("banco", EntryQuery.toggleToken(t, "is:weak"));
        assertEquals("is:old", EntryQuery.toggleToken("", "is:old"));
    }
}
