package net.alshanex.familiarslib.registry;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.advancements.SimpleAdvancementTrigger;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;

public class CriteriaTriggersRegistry {

    public static final SimpleAdvancementTrigger TAMING_ARCHMAGE_TRIGGER = new SimpleAdvancementTrigger(new ResourceLocation(FamiliarsLib.MODID, "archmage_taming"));
    public static final SimpleAdvancementTrigger TAMING_CLERIC_TRIGGER = new SimpleAdvancementTrigger(new ResourceLocation(FamiliarsLib.MODID, "cleric_taming"));
    public static final SimpleAdvancementTrigger TAMING_DRUID_TRIGGER = new SimpleAdvancementTrigger(new ResourceLocation(FamiliarsLib.MODID, "druid_taming"));
    public static final SimpleAdvancementTrigger TAMING_HUNTER_TRIGGER = new SimpleAdvancementTrigger(new ResourceLocation(FamiliarsLib.MODID, "hunter_taming"));
    public static final SimpleAdvancementTrigger TAMING_ILLUSIONIST_TRIGGER = new SimpleAdvancementTrigger(new ResourceLocation(FamiliarsLib.MODID, "illusionist_taming"));
    public static final SimpleAdvancementTrigger TAMING_MAGE_TRIGGER = new SimpleAdvancementTrigger(new ResourceLocation(FamiliarsLib.MODID, "mage_taming"));
    public static final SimpleAdvancementTrigger TAMING_NECROMANCER_TRIGGER = new SimpleAdvancementTrigger(new ResourceLocation(FamiliarsLib.MODID, "necromancer_taming"));
    public static final SimpleAdvancementTrigger TAMING_PLAGUE_TRIGGER = new SimpleAdvancementTrigger(new ResourceLocation(FamiliarsLib.MODID, "plague_taming"));
    public static final SimpleAdvancementTrigger TAMING_SCORCHER_TRIGGER = new SimpleAdvancementTrigger(new ResourceLocation(FamiliarsLib.MODID, "scorcher_taming"));
    public static final SimpleAdvancementTrigger TAMING_SUMMONER_TRIGGER = new SimpleAdvancementTrigger(new ResourceLocation(FamiliarsLib.MODID, "summoner_taming"));
    public static final SimpleAdvancementTrigger TAMING_BARD_TRIGGER = new SimpleAdvancementTrigger(new ResourceLocation(FamiliarsLib.MODID, "bard_taming"));
    public static final SimpleAdvancementTrigger TAMING_FROSTLING_TRIGGER = new SimpleAdvancementTrigger(new ResourceLocation(FamiliarsLib.MODID, "frostling_taming"));

    public static final SimpleAdvancementTrigger CONSUMABLE_TRIGGER = new SimpleAdvancementTrigger(new ResourceLocation(FamiliarsLib.MODID, "consumable_use"));
    public static final SimpleAdvancementTrigger SHARD_TRIGGER = new SimpleAdvancementTrigger(new ResourceLocation(FamiliarsLib.MODID, "shard_use"));
    public static final SimpleAdvancementTrigger ILLUSIONIST_REVEAL_TRIGGER = new SimpleAdvancementTrigger(new ResourceLocation(FamiliarsLib.MODID, "illusionist_reveal"));

    public static void register() {
        CriteriaTriggers.register(TAMING_ARCHMAGE_TRIGGER);
        CriteriaTriggers.register(TAMING_CLERIC_TRIGGER);
        CriteriaTriggers.register(TAMING_DRUID_TRIGGER);
        CriteriaTriggers.register(TAMING_HUNTER_TRIGGER);
        CriteriaTriggers.register(TAMING_ILLUSIONIST_TRIGGER);
        CriteriaTriggers.register(TAMING_MAGE_TRIGGER);
        CriteriaTriggers.register(TAMING_NECROMANCER_TRIGGER);
        CriteriaTriggers.register(TAMING_PLAGUE_TRIGGER);
        CriteriaTriggers.register(TAMING_SCORCHER_TRIGGER);
        CriteriaTriggers.register(TAMING_SUMMONER_TRIGGER);
        CriteriaTriggers.register(TAMING_BARD_TRIGGER);
        CriteriaTriggers.register(TAMING_FROSTLING_TRIGGER);
        CriteriaTriggers.register(CONSUMABLE_TRIGGER);
        CriteriaTriggers.register(SHARD_TRIGGER);
        CriteriaTriggers.register(ILLUSIONIST_REVEAL_TRIGGER);
    }
}
