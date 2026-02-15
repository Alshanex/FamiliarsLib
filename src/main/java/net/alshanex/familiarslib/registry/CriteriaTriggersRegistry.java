package net.alshanex.familiarslib.registry;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.advancements.SimpleAdvancementTrigger;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CriteriaTriggersRegistry {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, FamiliarsLib.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> TAMING_ARCHMAGE_TRIGGER = TRIGGERS.register("archmage_taming", SimpleAdvancementTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> TAMING_CLERIC_TRIGGER = TRIGGERS.register("cleric_taming", SimpleAdvancementTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> TAMING_DRUID_TRIGGER = TRIGGERS.register("druid_taming", SimpleAdvancementTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> TAMING_HUNTER_TRIGGER = TRIGGERS.register("hunter_taming", SimpleAdvancementTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> TAMING_ILLUSIONIST_TRIGGER = TRIGGERS.register("illusionist_taming", SimpleAdvancementTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> TAMING_MAGE_TRIGGER = TRIGGERS.register("mage_taming", SimpleAdvancementTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> TAMING_NECROMANCER_TRIGGER = TRIGGERS.register("necromancer_taming", SimpleAdvancementTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> TAMING_PLAGUE_TRIGGER = TRIGGERS.register("plague_taming", SimpleAdvancementTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> TAMING_SCORCHER_TRIGGER = TRIGGERS.register("scorcher_taming", SimpleAdvancementTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> TAMING_SUMMONER_TRIGGER = TRIGGERS.register("summoner_taming", SimpleAdvancementTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> TAMING_BARD_TRIGGER = TRIGGERS.register("bard_taming", SimpleAdvancementTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> TAMING_FROSTLING_TRIGGER = TRIGGERS.register("frostling_taming", SimpleAdvancementTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> TAMING_DRAGOON_TRIGGER = TRIGGERS.register("dragoon_taming", SimpleAdvancementTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> CONSUMABLE_TRIGGER = TRIGGERS.register("consumable_use", SimpleAdvancementTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> SHARD_TRIGGER = TRIGGERS.register("shard_use", SimpleAdvancementTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> ILLUSIONIST_REVEAL_TRIGGER = TRIGGERS.register("illusionist_reveal", SimpleAdvancementTrigger::new);
}
