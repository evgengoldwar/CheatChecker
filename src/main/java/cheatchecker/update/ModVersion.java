package cheatchecker.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The numeric part of a version string ({@code 0.0.24}, {@code v1.2}, {@code 0.0.24-dirty}); anything after the first
 * run of dot-separated numbers is ignored. Unparseable strings compare by text only.
 */
public final class ModVersion implements Comparable<ModVersion> {

    /**
     * At least two dot-separated numbers, not running straight into letters: {@code 0.0.16-FixBug.37} is 0.0.16, but a
     * commit hash such as {@code 15221ab} is no version at all.
     */
    private static final Pattern NUMBERS = Pattern.compile("^[vV]?(\\d++(?:\\.\\d++)++)(?![0-9A-Za-z])");

    public final String raw;
    private final int[] parts;

    private ModVersion(String raw, int[] parts) {
        this.raw = raw;
        this.parts = parts;
    }

    public static ModVersion parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        Matcher m = NUMBERS.matcher(text);
        if (!m.find()) {
            return new ModVersion(text, null);
        }
        String[] split = m.group(1)
            .split("\\.");
        int[] parts = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            try {
                parts[i] = Integer.parseInt(split[i]);
            } catch (NumberFormatException e) {
                return new ModVersion(text, null);
            }
        }
        return new ModVersion(text, parts);
    }

    public boolean isParsed() {
        return parts != null;
    }

    @Override
    public int compareTo(ModVersion other) {
        if (parts == null || other.parts == null) {
            return raw.equals(other.raw) ? 0 : (parts == null ? -1 : 1);
        }
        int n = Math.max(parts.length, other.parts.length);
        for (int i = 0; i < n; i++) {
            int a = i < parts.length ? parts[i] : 0;
            int b = i < other.parts.length ? other.parts[i] : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    /**
     * Compact form for a chat column: just the numbers ({@code 0.0.16} for {@code 0.0.16-FixBug.37+cb856abb62}), or
     * the first 8 characters of an unparseable version such as a bare commit hash.
     */
    public String shortText() {
        if (parts == null) {
            return raw.length() > 8 ? raw.substring(0, 8) : raw;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /** Whether {@link #shortText()} leaves something out (a dev build suffix). */
    public boolean isShortened() {
        return !shortText().equals(toString());
    }

    /** The version without a leading {@code v}, as shown to the player. */
    @Override
    public String toString() {
        return raw.startsWith("v") || raw.startsWith("V") ? raw.substring(1) : raw;
    }
}
