package thaumcraft.common.entities.projectile;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.items.casters.ItemCaster;
import thaumcraft.init.ModEntities;

import javax.annotation.concurrent.GuardedBy;

/**
 * Aspect orb (TC6 node system, Thaumaturge EntityAspectOrb parity).
 *
 * <p>Spawns when a node bursts (e.g. broken or pearl-detonated) carrying a
 * share of the node's vis. Drifts with light gravity, bobs in water, and is
 * gently pulled toward a nearby player who has a caster gauntlet with room in
 * its buffer. Touching such a player transfers the vis into the wand's buffer.
 * Orbs dissolve after 150 ticks or when hurt to 0 health.
 */
public class EntityAspectOrb extends Entity {

    public static final int MAX_AGE = 150;

    private static final EntityDataAccessor<String> DATA_ASPECT =
            SynchedEntityData.defineId(EntityAspectOrb.class, EntityDataSerializers.STRING);
    private static final short DEFAULT_HEALTH = 5;
    private static final short DEFAULT_AGE = 0;
    private static final short DEFAULT_VALUE = 1;
    private static final String KEY_HEALTH = "Health";
    private static final String KEY_AGE = "Age";
    private static final String KEY_VALUE = "Value";
    private static final String KEY_ASPECT = "Aspect";

    private static final double FOLLOW_RANGE = 8.0;
    private static final int PLAYER_SCAN_PERIOD = 5;
    private static final double FOLLOW_STRENGTH = 0.1;
    private static final double GRAVITY = 0.03;
    private static final double BOUNCE_FACTOR = 0.9;
    private static final float FRICTION = 0.98F;
    private static final double UNDERWATER_DRAG = 0.99;
    private static final double UNDERWATER_LIFT = 0.0005;
    private static final double UNDERWATER_MAX_RISE = 0.06;
    private static final double LAVA_SPREAD = 0.2;
    private static final double LAVA_RISE = 0.2;
    private static final float LAVA_HISS_VOLUME = 0.4F;
    private static final float LAVA_HISS_PITCH_BASE = 2.0F;
    private static final float LAVA_HISS_PITCH_SPREAD = 0.4F;
    private static final double SPAWN_HORIZONTAL_SPEED = 0.4;
    private static final double SPAWN_HORIZONTAL_OFFSET = 0.2;
    private static final double SPAWN_VERTICAL_SPEED = 0.4;
    private static final float FULL_TURN_DEGREES = 360.0F;
    private static final int PICKUP_DELAY_TICKS = 2;
    private static final float PICKUP_VOLUME = 0.1F;
    private static final float PICKUP_PITCH_FACTOR = 0.5F;
    private static final float PICKUP_PITCH_SPREAD = 0.7F;
    private static final float PICKUP_PITCH_BASE = 1.8F;
    private static final int PICKUP_ITEM_COUNT = 1;

    private int age = DEFAULT_AGE;
    private int health = DEFAULT_HEALTH;
    private int value = DEFAULT_VALUE;
    @Nullable
    private Player followTarget;

    public EntityAspectOrb(EntityType<? extends EntityAspectOrb> type, Level level) {
        super(type, level);
    }

