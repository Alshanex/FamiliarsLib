package net.alshanex.familiarslib.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.alshanex.familiarslib.registry.CriteriaTriggersRegistry;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class SimpleAdvancementTrigger extends SimpleCriterionTrigger<SimpleAdvancementTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, (instance) -> true);
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player))
                .apply(instance, TriggerInstance::new));

        public static Criterion<TriggerInstance> tameArchmage() {
            return CriteriaTriggersRegistry.TAMING_ARCHMAGE_TRIGGER.get().createCriterion(new TriggerInstance(Optional.empty()));
        }

        public static Criterion<TriggerInstance> tameCleric() {
            return CriteriaTriggersRegistry.TAMING_CLERIC_TRIGGER.get().createCriterion(new TriggerInstance(Optional.empty()));
        }

        public static Criterion<TriggerInstance> tameDruid() {
            return CriteriaTriggersRegistry.TAMING_DRUID_TRIGGER.get().createCriterion(new TriggerInstance(Optional.empty()));
        }

        public static Criterion<TriggerInstance> tameHunter() {
            return CriteriaTriggersRegistry.TAMING_HUNTER_TRIGGER.get().createCriterion(new TriggerInstance(Optional.empty()));
        }

        public static Criterion<TriggerInstance> tameIllusionist() {
            return CriteriaTriggersRegistry.TAMING_ILLUSIONIST_TRIGGER.get().createCriterion(new TriggerInstance(Optional.empty()));
        }

        public static Criterion<TriggerInstance> tameMage() {
            return CriteriaTriggersRegistry.TAMING_MAGE_TRIGGER.get().createCriterion(new TriggerInstance(Optional.empty()));
        }

        public static Criterion<TriggerInstance> tameNecromancer() {
            return CriteriaTriggersRegistry.TAMING_NECROMANCER_TRIGGER.get().createCriterion(new TriggerInstance(Optional.empty()));
        }

        public static Criterion<TriggerInstance> tamePlague() {
            return CriteriaTriggersRegistry.TAMING_PLAGUE_TRIGGER.get().createCriterion(new TriggerInstance(Optional.empty()));
        }

        public static Criterion<TriggerInstance> tameScorcher() {
            return CriteriaTriggersRegistry.TAMING_SCORCHER_TRIGGER.get().createCriterion(new TriggerInstance(Optional.empty()));
        }

        public static Criterion<TriggerInstance> tameSummoner() {
            return CriteriaTriggersRegistry.TAMING_SUMMONER_TRIGGER.get().createCriterion(new TriggerInstance(Optional.empty()));
        }

        public static Criterion<TriggerInstance> tameBard() {
            return CriteriaTriggersRegistry.TAMING_BARD_TRIGGER.get().createCriterion(new TriggerInstance(Optional.empty()));
        }

        public static Criterion<TriggerInstance> tameFrostling() {
            return CriteriaTriggersRegistry.TAMING_FROSTLING_TRIGGER.get().createCriterion(new TriggerInstance(Optional.empty()));
        }

        public static Criterion<TriggerInstance> consumableUse() {
            return CriteriaTriggersRegistry.CONSUMABLE_TRIGGER.get().createCriterion(new TriggerInstance(Optional.empty()));
        }

        public static Criterion<TriggerInstance> shardUse() {
            return CriteriaTriggersRegistry.SHARD_TRIGGER.get().createCriterion(new TriggerInstance(Optional.empty()));
        }

        public static Criterion<TriggerInstance> illusionistReveal() {
            return CriteriaTriggersRegistry.ILLUSIONIST_REVEAL_TRIGGER.get().createCriterion(new TriggerInstance(Optional.empty()));
        }
    }
}
