package net.alshanex.familiarslib.util.consumables;

public class ConsumableUtils {
    public static float calculateMaxHealthWithModifiers(FamiliarConsumableSystem.ConsumableData consumableData, float baseMaxHealth) {
        int healthBonus = consumableData.getValue(FamiliarConsumableSystem.ConsumableType.HEALTH);

        if (healthBonus > 0) {
            double multiplier = healthBonus / 100.0;
            return (float) (baseMaxHealth * (1.0 + multiplier));
        }

        return baseMaxHealth;
    }
}