    public EntityAspectOrb(Level level, double x, double y, double z, Aspect aspect, int value) {
        this(ModEntities.ASPECT_ORB.get(), level);
        setPos(x, y, z);
        setYRot(random.nextFloat() * FULL_TURN_DEGREES);
        setDeltaMovement(
                random.nextDouble() * SPAWN_HORIZONTAL_SPEED - SPAWN_HORIZONTAL_OFFSET,
                random.nextDouble() * SPAWN_VERTICAL_SPEED,
                random.nextDouble() * SPAWN_HORIZONTAL_SPEED - SPAWN_HORIZONTAL_OFFSET);
        setAspect(aspect);
        this.value = value;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ASPECT, Aspect.AIR.getTag());
    }

    public Aspect getAspect() {
        Aspect aspect = Aspect.getAspect(entityData.get(DATA_ASPECT));
        return aspect != null ? aspect : Aspect.AIR;
    }

    public void setAspect(Aspect aspect) {
        entityData.set(DATA_ASPECT, aspect.getTag());
    }

    public int getAge() {
        return age;
    }

    public int getValue() {
        return value;
    }

    public int getAspectColor() {
        return getAspect().getColor();
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    @Override
    public void tick() {
        super.tick();
        if (isEyeInFluid(FluidTags.WATER)) {
            Vec3 motion = getDeltaMovement();
            setDeltaMovement(motion.x * UNDERWATER_DRAG,
                    Math.min(motion.y + UNDERWATER_LIFT, UNDERWATER_MAX_RISE), motion.z * UNDERWATER_DRAG);
        } else {
            applyGravity();
        }
        if (level().getFluidState(blockPosition()).is(FluidTags.LAVA)) {
            setDeltaMovement(
                    (random.nextFloat() - random.nextFloat()) * LAVA_SPREAD, LAVA_RISE,
                    (random.nextFloat() - random.nextFloat()) * LAVA_SPREAD);
            playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, LAVA_HISS_VOLUME,
                    LAVA_HISS_PITCH_BASE + random.nextFloat() * LAVA_HISS_PITCH_SPREAD);
        }
        if (!level().isClientSide()) {
            followPlayer();
        }
        double fallSpeed = getDeltaMovement().y;
        move(MoverType.SELF, getDeltaMovement());
        float horizontalFriction = FRICTION;
        if (onGround()) {
            horizontalFriction = level().getBlockState(blockPosition()).getBlock().getFriction() * FRICTION;
        }
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(motion.x * horizontalFriction, motion.y * FRICTION, motion.z * horizontalFriction);
        if (onGround() && fallSpeed < -GRAVITY) {
            Vec3 damped = getDeltaMovement();
            setDeltaMovement(damped.x, -fallSpeed * BOUNCE_FACTOR, damped.z);
        }
        age++;
        if (age >= MAX_AGE) {
            discard();
        }
    }

    private void followPlayer() {
        if (followTarget != null
                && (followTarget.isRemoved() || followTarget.distanceToSqr(this) > FOLLOW_RANGE * FOLLOW_RANGE)) {
            followTarget = null;
        }
        if (followTarget == null && tickCount % PLAYER_SCAN_PERIOD == 0) {
            net.minecraft.world.phys.AABB search = getBoundingBox().inflate(FOLLOW_RANGE);
            for (Player candidate : level().getEntitiesOfClass(Player.class, search,
                    p -> !p.isRemoved() && this.canAttract(p))) {
                if (followTarget == null || candidate.distanceToSqr(this) < followTarget.distanceToSqr(this)) {
                    followTarget = candidate;
                }
            }
        }
        if (followTarget == null) {
            return;
        }
        Vec3 offset = followTarget.getEyePosition().subtract(position());
        double distance = offset.length();
        if (distance >= FOLLOW_RANGE || distance <= 0.0) {
            return;
        }
        double pull = 1.0 - distance / FOLLOW_RANGE;
        setDeltaMovement(getDeltaMovement().add(offset.scale(1.0 / distance).scale(pull * pull * FOLLOW_STRENGTH)));
    }

    private boolean canAttract(Entity candidate) {
        return candidate instanceof Player player
                && !player.isSpectator()
                && !findWand(player).isEmpty();
    }

    /** Find a gauntlet in the player's hotbar with buffer room for this orb. */
    private ItemStack findWand(Player player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof ItemCaster)) {
                continue;
            }
            if (ItemCaster.bufferAmountOf(stack, getAspect()) >= ItemCaster.bufferCapacity(stack)) {
                continue;
            }
            return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void playerTouch(Player player) {
        if (!(player instanceof ServerPlayer) || player.takeXpDelay > 0
                || !getAspect().isPrimal()) {
            return;
        }
        ItemStack wand = findWand(player);
        if (wand.isEmpty()) {
            return;
        }
        ItemCaster.topUpBuffer(wand, getAspect(), value);
        player.takeXpDelay = PICKUP_DELAY_TICKS;
        playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, PICKUP_VOLUME,
                PICKUP_PITCH_FACTOR * ((random.nextFloat() - random.nextFloat()) * PICKUP_PITCH_SPREAD + PICKUP_PITCH_BASE));
        discard();
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isInvulnerableToBase(source)) {
            return false;
        }
        markHurt();
        health = (int) (health - amount);
        if (health <= 0) {
            discard();
        }
        return true;
    }

    @Override
    public final boolean hurtClient(DamageSource source) {
        return !isInvulnerableToBase(source);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putShort(KEY_HEALTH, (short) health);
        output.putShort(KEY_AGE, (short) age);
        output.putShort(KEY_VALUE, (short) value);
        output.putString(KEY_ASPECT, entityData.get(DATA_ASPECT));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        health = input.getShortOr(KEY_HEALTH, DEFAULT_HEALTH);
        age = input.getShortOr(KEY_AGE, DEFAULT_AGE);
        value = input.getShortOr(KEY_VALUE, DEFAULT_VALUE);
        entityData.set(DATA_ASPECT, input.getStringOr(KEY_ASPECT, Aspect.AIR.getTag()));
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.AMBIENT;
    }
}
