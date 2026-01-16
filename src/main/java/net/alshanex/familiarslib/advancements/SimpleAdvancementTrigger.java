package net.alshanex.familiarslib.advancements;

import com.google.gson.JsonObject;
import net.alshanex.familiarslib.registry.CriteriaTriggersRegistry;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class SimpleAdvancementTrigger extends SimpleCriterionTrigger<SimpleAdvancementTrigger.TriggerInstance> {

    private final ResourceLocation id;

    public SimpleAdvancementTrigger(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
        return new TriggerInstance(this.id, player);
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, (instance) -> true);
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {

        public TriggerInstance(ResourceLocation id, ContextAwarePredicate player) {
            super(id, player);
        }

        public static TriggerInstance tameArchmage() {
            return new TriggerInstance(CriteriaTriggersRegistry.TAMING_ARCHMAGE_TRIGGER.getId(), ContextAwarePredicate.ANY);
        }

        public static TriggerInstance tameCleric() {
            return new TriggerInstance(CriteriaTriggersRegistry.TAMING_CLERIC_TRIGGER.getId(), ContextAwarePredicate.ANY);
        }

        public static TriggerInstance tameDruid() {
            return new TriggerInstance(CriteriaTriggersRegistry.TAMING_DRUID_TRIGGER.getId(), ContextAwarePredicate.ANY);
        }

        public static TriggerInstance tameHunter() {
            return new TriggerInstance(CriteriaTriggersRegistry.TAMING_HUNTER_TRIGGER.getId(), ContextAwarePredicate.ANY);
        }

        public static TriggerInstance tameIllusionist() {
            return new TriggerInstance(CriteriaTriggersRegistry.TAMING_ILLUSIONIST_TRIGGER.getId(), ContextAwarePredicate.ANY);
        }

        public static TriggerInstance tameMage() {
            return new TriggerInstance(CriteriaTriggersRegistry.TAMING_MAGE_TRIGGER.getId(), ContextAwarePredicate.ANY);
        }

        public static TriggerInstance tameNecromancer() {
            return new TriggerInstance(CriteriaTriggersRegistry.TAMING_NECROMANCER_TRIGGER.getId(), ContextAwarePredicate.ANY);
        }

        public static TriggerInstance tamePlague() {
            return new TriggerInstance(CriteriaTriggersRegistry.TAMING_PLAGUE_TRIGGER.getId(), ContextAwarePredicate.ANY);
        }

        public static TriggerInstance tameScorcher() {
            return new TriggerInstance(CriteriaTriggersRegistry.TAMING_SCORCHER_TRIGGER.getId(), ContextAwarePredicate.ANY);
        }

        public static TriggerInstance tameSummoner() {
            return new TriggerInstance(CriteriaTriggersRegistry.TAMING_SUMMONER_TRIGGER.getId(), ContextAwarePredicate.ANY);
        }

        public static TriggerInstance tameBard() {
            return new TriggerInstance(CriteriaTriggersRegistry.TAMING_BARD_TRIGGER.getId(), ContextAwarePredicate.ANY);
        }

        public static TriggerInstance tameFrostling() {
            return new TriggerInstance(CriteriaTriggersRegistry.TAMING_FROSTLING_TRIGGER.getId(), ContextAwarePredicate.ANY);
        }

        public static TriggerInstance consumableUse() {
            return new TriggerInstance(CriteriaTriggersRegistry.CONSUMABLE_TRIGGER.getId(), ContextAwarePredicate.ANY);
        }

        public static TriggerInstance shardUse() {
            return new TriggerInstance(CriteriaTriggersRegistry.SHARD_TRIGGER.getId(), ContextAwarePredicate.ANY);
        }

        public static TriggerInstance illusionistReveal() {
            return new TriggerInstance(CriteriaTriggersRegistry.ILLUSIONIST_REVEAL_TRIGGER.getId(), ContextAwarePredicate.ANY);
        }
    }
}
