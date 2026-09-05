package net.vyrim.core.module.advancements;

/**
 * Immutable record representing the raw trigger block of an advancement.
 *
 * @param type   the trigger type identifier (e.g. JOIN_SERVER, BREAK_BLOCK)
 * @param target the target qualifier (e.g. material name, entity name, stat key), or null
 * @param amount the target threshold, defaults to 1 if not specified or <= 0
 */
public record AdvancementTriggerData(
        String type,
        String target,
        int amount
) {
    public AdvancementTriggerData(String type, String target, int amount) {
        this.type = type != null ? type : "UNKNOWN";
        this.target = target;
        this.amount = amount > 0 ? amount : 1;
    }
}
