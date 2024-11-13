package com.nameless.valourguard;


import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import com.nameless.impactful.config.CommonConfig;
import com.nameless.impactful.network.CameraShake;
import com.nameless.impactful.network.NetWorkManger;
import io.netty.buffer.Unpooled;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.player.Input;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.BasicAttackAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.utils.AttackResult;
import yesman.epicfight.client.ClientEngine;
import yesman.epicfight.client.gui.BattleModeGui;
import yesman.epicfight.gameasset.EpicFightSkills;
import yesman.epicfight.network.client.CPExecuteSkill;
import yesman.epicfight.particle.EpicFightParticles;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.SkillCategories;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.SkillDataManager;
import yesman.epicfight.skill.guard.GuardSkill;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;
import yesman.epicfight.world.damagesource.StunType;
import yesman.epicfight.world.entity.eventlistener.ComboCounterHandleEvent;
import yesman.epicfight.world.entity.eventlistener.HurtEvent;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener;

import javax.annotation.Nullable;
import java.util.UUID;

import static com.nameless.toybox.common.attribute.ai.ToyBoxAttributes.BLOCK_RATE;

public class BraveGuard extends GuardSkill {
    private boolean successBlock = false;
    private static final UUID EXTRA_EVENT_UUID = UUID.fromString("c4b1259f-295b-47c7-87ae-84c6232eeeb1");
    private static final SkillDataManager.SkillDataKey<Integer> combocounter = SkillDataManager.SkillDataKey.createDataKey(SkillDataManager.ValueType.INTEGER);
    private static final SkillDataManager.SkillDataKey<Boolean> BRAVE_ACTIVE = SkillDataManager.SkillDataKey.createDataKey(SkillDataManager.ValueType.BOOLEAN);
    private static final SkillDataManager.SkillDataKey<Integer> BRAVE = SkillDataManager.SkillDataKey.createDataKey(SkillDataManager.ValueType.INTEGER);

    private final StaticAnimation[] animations = {ValourGuard.COLLISION_RIGHT, ValourGuard.COLLISION_LEFT};
    private static final ResourceLocation UI = new ResourceLocation(ValourGuard.MOD_ID, "textures/gui/skills/brave_guard_ui.png");

    private float superiorPenalizer;
    private float collisionDamageReducer;
    private float braveDamageReducer;
    private float recoveryMultiplier;
    private int collisionBrave;
    private int guardBrave;
    private int braveCostPerSecond;
    private int braveCostPerHit;
    private boolean initiate = false;
    @OnlyIn(Dist.CLIENT)
    private boolean canExecute = true;
    public static Builder createBraveGuardBuilder() {
        return GuardSkill.createGuardBuilder()
                .setResource(Resource.STAMINA);
    }
    public BraveGuard(Builder builder) {
        super(builder);
    }

    @Override
    public void setParams(CompoundTag parameters) {
        super.setParams(parameters);
        this.superiorPenalizer = parameters.getFloat("superior_penalizer");
        this.collisionDamageReducer = parameters.getFloat("collision_damage_reducer");
        this.braveDamageReducer = parameters.getFloat("brave_damage_reducer");
        this.recoveryMultiplier = parameters.getFloat("recovery_multi");
        this.collisionBrave = parameters.getInt("collision_brave");
        this.guardBrave = parameters.getInt("block_brave");
        this.braveCostPerSecond = parameters.getInt("brave_cost_per_second");
        this.braveCostPerHit = parameters.getInt("brave_cost_per_hit");
    }

