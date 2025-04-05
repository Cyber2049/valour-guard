package com.nameless.valourguard;

import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.property.AnimationEvent;
import yesman.epicfight.api.animation.property.AnimationProperty;
import yesman.epicfight.api.animation.property.MoveCoordFunctions;
import yesman.epicfight.api.animation.types.EntityState;
import yesman.epicfight.api.animation.types.GuardAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.data.reloader.SkillManager;
import yesman.epicfight.api.forgeevent.AnimationRegistryEvent;
import yesman.epicfight.api.forgeevent.SkillBuildEvent;
import yesman.epicfight.api.forgeevent.WeaponCapabilityPresetRegistryEvent;
import yesman.epicfight.api.utils.math.ValueModifier;
import yesman.epicfight.gameasset.*;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.particle.EpicFightParticles;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponCapability;
import yesman.epicfight.world.damagesource.StunType;

import java.util.function.Function;

import static com.nameless.valourguard.ClientUtil.dust;

@Mod(ValourGuard.MOD_ID)
public class ValourGuard
{
    public static final String MOD_ID = "valourguard";
    public static StaticAnimation COLLISION_RIGHT;
    public static StaticAnimation COLLISION_LEFT;
    public static StaticAnimation FIST_GUARD;
    public static StaticAnimation FIST_GUARD_HIT;
    public static StaticAnimation FIST_COLLISION;
    public static Skill BRAVE_GUARD;

