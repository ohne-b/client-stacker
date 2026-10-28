package dev.ohneb.clientstacker.core;

/** Zero caps are disabled. A retained value is measured before the proposed purchase. */
public record TradeLimits(long spendingCap, long retainedValue, long outputTarget) {
    public TradeLimits {
        if (spendingCap < 0 || retainedValue < 0 || outputTarget < 0) {
            throw new IllegalArgumentException("Negative trade limit");
        }
    }

    public enum Stop { NONE, SPENDING_CAP, RESERVE, OUTPUT_TARGET }

    public Stop check(long spent, long price, long availableValue, long ownedOutput, long outputCount) {
        if (spent < 0 || price < 0 || availableValue < 0 || ownedOutput < 0 || outputCount < 1) {
            throw new IllegalArgumentException("Invalid trade counts");
        }
        if (spendingCap > 0 && (spent > spendingCap || price > spendingCap - spent)) return Stop.SPENDING_CAP;
        if (availableValue < retainedValue || price > availableValue - retainedValue) return Stop.RESERVE;
        if (outputTarget > 0 && (ownedOutput >= outputTarget || outputCount > outputTarget - ownedOutput)) {
            return Stop.OUTPUT_TARGET;
        }
        return Stop.NONE;
    }
}
