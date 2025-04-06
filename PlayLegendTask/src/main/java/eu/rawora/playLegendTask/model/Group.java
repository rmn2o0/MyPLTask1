package eu.rawora.playLegendTask.model;

import org.bukkit.ChatColor;

import java.util.Objects;

public class Group {
    private final String name;
    private String prefix;

    /**
     * Konstruktor für eine Gruppe.
     * @param name Der eindeutige Name der Gruppe (Groß-/Kleinschreibung wird oft ignoriert).
     * @param prefix Der Prefix der Gruppe (Farb-Codes mit '&' werden unterstützt).
     */
    public Group(String name, String prefix) {
        this.name = Objects.requireNonNull(name, "Group name cannot be null");
        this.prefix = prefix != null ? prefix : "";
    }

    /**
     * Gibt den Namen der Gruppe zurück.
     * @return Gruppenname.
     */
    public String getName() {
        return name;
    }

    /**
     * Gibt den rohen Prefix-String zurück (mit '&' Codes).
     * @return Prefix-String.
     */
    public String getRawPrefix() {
        return prefix;
    }

    /**
     * Gibt den Prefix mit aufgelösten Farb-Codes zurück.
     * @return Farbiger Prefix.
     */
    public String getPrefix() {
        return ChatColor.translateAlternateColorCodes('&', this.prefix);
    }

    /**
     * Setzt den Prefix der Gruppe.
     * @param prefix Der neue Prefix (mit '&' für Farben).
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix != null ? prefix : "";
    }

    /**
     * Vergleicht Gruppen basierend auf ihrem Namen (ignoriert Groß-/Kleinschreibung).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Group group = (Group) o;
        // Wichtig: Vergleich ignoriert Groß-/Kleinschreibung
        return name.equalsIgnoreCase(group.name);
    }

    /**
     * Gibt einen Hashcode basierend auf dem Namen zurück (ignoriert Groß-/Kleinschreibung).
     */
    @Override
    public int hashCode() {
        // Wichtig: Hashcode muss zur equals-Methode passen
        return name.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return "Group{" +
               "name='" + name + '\'' +
               ", prefix='" + prefix + '\'' +
               '}';
    }
}