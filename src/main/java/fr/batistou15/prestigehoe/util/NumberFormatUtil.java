package fr.batistou15.prestigehoe.util;

import java.util.Locale;

/**
 * Formatage compact des grands nombres :
 *  950           -> "950"
 *  12 300        -> "12.3K"
 *  1 200 000     -> "1.2M"
 *  3 450 000 000 -> "3.45B"
 *  7 800 000 000 000 -> "7.8T"
 *  1e15          -> "1Qa"
 *  1e18          -> "1Qi"
 *  1e21          -> "1Sx"
 *  1e24          -> "1Sp"
 *  1e27          -> "1Oc"
 *  1e30          -> "1No"
 */
public final class NumberFormatUtil {

    private static final double[] THRESHOLDS = {
            1_000.0,               // K
            1_000_000.0,           // M
            1_000_000_000.0,       // B
            1_000_000_000_000.0,   // T
            1_000_000_000_000_000.0,             // Qa (quadrillion)
            1_000_000_000_000_000_000.0,         // Qi (quintillion)
            1e21,  // Sx
            1e24,  // Sp
            1e27,  // Oc
            1e30   // No
    };

    private static final String[] SUFFIXES = {
            "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No"
    };

    private NumberFormatUtil() {
    }

    public static String formatShort(long value) {
        return formatShort((double) value);
    }

    public static String formatShort(double value) {
        boolean negative = value < 0;
        double abs = Math.abs(value);

        // Pas besoin de suffixe
        if (abs < 1000.0) {
            String base;
            if (Math.abs(abs - Math.rint(abs)) < 1e-9) {
                base = String.format(Locale.US, "%.0f", abs);
            } else {
                base = String.format(Locale.US, "%.2f", abs);
            }
            return (negative ? "-" : "") + base;
        }

        // Cherche le plus grand seuil applicable
        double divisor = 1.0;
        String suffix = "";
        for (int i = THRESHOLDS.length - 1; i >= 0; i--) {
            if (abs >= THRESHOLDS[i]) {
                divisor = THRESHOLDS[i];
                suffix = SUFFIXES[i];
                break;
            }
        }

        double scaled = abs / divisor;
        String formatted;
        if (scaled < 10) {
            formatted = String.format(Locale.US, "%.2f", scaled);
        } else if (scaled < 100) {
            formatted = String.format(Locale.US, "%.1f", scaled);
        } else {
            formatted = String.format(Locale.US, "%.0f", scaled);
        }

        if (negative) {
            formatted = "-" + formatted;
        }
        return formatted + suffix;
    }
}
