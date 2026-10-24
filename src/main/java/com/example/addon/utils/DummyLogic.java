package com.example.addon.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

// 假人的本地伤害/图腾/装备逻辑，bot 与真人两种实体共用
public final class DummyLogic {
    public enum Gear {
        None,
        Iron,
        Diamond,
        Netherite
    }

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random RNG = new Random();
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final AbstractClientPlayerEntity body;
    private final float maxHealthValue;
    private final int maxTotems;
    private final int respawnDelay;

    private int totems;
    private int invulnTicks;
    private int deadTicks;
    private Vec3d home = Vec3d.ZERO;

    private int hurtInterval = 10;
    private boolean infiniteTotems;
    private Gear gear = Gear.Diamond;
    private int protectionLevel;
    private int unbreakingLevel;
    private boolean offHandHasTotem;
    private Item mainHandItem = Items.AIR;

    private int hitsTaken;
    private int totemPops;
    private java.util.function.Consumer<String> logSink = s -> {};

    public DummyLogic(AbstractClientPlayerEntity body, float health, int totems, int respawnDelay) {
        this.body = body;
        this.maxHealthValue = health;
        this.maxTotems = totems;
        this.respawnDelay = Math.max(1, respawnDelay);
        this.totems = totems;
        var healthAttribute = body.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (healthAttribute != null) {
            healthAttribute.setBaseValue(Math.max(1.0, health));
        }
        body.setHealth(health);
        body.setAbsorptionAmount(0.0f);
        this.home = body.getEntityPos();
    }

    public AbstractClientPlayerEntity body() {
        return body;
    }

    public void setLogSink(java.util.function.Consumer<String> sink) {
        this.logSink = sink == null ? s -> {} : sink;
    }

    private void log(String message) {
        try {
            this.logSink.accept(message);
        } catch (Exception ignored) {
        }
    }

    private static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private String totemText() {
        return infiniteTotems ? "inf" : String.valueOf(totems);
    }

    public void setHome(Vec3d pos) {
        this.home = pos;
    }

    public Vec3d getHome() {
        return home;
    }

    public boolean isDead() {
        return body.getHealth() <= 0.0f;
    }

    public int getTotems() {
        return totems;
    }

    public void configure(int hurtInterval, boolean infiniteTotems, Gear gear, int protection, int unbreaking, Item mainHand) {
        this.hurtInterval = Math.max(0, hurtInterval);
        this.infiniteTotems = infiniteTotems;

        if (this.gear != gear || this.protectionLevel != protection || this.unbreakingLevel != unbreaking) {
            this.gear = gear;
            this.protectionLevel = protection;
            this.unbreakingLevel = unbreaking;
            this.applyArmor();
            log("[equip] gear " + gear + ", protection " + protection + ", unbreaking " + unbreaking);
        }

        if (this.mainHandItem != mainHand) {
            this.mainHandItem = mainHand;
            body.equipStack(EquipmentSlot.MAINHAND, mainHand == Items.AIR ? ItemStack.EMPTY : enchanted(new ItemStack(mainHand)));
            log("[equip] main hand " + (mainHand == Items.AIR ? "empty" : mainHand.getTranslationKey()));
        }

        this.updateOffHand();
    }

    private void applyArmor() {
        if (gear == Gear.None) {
            for (EquipmentSlot slot : ARMOR_SLOTS) body.equipStack(slot, ItemStack.EMPTY);
            return;
        }

        Item[] items = switch (gear) {
            case Iron -> new Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
            case Diamond -> new Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
            default -> new Item[]{Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
        };

        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            body.equipStack(ARMOR_SLOTS[i], enchanted(new ItemStack(items[i])));
        }
    }

