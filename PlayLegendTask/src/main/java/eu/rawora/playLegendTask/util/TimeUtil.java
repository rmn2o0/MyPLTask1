package eu.rawora.playLegendTask.util;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hilfsklasse zur Verarbeitung von Zeitangaben und Dauerformatierungen.
 */
public final class TimeUtil { // final, da nur statische Methoden

    // Privater Konstruktor, um Instanziierung zu verhindern
    private TimeUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // Regulärer Ausdruck, um Zeitformate wie 1d7h30m10s zu erkennen (Groß-/Kleinschreibung egal)
    // Erlaubt optionale Einheiten (Tage, Stunden, Minuten, Sekunden) in beliebiger Kombination.
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(?:(\\d+)\\s*d(?:ays?)?)?\\s*" +    // Tage (optional) mit optionalem Suffix 'day'/'days'
                    "(?:(\\d+)\\s*h(?:ours?)?)?\\s*" +   // Stunden (optional) mit optionalem Suffix 'hour'/'hours'
                    "(?:(\\d+)\\s*m(?:inutes?)?)?\\s*" + // Minuten (optional) mit optionalem Suffix 'minute'/'minutes'
                    "(?:(\\d+)\\s*s(?:econds?)?)?",      // Sekunden (optional) mit optionalem Suffix 'second'/'seconds'
            Pattern.CASE_INSENSITIVE);

    /**
     * Wandelt einen Dauer-String (z.B., "1d", "7h30m", "10s", "2h 5m 30s") in Millisekunden um.
     * Ignoriert Leerzeichen zwischen den Einheiten.
     * Gibt null zurück, wenn das Format ungültig ist, keine Zeiteinheit gefunden wurde oder die Dauer 0 ist.
     *
     * @param durationString Der zu parsende String.
     * @return Die Dauer in Millisekunden oder null bei ungültigem Format/Null-Dauer.
     */
    public static Long parseDuration(String durationString) {
        if (durationString == null || durationString.trim().isEmpty()) {
            return null;
        }
        long totalMillis = 0;
        // Entferne redundante Leerzeichen für den Matcher
        String trimmedDuration = durationString.trim();
        Matcher matcher = TIME_PATTERN.matcher(trimmedDuration);

        if (matcher.matches()) {
            boolean matchedAnyUnit = false; // Um zu prüfen, ob überhaupt eine Einheit gefunden wurde

            // Tage
            if (matcher.group(1) != null) {
                try {
                    totalMillis += TimeUnit.DAYS.toMillis(Integer.parseInt(matcher.group(1)));
                    matchedAnyUnit = true;
                } catch (NumberFormatException ignored) {
                }
            }
            // Stunden
            if (matcher.group(2) != null) {
                try {
                    totalMillis += TimeUnit.HOURS.toMillis(Integer.parseInt(matcher.group(2)));
                    matchedAnyUnit = true;
                } catch (NumberFormatException ignored) {
                }
            }
            // Minuten
            if (matcher.group(3) != null) {
                try {
                    totalMillis += TimeUnit.MINUTES.toMillis(Integer.parseInt(matcher.group(3)));
                    matchedAnyUnit = true;
                } catch (NumberFormatException ignored) {
                }
            }
            //  Sekunden
            if (matcher.group(4) != null) {
                try {
                    totalMillis += TimeUnit.SECONDS.toMillis(Integer.parseInt(matcher.group(4)));
                    matchedAnyUnit = true;
                } catch (NumberFormatException ignored) {
                }
            }

            // Gib null zurück, wenn keine Einheit erkannt wurde oder die Dauer 0 ist
            if (!matchedAnyUnit || totalMillis <= 0) {
                return null;
            }

            return totalMillis;

        } else {
            // Wenn das Pattern gar nicht passt
            return null;
        }
    }

    /**
     * Formatiert eine Dauer in Millisekunden in einen lesbaren String (z.B., "1d 7h 30m 10s").
     * Zeigt nur relevante Zeiteinheiten an (z.B. keine Tage, wenn Dauer < 1 Tag).
     *
     * @param millis Die Dauer in Millisekunden.
     * @return Ein formatierter String oder "0s" wenn die Dauer null oder negativ ist.
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return "0s"; // Oder "None", "Expired"? "0s" ist technisch korrekt.
        }

        // Wandle Millisekunden in Tage, Stunden, Minuten, Sekunden um
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days); // Restliche Millisekunden
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        // Baue den String zusammen
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        // Sekunden nur anzeigen, wenn sie > 0 sind ODER wenn keine anderen Einheiten da sind (damit nicht leer zurückgegeben wird..)
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append("s");
        }
        return sb.toString().trim();
    }

    public static String formatTimestamp(long timestampMillis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new java.util.Date(timestampMillis));
    }
}