    public ValourGuard()
    {

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::registerAnimations);
        this.registerSkills();
        bus.addListener(this::register);
        MinecraftForge.EVENT_BUS.addListener(this::buildSkillEvent);
    }

    private void registerAnimations(AnimationRegistryEvent event) {
        event.getRegistryMap().put(ValourGuard.MOD_ID, this::build);
    }

    private void build() {
        HumanoidArmature biped = Armatures.BIPED;
        COLLISION_RIGHT = new CollisionAnimation(0.01F,0.07F,0.08F, 0.2F, 0.8F, ColliderPreset.FIST_FIXED, biped.rootJoint, "biped/skill/collision_right",biped)
                .addProperty(AnimationProperty.AttackPhaseProperty.IMPACT_MODIFIER, ValueModifier.setter(1F))
                .addProperty(AnimationProperty.AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(0.1F))
                .addProperty(AnimationProperty.AttackPhaseProperty.STUN_TYPE, StunType.SHORT)
                .addProperty(AnimationProperty.AttackPhaseProperty.PARTICLE, EpicFightParticles.HIT_BLUNT)
                .addProperty(AnimationProperty.AttackPhaseProperty.SWING_SOUND, EpicFightSounds.ROLL)
                .addProperty(AnimationProperty.AttackPhaseProperty.HIT_SOUND, EpicFightSounds.BLUNT_HIT_HARD)
                .addProperty(AnimationProperty.ActionAnimationProperty.RESET_PLAYER_COMBO_COUNTER, false)
                .addProperty(AnimationProperty.ActionAnimationProperty.COORD_SET_BEGIN, MoveCoordFunctions.TRACE_LOCROT_TARGET)
                .addProperty(AnimationProperty.StaticAnimationProperty.PLAY_SPEED_MODIFIER, FIX_SPEED)
                .newTimePair(0F, 0.6F)
                .addStateRemoveOld(EntityState.CAN_SKILL_EXECUTION, false)
                .newTimePair(0F, 0.6F)
                .addStateRemoveOld(EntityState.CAN_BASIC_ATTACK, false)
                .addEvents(AnimationEvent.TimeStampedEvent.create(0.2F, DUST, AnimationEvent.Side.CLIENT));
        COLLISION_LEFT = new CollisionAnimation(0.01F,0.05F,0.06F, 0.25F, 1.05F, ColliderPreset.FIST_FIXED, biped.rootJoint, "biped/skill/collision_left",biped)
                .addProperty(AnimationProperty.AttackPhaseProperty.IMPACT_MODIFIER, ValueModifier.setter(1F))
                .addProperty(AnimationProperty.AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(0.1F))
                .addProperty(AnimationProperty.AttackPhaseProperty.STUN_TYPE, StunType.SHORT)
                .addProperty(AnimationProperty.AttackPhaseProperty.PARTICLE, EpicFightParticles.HIT_BLUNT)
                .addProperty(AnimationProperty.AttackPhaseProperty.SWING_SOUND, EpicFightSounds.ROLL)
                .addProperty(AnimationProperty.AttackPhaseProperty.HIT_SOUND, EpicFightSounds.BLUNT_HIT_HARD)
                .addProperty(AnimationProperty.ActionAnimationProperty.RESET_PLAYER_COMBO_COUNTER, false)
                .addProperty(AnimationProperty.ActionAnimationProperty.COORD_SET_BEGIN, MoveCoordFunctions.TRACE_LOCROT_TARGET)
                .addProperty(AnimationProperty.StaticAnimationProperty.PLAY_SPEED_MODIFIER, FIX_SPEED)
                .newTimePair(0F, 0.75F)
                .addStateRemoveOld(EntityState.CAN_SKILL_EXECUTION, false)
                .newTimePair(0F, 0.8F)
                .addStateRemoveOld(EntityState.CAN_BASIC_ATTACK, false)
                .addEvents(AnimationEvent.TimeStampedEvent.create(0.25F, DUST, AnimationEvent.Side.CLIENT));
        FIST_GUARD = new StaticAnimation(true, "biped/skill/guard_fist", biped);
        FIST_GUARD_HIT = new GuardAnimation(0.05F, "biped/skill/guard_fist_hit", biped);
        FIST_COLLISION = new CollisionAnimation(0.01F,0.2F,0.21F, 0.35F, 1.0F, ColliderPreset.FIST_FIXED, biped.rootJoint, "biped/skill/collision_fist",biped)
                .addProperty(AnimationProperty.AttackPhaseProperty.IMPACT_MODIFIER, ValueModifier.setter(1F))
                .addProperty(AnimationProperty.AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(0.1F))
                .addProperty(AnimationProperty.AttackPhaseProperty.STUN_TYPE, StunType.SHORT)
                .addProperty(AnimationProperty.AttackPhaseProperty.PARTICLE, EpicFightParticles.HIT_BLUNT)
                .addProperty(AnimationProperty.AttackPhaseProperty.SWING_SOUND, EpicFightSounds.ROLL)
                .addProperty(AnimationProperty.AttackPhaseProperty.HIT_SOUND, EpicFightSounds.BLUNT_HIT_HARD)
                .addProperty(AnimationProperty.ActionAnimationProperty.RESET_PLAYER_COMBO_COUNTER, false)
                .addProperty(AnimationProperty.ActionAnimationProperty.COORD_SET_BEGIN, MoveCoordFunctions.TRACE_LOCROT_TARGET)
                .addProperty(AnimationProperty.StaticAnimationProperty.PLAY_SPEED_MODIFIER, (self, entitypatch, speed, elapsedTime) -> 1.0F)
                .newTimePair(0F, 0.75F)
                .addStateRemoveOld(EntityState.CAN_SKILL_EXECUTION, false)
                .newTimePair(0F, 0.8F)
                .addStateRemoveOld(EntityState.CAN_BASIC_ATTACK, false)
                .addEvents(AnimationEvent.TimeStampedEvent.create(0.25F, DUST, AnimationEvent.Side.CLIENT));
    }

    public final AnimationEvent.AnimationEventConsumer DUST = (entitypatch, animation, params) -> {
        if(!entitypatch.isLogicalClient()) return;
        dust(entitypatch);
    };

    public static final AnimationProperty.PlaybackTimeModifier FIX_SPEED = (self, entitypatch, speed, elapsedTime) -> {
        if(elapsedTime < 0.07) {
            return 0.5F;
        }
        return 1.05F;
    };

    private void registerSkills(){
        SkillManager.register(BraveGuard::new, BraveGuard.createBraveGuardBuilder() ,ValourGuard.MOD_ID,"brave_guard");
    }

    private void buildSkillEvent(SkillBuildEvent onBuild) {
        BRAVE_GUARD = onBuild.build(ValourGuard.MOD_ID, "brave_guard");
    }

    public void register(WeaponCapabilityPresetRegistryEvent event) {
        event.getTypeEntry().put("fist", FIST);
    }

    public static final Function<Item, CapabilityItem.Builder> FIST = (item) -> WeaponCapability.builder()
            .newStyleCombo(CapabilityItem.Styles.ONE_HAND, Animations.FIST_AUTO1, Animations.FIST_AUTO2, Animations.FIST_AUTO3, Animations.FIST_DASH, Animations.FIST_AIR_SLASH)
            .innateSkill(CapabilityItem.Styles.ONE_HAND, (itemstack) -> EpicFightSkills.RELENTLESS_COMBO)
            .category(CapabilityItem.WeaponCategories.FIST)
            .livingMotionModifier(CapabilityItem.Styles.ONE_HAND, LivingMotions.BLOCK, ValourGuard.FIST_GUARD)
            .constructor(NewGloveCapability::new);
}
