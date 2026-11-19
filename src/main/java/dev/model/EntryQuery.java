package dev.model;

import dev.security.VaultHealth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Búsqueda con operadores. Texto libre (todas las palabras deben aparecer en nombre, usuario, sitio, categoría o notas)
 * más filtros: {@code cat:web}, {@code user:ana}, {@code url:example}, {@code is:weak|reused|old|empty|2fa|fav}.
 * Entre comillas se admiten espacios ({@code cat:"redes sociales"}) y con {@code -} delante se niega ({@code -is:old}).
 */
public final class EntryQuery {
    private record Term(String field, String value, boolean negated) {}

    private final List<Term> terms;

    private EntryQuery(List<Term> terms) {
        this.terms = terms;
    }

    public static EntryQuery parse(String text) {
        List<Term> out = new ArrayList<>();
        if (text != null) {
            for (String raw : tokenize(text)) {
                boolean neg = raw.startsWith("-") && raw.length() > 1;
                String t = neg ? raw.substring(1) : raw;
                int colon = t.indexOf(':');
                String field = colon > 0 ? t.substring(0, colon).toLowerCase(Locale.ROOT) : "";
                if (!field.isEmpty() && !isOperator(field)) field = "";
                String value = field.isEmpty() ? t : t.substring(colon + 1);
                value = value.toLowerCase(Locale.ROOT);
                if (value.isEmpty()) continue; // «cat:» a medio escribir no filtra nada todavía
                out.add(new Term(canonical(field), value, neg));
            }
        }
        return new EntryQuery(out);
    }

    public boolean isEmpty() {
        return terms.isEmpty();
    }

    /** @param health estado de la entrada (para {@code is:weak}…); puede ser {@code null} si no se conoce. */
    public boolean matches(PasswordEntry e, VaultHealth.EntryHealth health) {
        for (Term t : terms) {
            if (matchTerm(t, e, health) == t.negated()) return false;
        }
        return true;
    }

    public boolean matches(PasswordEntry e, Function<PasswordEntry, VaultHealth.EntryHealth> healthOf) {
        return matches(e, healthOf == null ? null : healthOf.apply(e));
    }

    private static boolean matchTerm(Term t, PasswordEntry e, VaultHealth.EntryHealth h) {
        return switch (t.field()) {
            case "cat" -> contains(e.getCategory(), t.value());
            case "user" -> contains(e.getUsername(), t.value());
            case "url" -> contains(e.getUrl(), t.value());
            case "is" -> switch (t.value()) {
                case "weak" -> h != null && h.has(VaultHealth.Flag.WEAK);
                case "reused" -> h != null && h.has(VaultHealth.Flag.REUSED);
                case "old" -> h != null && h.has(VaultHealth.Flag.OLD);
                case "empty" -> h != null && h.has(VaultHealth.Flag.EMPTY);
                case "2fa" -> e.getTotpSecret() != null && !e.getTotpSecret().isBlank();
                case "fav" -> e.isFavorite();
                default -> false;
            };
            default -> contains(e.getTitle(), t.value()) || contains(e.getUsername(), t.value())
                    || contains(e.getUrl(), t.value()) || contains(e.getCategory(), t.value())
                    || contains(e.getNotes(), t.value());
        };
    }

    private static boolean contains(String hay, String needle) {
        return hay != null && hay.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean isOperator(String f) {
        return switch (f.toLowerCase(Locale.ROOT)) {
            case "cat", "categoria", "categoría", "user", "usuario", "url", "sitio", "is", "es" -> true;
            default -> false;
        };
    }

    private static String canonical(String f) {
        return switch (f) {
            case "categoria", "categoría" -> "cat";
            case "usuario" -> "user";
            case "sitio" -> "url";
            case "es" -> "is";
            default -> f;
        };
    }

    /** Parte por espacios respetando las comillas (que se quitan). */
    private static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (char c : s.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (cur.length() > 0) out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /** Añade o quita un operador en el texto de búsqueda (para los filtros rápidos). */
    public static String toggleToken(String text, String token) {
        List<String> parts = new ArrayList<>(tokenizeKeepQuotes(text));
        if (!parts.remove(token)) parts.add(token);
        return String.join(" ", parts);
    }

    public static boolean hasToken(String text, String token) {
        return tokenizeKeepQuotes(text).contains(token);
    }

    private static List<String> tokenizeKeepQuotes(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (char c : s.toCharArray()) {
            if (c == '"') quoted = !quoted;
            if (Character.isWhitespace(c) && !quoted) {
                if (cur.length() > 0) out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