    @Override
    public void onInitiate(SkillContainer container) {
        super.onInitiate(container);
        container.getDataManager().registerData(BRAVE);
        container.getDataManager().registerData(BRAVE_ACTIVE);
        container.getDataManager().registerData(combocounter);
        this.initiate = true;


        //取代父类事件
        PlayerPatch<?> executer = container.getExecuter();
        executer.getEventListener().addEventListener(PlayerEventListener.EventType.CLIENT_ITEM_USE_EVENT, EVENT_UUID, (event) -> {
            if(!this.initiate) return;
            CapabilityItem itemCapability = event.getPlayerPatch().getHoldingItemCapability(InteractionHand.MAIN_HAND);
            //满足铁山靠条件，发包铁山靠
            if (this.correctWeaponCategoryAndStyle(executer) && this.isExecutableState(event.getPlayerPatch()) && this.canExecute) {
                DynamicAnimation animation = executer.getAnimator().getPlayerFor(null).getAnimation();
                if(container.getDataManager().getDataValue(combocounter) > 0 && executer.getEntityState().getLevel() != 1  && this.resourcePredicate(executer) && !(animation.equals(ValourGuard.COLLISION_RIGHT) || animation.equals(ValourGuard.COLLISION_LEFT))){
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    //是否根据连击数区分左右
                    buf.writeBoolean(true);
                    CPExecuteSkill packet = new CPExecuteSkill(executer.getSkill(this).getSlotId(), CPExecuteSkill.WorkType.ACTIVATE, buf);
                    ClientEngine.getInstance().controllEngine.addPacketToSend(packet);
                    executer.getOriginal().stopUsingItem();
                    event.setCanceled(true);
                    //满足防御条件，防御
                } else if (executer.getEntityState().canUseSkill()) {
                    event.getPlayerPatch().getOriginal().startUsingItem(InteractionHand.MAIN_HAND);
                }
            }
            if (!this.correctWeaponCategoryAndStyle(executer) && this.isHoldingWeaponAvailable(event.getPlayerPatch(), itemCapability, BlockType.GUARD) && super.isExecutableState(event.getPlayerPatch())) {
                event.getPlayerPatch().getOriginal().startUsingItem(InteractionHand.MAIN_HAND);
            }
        });

        //连击计数
        executer.getEventListener().addEventListener(PlayerEventListener.EventType.COMBO_COUNTER_HANDLE_EVENT, EVENT_UUID, (event) -> {
            if(!this.initiate) return;
           if(event.getCausal().equals(ComboCounterHandleEvent.Causal.ACTION_ANIMATION_RESET)){
               if(event.getAnimation() instanceof BasicAttackAnimation){
                   container.getDataManager().setDataSync(combocounter, container.getDataManager().getDataValue(combocounter) + 1, (ServerPlayer)executer.getOriginal());
               } else container.getDataManager().setDataSync(combocounter, 0, (ServerPlayer)executer.getOriginal());
           }
           if(event.getCausal().equals(ComboCounterHandleEvent.Causal.TIME_EXPIRED_RESET)){
               container.getDataManager().setDataSync(combocounter, 0, (ServerPlayer)executer.getOriginal());
           }
        });

        //防御锁定移动
        executer.getEventListener().addEventListener(PlayerEventListener.EventType.MOVEMENT_INPUT_EVENT, EVENT_UUID, (event) -> {
            if(!this.initiate) return;
            if (executer.getOriginal().isUsingItem() && this.correctWeaponCategoryAndStyle(executer)) {
                event.getPlayerPatch().getOriginal().setSprinting(false);
                Input input = event.getMovementInput();
                input.forwardImpulse = 0.0F;
                input.leftImpulse = 0.0F;
                input.up = false;
                input.down = false;
                input.left = false;
                input.right = false;
                input.jumping = false;
                input.shiftKeyDown = false;
            }

            if(!this.canExecute && event.getPlayerPatch().getEntityState().getLevel() == 0 && !event.getPlayerPatch().getOriginal().isUsingItem()){
                this.canExecute = true;
            }
        });


        executer.getEventListener().addEventListener(PlayerEventListener.EventType.HURT_EVENT_PRE, EXTRA_EVENT_UUID, (event) -> {
            if(!this.initiate) return;
            DynamicAnimation animation = event.getPlayerPatch().getAnimator().getPlayerFor(null).getAnimation();
            int attack_level = event.getPlayerPatch().getEntityState().getLevel();
            LivingEntityPatch<?> attackerPatch = EpicFightCapabilities.getEntityPatch(event.getDamageSource().getEntity(), LivingEntityPatch.class);
            //成功铁山靠判定减伤+霸体
            if((animation.equals(ValourGuard.COLLISION_LEFT) || animation.equals(ValourGuard.COLLISION_RIGHT)) && attack_level == 1){
                successBlock = true;
                if(event.getDamageSource() instanceof EpicFightDamageSource epicFightDamageSource){
                    epicFightDamageSource.setStunType(StunType.NONE);
                }
                event.setParried(true);
                this.processDamage(event.getPlayerPatch(), event.getDamageSource(), AttackResult.ResultType.SUCCESS, this.collisionDamageReducer * event.getAmount(), attackerPatch);
                event.setResult(AttackResult.ResultType.BLOCKED);
                event.setCanceled(true);

            }

            //勇气状态减伤+霸体
            if(container.getDataManager().getDataValue(BRAVE_ACTIVE) && animation instanceof AttackAnimation && attack_level > 0 && attack_level < 3){
                if(!this.initiate) return;
                if(event.getDamageSource() instanceof EpicFightDamageSource epicFightDamageSource && (epicFightDamageSource.getStunType().equals(StunType.SHORT) || epicFightDamageSource.getStunType().equals(StunType.HOLD))){
                    epicFightDamageSource.setStunType(StunType.NONE);
                }
                this.processDamage(event.getPlayerPatch(), event.getDamageSource(), AttackResult.ResultType.SUCCESS, this.braveDamageReducer * event.getAmount(), attackerPatch);
                event.setResult(AttackResult.ResultType.BLOCKED);
                event.setCanceled(true);
            }
        }, 2);

        executer.getEventListener().addEventListener(PlayerEventListener.EventType.HURT_EVENT_POST, EXTRA_EVENT_UUID, (event) -> {
            if(!this.initiate) return;
            //受伤时候消耗勇气值
            if(!this.successBlock)setBrave(container.getDataManager().getDataValue(BRAVE) - this.braveCostPerHit, container, (ServerPlayer) executer.getOriginal());
        });

        //成功铁山靠的回耐，加勇气值，释放粒子
        executer.getEventListener().addEventListener(PlayerEventListener.EventType.DEALT_DAMAGE_EVENT_POST, EXTRA_EVENT_UUID, (event) -> {
            if(!this.initiate) return;
            StaticAnimation animation = event.getDamageSource().getAnimation();
            if((animation.equals(ValourGuard.COLLISION_RIGHT) || animation.equals(ValourGuard.COLLISION_LEFT)) && successBlock){
                EpicFightParticles.AIR_BURST.get().spawnParticleWithArgument(((ServerLevel)executer.getOriginal().level), executer.getOriginal(), event.getTarget());
                event.getPlayerPatch().setStamina(event.getPlayerPatch().getStamina() + recoveryMultiplier * this.getConsumption());
                setBrave(container.getDataManager().getDataValue(BRAVE) + this.collisionBrave, container, (ServerPlayer) executer.getOriginal());
            }
        });

        //成功铁山靠赋予攻击击退效果
        executer.getEventListener().addEventListener(PlayerEventListener.EventType.DEALT_DAMAGE_EVENT_PRE, EXTRA_EVENT_UUID, (event) -> {
            if(!this.initiate) return;
            StaticAnimation animation = event.getDamageSource().getAnimation();
            if((animation.equals(ValourGuard.COLLISION_RIGHT) || animation.equals(ValourGuard.COLLISION_LEFT)) && successBlock){
                event.getDamageSource().setStunType(StunType.LONG);
                event.getDamageSource().setImpact(5.0F);
            }
        });

        //
        executer.getEventListener().addEventListener(PlayerEventListener.EventType.SKILL_EXECUTE_EVENT, EXTRA_EVENT_UUID, (event) -> {
            if(!this.initiate) return;
            if (container.getExecuter().isLogicalClient()) {
                Skill skill = event.getSkillContainer().getSkill();

                if(skill.getCategory() == SkillCategories.BASIC_ATTACK && event.getPlayerPatch().getOriginal().isUsingItem()){
                    this.canExecute = false;
                }

                if (skill.getCategory() != SkillCategories.WEAPON_INNATE || !this.correctWeaponCategoryAndStyle(executer)) {
                    return;
                }


                if(executer.getOriginal().isUsingItem() && this.resourcePredicate(executer)) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeBoolean(false);
                    CPExecuteSkill packet = new CPExecuteSkill(executer.getSkill(this).getSlotId(), CPExecuteSkill.WorkType.ACTIVATE, buf);
                    ClientEngine.getInstance().controllEngine.addPacketToSend(packet);
                    executer.getOriginal().stopUsingItem();
                    event.setCanceled(true);
                }
            }
        });
    }

    @Override
    public boolean isExecutableState(PlayerPatch<?> executer) {
        return !(executer.isUnstable() || executer.getEntityState().hurt())  && executer.isBattleMode();
    }

    @Override
    public void onRemoved(SkillContainer container) {
        this.initiate = false;
        super.onRemoved(container);
        container.getExecuter().getEventListener().removeListener(PlayerEventListener.EventType.COMBO_COUNTER_HANDLE_EVENT, EXTRA_EVENT_UUID);
        container.getExecuter().getEventListener().removeListener(PlayerEventListener.EventType.MOVEMENT_INPUT_EVENT, EXTRA_EVENT_UUID);
        container.getExecuter().getEventListener().removeListener(PlayerEventListener.EventType.HURT_EVENT_PRE, EXTRA_EVENT_UUID);
        container.getExecuter().getEventListener().removeListener(PlayerEventListener.EventType.HURT_EVENT_POST, EXTRA_EVENT_UUID);
        container.getExecuter().getEventListener().removeListener(PlayerEventListener.EventType.DEALT_DAMAGE_EVENT_PRE, EXTRA_EVENT_UUID);
        container.getExecuter().getEventListener().removeListener(PlayerEventListener.EventType.DEALT_DAMAGE_EVENT_POST, EXTRA_EVENT_UUID);
        container.getExecuter().getEventListener().removeListener(PlayerEventListener.EventType.SKILL_EXECUTE_EVENT, EXTRA_EVENT_UUID);
    }

    @Override
    public void executeOnServer(ServerPlayerPatch executer, FriendlyByteBuf args) {
        if(!this.initiate) return;
        super.executeOnServer(executer, args);
        if(args.readBoolean()) {
            int c = executer.getSkill(this).getDataManager().getDataValue(combocounter)%2;
            executer.playAnimationSynchronized(animations[c], 0.01F);
        } else {
            executer.playAnimationSynchronized(animations[0], 0.05F);
        }
    }

    @Override
    public void guard(SkillContainer container, CapabilityItem itemCapapbility, HurtEvent.Pre event, float knockback, float impact, boolean advanced) {
        if(ModList.get().isLoaded("toybox")) {
            float blockrate = 1F - Math.min((float) event.getPlayerPatch().getOriginal().getAttributeValue(BLOCK_RATE.get()) / 100, 0.9F);
            if (event.getDamageSource() instanceof EpicFightDamageSource epicdamagesource) {
                float k = epicdamagesource.getImpact();
                impact = event.getAmount() / 4F * (1F + k / 2F) * blockrate;
            } else impact = event.getAmount() / 3F * blockrate;
        }
        super.guard(container, itemCapapbility, event, knockback, impact, this.correctWeaponCategoryAndStyle(event.getPlayerPatch()));
    }

    @Override
    public void dealEvent(PlayerPatch<?> playerpatch, HurtEvent.Pre event, boolean advanced) {
        if(!this.initiate) return;
        event.setResult(AttackResult.ResultType.BLOCKED);

        LivingEntityPatch<?> attackerpatch = EpicFightCapabilities.getEntityPatch(event.getDamageSource().getEntity(), LivingEntityPatch.class);

        if (attackerpatch != null) {
            attackerpatch.setLastAttackEntity(playerpatch.getOriginal());
        }

        if (event.getDamageSource() instanceof EpicFightDamageSource epicfightDamageSource) {
            epicfightDamageSource.setStunType(StunType.NONE);
        }

        event.setCanceled(true);
        Entity directEntity = event.getDamageSource().getDirectEntity();
        LivingEntityPatch<?> entitypatch = EpicFightCapabilities.getEntityPatch(directEntity, LivingEntityPatch.class);

        if (advanced) {
            SkillContainer container = playerpatch.getSkill(this);
            setBrave(container.getDataManager().getDataValue(BRAVE) + this.guardBrave, container, (ServerPlayer) playerpatch.getOriginal());
        }

        if (entitypatch != null) {
            entitypatch.onAttackBlocked(event.getDamageSource(), playerpatch);
        }
    }

    @Override
    public float getPenalizer(CapabilityItem itemCap) {
        return itemCap.getWeaponCategory() == CapabilityItem.WeaponCategories.GREATSWORD ? this.superiorPenalizer : this.penalizer;
    }

    @Override
    public Skill getPriorSkill() {
        return EpicFightSkills.GUARD;

    }

    @Override
    protected boolean isAdvancedGuard() {
        return true;
    }

    @Override
    @Nullable
    protected StaticAnimation getGuardMotion(PlayerPatch<?> playerpatch, CapabilityItem itemCapability, BlockType blockType) {
        StaticAnimation animation = itemCapability.getGuardMotion(this, blockType, playerpatch);

        if (animation != null) {
            return animation;
        }
        if(ModList.get().isLoaded("impactful")) {
            Pair<Integer, Float> k = switch (blockType) {
                case GUARD_BREAK -> Pair.of(CommonConfig.GUARDBREAK_CAMERASHAKE_TIME.get(), CommonConfig.GUARDBREAK_CAMERASHAKE_STRENGTH.get().floatValue());
                case GUARD -> Pair.of(CommonConfig.GUARD_CAMERASHAKE_TIME.get(), CommonConfig.GUARD_CAMERASHAKE_STRENGTH.get().floatValue());
                case ADVANCED_GUARD -> Pair.of(CommonConfig.ADVANCEDGUARD_CAMERASHAKE_TIME.get(), CommonConfig.ADVANCEDGUARD_CAMERASHAKE_STRENGTH.get().floatValue());
            };
            NetWorkManger.sendToPlayer(new CameraShake(k.getFirst(), k.getSecond(), 1.5F), (ServerPlayer) playerpatch.getOriginal());
        }

        return (StaticAnimation)this.getGuradMotionMap(blockType).getOrDefault(itemCapability.getWeaponCategory(), (a, b) -> null).apply(itemCapability, playerpatch);
    }

    @Override
    public void updateContainer(SkillContainer container) {
        PlayerPatch<?> executer = container.getExecuter();
        if(!executer .isLogicalClient()) {
            if (this.successBlock && container.getExecuter().getTickSinceLastAction() > 0) {
                this.successBlock = false;
            }

            if(initiate) {
                if(executer.getTickSinceLastAction() > 10 && container.getDataManager().getDataValue(combocounter)>0){
                    container.getDataManager().setDataSync(combocounter, 0, (ServerPlayer) executer.getOriginal() ) ;
                }
                int tick = executer.getOriginal().tickCount;
                if (container.getDataManager().getDataValue(BRAVE_ACTIVE)) {
                    //每秒消耗勇气值
                    if (tick % 20 == 1)
                        setBrave(container.getDataManager().getDataValue(BRAVE) - this.braveCostPerSecond, container, (ServerPlayer) executer.getOriginal());

                    //勇气值为0或没有使用大剑时退出勇气状态
                    if (container.getDataManager().getDataValue(BRAVE) == 0 || (container.getDataManager().getDataValue(BRAVE_ACTIVE) && !this.correctWeaponCategoryAndStyle(executer))) {
                        container.getDataManager().setDataSync(BRAVE_ACTIVE, false, (ServerPlayer) executer.getOriginal());
                        setBrave(0, container, (ServerPlayer) executer.getOriginal());
                    }
                }

                //勇气值为100时 进入勇气状态
                if (!container.getDataManager().getDataValue(BRAVE_ACTIVE) && container.getDataManager().getDataValue(BRAVE) == 100) {
                    container.getDataManager().setDataSync(BRAVE_ACTIVE, true, (ServerPlayer) executer.getOriginal());
                }
            }
        }
        super.updateContainer(container);
    }

    @OnlyIn(Dist.CLIENT)
    public boolean shouldDraw(SkillContainer container) {
        return container.getDataManager().getDataValue(PENALTY) > 0.0F || container.getDataManager().getDataValue(BRAVE) > 0;
    }

    //
    public void processDamage(PlayerPatch<?> entitypatch, DamageSource damageSource, AttackResult.ResultType resultType, float amount, @Nullable LivingEntityPatch<?> attackerPatch){
        AttackResult result = (entitypatch != null && !damageSource.isBypassInvul()) ? new AttackResult(resultType, amount) : AttackResult.success(amount);
        if (attackerPatch != null) {
            attackerPatch.setLastAttackResult(result);
        }
        DamageSource deflictedDamage = new DamageSource(damageSource.msgId).bypassInvul();
        if (entitypatch != null) {
            entitypatch.getOriginal().hurt(deflictedDamage, result.damage);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void drawOnGui(BattleModeGui gui, SkillContainer container, PoseStack poseStack, float x, float y) {
        poseStack.pushPose();
        poseStack.translate(0, (float)gui.getSlidingProgression(), 0);
        boolean brave_active = initiate && container.getDataManager().getDataValue(BRAVE_ACTIVE);
        RenderSystem.setShaderTexture(0, brave_active ? UI :EpicFightSkills.GUARD.getSkillTexture());
        if(brave_active) {RenderSystem.setShaderColor(1.0F, 0.84F, 0.0F, 0.6F);}
        GuiComponent.blit(poseStack, (int)x, (int)y, 24, 24, 0, 0, 1, 1, 1, 1);
        if(container.getDataManager().getDataValue(BRAVE) > 0)
            gui.font.drawShadow(poseStack, String.format("%d%%", container.getDataManager().getDataValue(BRAVE)), x + 1, y - 4, 16777215);
        gui.font.drawShadow(poseStack, String.format("x%.1f", container.getDataManager().getDataValue(PENALTY)), x + 13, y + 15, 16777215);
    }

    private void setBrave(int count, SkillContainer container, ServerPlayer player){
        container.getDataManager().setDataSync(BRAVE, Mth.clamp(count, 0, 100), player);
    }

    private boolean correctWeaponCategoryAndStyle(PlayerPatch<?> playerPatch){
        CapabilityItem capabilityItem = playerPatch.getHoldingItemCapability(InteractionHand.MAIN_HAND);
        return capabilityItem.getWeaponCategory().equals(CapabilityItem.WeaponCategories.GREATSWORD) && capabilityItem.getStyle(playerPatch).equals(CapabilityItem.Styles.TWO_HAND);
    }
}
