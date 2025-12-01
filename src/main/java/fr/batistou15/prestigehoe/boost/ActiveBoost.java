package fr.batistou15.prestigehoe.boost;

public class ActiveBoost {

    private final BoostDefinition definition;
    private final long startMillis;
    private final long endMillis;

    public ActiveBoost(BoostDefinition definition, long startMillis, long endMillis) {
        this.definition = definition;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
    }

    public BoostDefinition getDefinition() {
        return definition;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > endMillis;
    }

    public long getRemainingSeconds() {
        long now = System.currentTimeMillis();
        if (now >= endMillis) return 0L;
        return (endMillis - now) / 1000L;
    }
}
