package net.alshanex.familiarslib.util.familiars;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;

public class FamiliarAnimationUtils {
    public static boolean isLongAnimCast(AbstractSpell spell, int spellLevel){
        return spell.getCastTime(spellLevel) > 15;
        /*
        return spell == SpellRegistry.LIGHTNING_BOLT_SPELL.get() || spell == SpellRegistry.SHOCKWAVE_SPELL.get()
                || spell == SpellRegistry.RAISE_DEAD_SPELL.get() || spell == SpellRegistry.SCULK_TENTACLES_SPELL.get()
                || spell == PetSpellRegistry.SUMMON_SHADOW.get() || spell == SpellRegistry.MAGIC_ARROW_SPELL.get()
                || spell == SpellRegistry.POISON_ARROW_SPELL.get() || spell == SpellRegistry.ARROW_VOLLEY_SPELL.get()
                || spell == SpellRegistry.FIREFLY_SWARM_SPELL.get() || spell == SpellRegistry.CHAIN_CREEPER_SPELL.get()
                || spell == SpellRegistry.BLIGHT_SPELL.get() || spell == SpellRegistry.SLOW_SPELL.get()
                || spell == SpellRegistry.INVISIBILITY_SPELL.get() || spell == SpellRegistry.ROOT_SPELL.get()
                || spell == SpellRegistry.CHAIN_CREEPER_SPELL.get() || spell == SpellRegistry.FANG_STRIKE_SPELL.get()
                || spell == SpellRegistry.FANG_WARD_SPELL.get() || spell == SpellRegistry.SUMMON_VEX_SPELL.get()
                || spell == SpellRegistry.GUST_SPELL.get() || spell == SpellRegistry.FIREBALL_SPELL.get()
                || spell == SpellRegistry.MAGMA_BOMB_SPELL.get() || spell == SpellRegistry.CLEANSE_SPELL.get()
                || spell == SpellRegistry.FORTIFY_SPELL.get() || spell == SpellRegistry.GREATER_HEAL_SPELL.get()
                || spell == SpellRegistry.HASTE_SPELL.get() || spell == SpellRegistry.WISP_SPELL.get()
                || spell == SpellRegistry.ACID_ORB_SPELL.get() || spell == SpellRegistry.POISON_SPLASH_SPELL.get()
                || spell == PetSpellRegistry.EXPLOSION_MELODY.get() || spell == PetSpellRegistry.BIRDS_SPELL.get()
                || spell == PetSpellRegistry.VIBRATION_SPELL.get() || spell == SpellRegistry.FIRE_ARROW_SPELL.get();

         */
    }
}
