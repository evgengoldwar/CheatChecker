package cheatchecker.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The numeric part of a version string ({@code 0.0.24}, {@code v1.2}, {@code 0.0.24-dirty}); anything after the first
 * run of dot-separated numbers is ignored. Unparseable strings compare by text only.
 */
public final class ModVersion implements Comparable<ModVersion> {

    private static final Pattern NUMBERS = Pattern.compile("^[vV]?(\\d+(?:\\.\\d+)*)");

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

    /** The version without a leading {@code v}, as shown to the player. */
    @Override
    public String toString() {
        return raw.startsWith("v") || raw.startsWith("V") ? raw.substring(1) : raw;
    }
}