    private ItemStack enchanted(ItemStack stack) {
        if (protectionLevel <= 0 && unbreakingLevel <= 0) return stack;

        Registry<Enchantment> registry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        if (protectionLevel > 0) builder.add(registry.getOrThrow(Enchantments.PROTECTION), protectionLevel);
        if (unbreakingLevel > 0) builder.add(registry.getOrThrow(Enchantments.UNBREAKING), unbreakingLevel);
        stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());
        return stack;
    }

    private void updateOffHand() {
        boolean hasTotem = infiniteTotems || totems > 0;
        if (hasTotem == offHandHasTotem) return;
        offHandHasTotem = hasTotem;
        body.equipStack(EquipmentSlot.OFFHAND, hasTotem ? new ItemStack(Items.TOTEM_OF_UNDYING) : ItemStack.EMPTY);
    }

    public void hurtBy(PlayerEntity attacker) {
        if (isDead()) return;

        String attackerName = attacker.getName().getString();
        if (invulnTicks > 0) {
            log("[ignored] " + attackerName + " hit while invulnerable (" + invulnTicks + " ticks left)");
            return;
        }

        float raw = computeDamage(attacker);
        float damage = reduceDamage(raw);
        hitsTaken++;
        body.hurtTime = 10;
        body.maxHurtTime = 10;
        body.animateDamage(attacker.getYaw());

        float next = body.getHealth() - damage;
        if (next <= 0.0f) {
            if (infiniteTotems || totems > 0) {
                if (!infiniteTotems) totems--;
                popTotem();
                next = 1.0f;
                log("[totem] " + attackerName + " popped a totem, totems left " + totemText() + ", hp 1.0");
            } else {
                body.setHealth(0.0f);
                this.deadTicks = 0;
                mc.world.playSoundClient(body.getX(), body.getY(), body.getZ(),
                    SoundEvents.ENTITY_PLAYER_DEATH, SoundCategory.PLAYERS, 1.0f, 1.0f, false);
                log("[death] killed by " + attackerName + " after " + hitsTaken + " hits");
                return;
            }
        }

        body.setHealth(next);
        this.invulnTicks = hurtInterval;
        mc.world.playSoundClient(body.getX(), body.getY(), body.getZ(),
            SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 1.0f, false);
        log("[hit] " + attackerName + " raw " + fmt(raw) + " -> " + fmt(damage)
            + " (armor " + (int) body.getArmor() + ", prot " + protectionLevel + ")"
            + ", hp " + fmt(next) + "/" + fmt(maxHealthValue) + ", totems " + totemText()
            + ", interval " + hurtInterval);
    }

    private float computeDamage(PlayerEntity attacker) {
        double base = attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        if (attacker.getMainHandStack().getItem() == Items.MACE && attacker.fallDistance > 1.5f) {
            base *= 1.0 + attacker.fallDistance * 0.5;
        }
        return (float) Math.max(1.0, base);
    }

    // 简化版护甲/保护减免，够测试用
    private float reduceDamage(float damage) {
        double armor = Math.min(20.0, body.getArmor());
        double epf = Math.min(20.0, protectionLevel * 4.0);
        double reduced = damage * (1.0 - armor / 25.0) * (1.0 - epf / 25.0);
        return (float) Math.max(1.0, reduced);
    }

    // 照原版不死图腾：回 1 血、清效果、给吸收/再生/抗火
    private void popTotem() {
        totemPops++;
        body.handleStatus(EntityStatuses.USE_TOTEM_OF_UNDYING);
        body.setHealth(1.0f);
        body.setAbsorptionAmount(0.0f);
        body.clearStatusEffects();
        body.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 800, 1));
        body.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1));
        body.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0));

        double x = body.getX(), y = body.getY() + 1.2, z = body.getZ();
        for (int i = 0; i < 24; i++) {
            mc.world.addParticleClient(ParticleTypes.TOTEM_OF_UNDYING, x, y, z,
                (RNG.nextDouble() - 0.5) * 0.9, RNG.nextDouble() * 0.7, (RNG.nextDouble() - 0.5) * 0.9);
        }
        mc.world.playSoundClient(x, y, z, SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f, false);
        this.updateOffHand();
    }

    public void respawn() {
        this.deadTicks = 0;
        this.invulnTicks = 20;
        this.totems = maxTotems;
        body.deathTime = 0;
        body.hurtTime = 0;
        body.setHealth(maxHealthValue);
        body.setAbsorptionAmount(0.0f);
        body.clearStatusEffects();
        this.offHandHasTotem = false;
        this.mainHandItem = Items.AIR;
        this.applyArmor();
        this.updateOffHand();
        body.setPos(home.x, home.y, home.z);
        body.setVelocity(Vec3d.ZERO);
        log("[respawn] hp " + fmt(maxHealthValue) + ", totems " + totemText()
            + ", hits taken " + hitsTaken + ", totem pops " + totemPops);
        this.hitsTaken = 0;
        this.totemPops = 0;
    }

    public void tick() {
        if (invulnTicks > 0) invulnTicks--;
        if (isDead()) {
            deadTicks++;
            if (deadTicks > respawnDelay) respawn();
        }
    }

    // 水平走向目标点，Y 保持不变（测试用，不做落地物理）
    public void stepTowards(Vec3d target, double speed) {
        if (isDead()) return;
        double dx = target.x - body.getX();
        double dz = target.z - body.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.08) return;

        dx = dx / len * speed;
        dz = dz / len * speed;

        if (canMove(dx, 0.0)) body.setPos(body.getX() + dx, body.getY(), body.getZ());
        if (canMove(0.0, dz)) body.setPos(body.getX(), body.getY(), body.getZ() + dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        body.setYaw(yaw);
        body.setHeadYaw(yaw);
    }

    private boolean canMove(double dx, double dz) {
        Box box = body.getBoundingBox().offset(dx, 0.0, dz);
        return mc.world.isSpaceEmpty(body, box);
    }
}
