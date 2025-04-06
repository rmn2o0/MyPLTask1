package eu.rawora.playLegendTask.model;

import java.util.UUID;

public class PlayerGroupInfo {
    private final UUID playerUUID;
    private String groupName;
    // null bedeutet, die Zuweisung ist permanent.
    private Long expiryTime;

    /**
     * Konstruktor für Spieler-Gruppen-Informationen.
     * @param playerUUID Die UUID des Spielers.
     * @param groupName Der Name der zugewiesenen Gruppe.
     * @param expiryTime Der Zeitpunkt (ms), an dem die Zuweisung abläuft, oder einfach null für permanent.
     */
    public PlayerGroupInfo(UUID playerUUID, String groupName, Long expiryTime) {
        this.playerUUID = playerUUID;
        this.groupName = groupName;
        this.expiryTime = expiryTime;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getGroupName() {
        return groupName;
    }

    public Long getExpiryTime() {
        return expiryTime;
    }

    /**
     * Prüft, ob die Gruppenzuweisung permanent ist.
     * @return true, wenn permanent (expiryTime ist null), sonst false.
     */
    public boolean isPermanent() {
        return expiryTime == null;
    }

    /**
     * Prüft, ob eine temporäre Gruppenzuweisung bereits abgelaufen ist.
     * Permanente Zuweisungen gelten nie als abgelaufen.
     * @return true, wenn die Zuweisung temporär ist UND die Ablaufzeit erreicht oder überschritten wurde.
     */
    public boolean hasExpired() {
        if (isPermanent()) {
            return false;
        }
        // Temporär -> Prüfe Zeit
        return System.currentTimeMillis() >= expiryTime;
    }

    /**
     * Aktualisiert den Gruppennamen (z.B. wenn Spieler neue Gruppe bekommt).
     * @param groupName Der neue Gruppenname.
     */
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    /**
     * Aktualisiert die Ablaufzeit (z.B. wenn Dauer geändert wird).
     * @param expiryTime Der neue Ablaufzeitpunkt (ms) oder null für permanent.
     */
    public void setExpiryTime(Long expiryTime) {
        this.expiryTime = expiryTime;
    }

    @Override
    public String toString() {
        return "PlayerGroupInfo{" +
               "playerUUID=" + playerUUID +
               ", groupName='" + groupName + '\'' +
               ", expiryTime=" + (expiryTime == null ? "Permanent" : expiryTime) +
               '}';
    }
}