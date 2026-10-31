package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.events.KeyboardInputEvent;
import com.example.addon.mixin.ClientWorldAccessor;
import com.example.addon.mixin.FireworkRocketEntityAccessor;
import com.example.addon.utils.eps.Rot2f;
import com.example.addon.utils.eps.RotationManager;
import dev.sherii.yuio.AModule;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;

public class FireworkElytraFly extends AModule {
    private static final long FIREWORK_PENDING_TIMEOUT_MS = 500;

    public enum Mode { Legit, GrimDurability, AutoSpear }
    public enum FireWorkMode { Auto, Delay, None }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgArmor = settings.createGroup("AutoArmor");

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("Mode")
        .description("运行模式")
        .defaultValue(Mode.GrimDurability)
        .build()
    );
    private final Setting<Double> spearDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("SpearDelay")
        .description("长矛突进延迟")
        .defaultValue(800)
        .sliderMax(1000)
        .build()
    );
    public final Setting<FireWorkMode> fireWorkMode = sgGeneral.add(new EnumSetting.Builder<FireWorkMode>()
        .name("FireWorkMode")
        .description("烟花使用模式")
        .defaultValue(FireWorkMode.Auto)
        .build()
    );
    private final Setting<Double> packetDealy = sgGeneral.add(new DoubleSetting.Builder()
        .name("PacketDelay")
        .description("发包延迟tick数")
        .defaultValue(3)
        .sliderMax(100)
        .build()
    );
    public final Setting<Boolean> stand = sgGeneral.add(new BoolSetting.Builder()
        .name("Stand")
        .description("站飞")
        .defaultValue(true)
        .build()
    );
    public final Setting<Boolean> freePitch = sgGeneral.add(new BoolSetting.Builder()
        .name("FreePitch")
        .description("自由竖直视角")
        .defaultValue(false)
        .build()
    );
    public final Setting<Boolean> noSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("NoSprint")
        .description("自动停止疾跑")
        .defaultValue(true)
        .build()
    );
    public final Setting<Boolean> autoJump = sgGeneral.add(new BoolSetting.Builder()
        .name("AutoJump")
        .description("自动跳跃")
        .defaultValue(true)
        .build()
    );
    public final Setting<Boolean> releaseSneak = sgGeneral.add(new BoolSetting.Builder()
        .name("ReleaseSneak")
        .description("自动松开shift")
        .defaultValue(true)
        .build()
    );
    public final Setting<Boolean> pressSneak = sgGeneral.add(new BoolSetting.Builder()
        .name("PressSneak")
        .description("自动按下shift")
        .defaultValue(true)
        .build()
    );
    public final Setting<Integer> releaseDelay = sgGeneral.add(new IntSetting.Builder()
        .name("ReleaseDelay")
        .description("shift延迟")
        .defaultValue(100)
        .sliderMax(1000)
        .build()
    );
    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("FireWorkDelay")
        .description("烟花操作延迟")
        .defaultValue(1000)
        .visible(() -> fireWorkMode.get() == FireWorkMode.Delay)
        .sliderMax(3000)
        .build()
    );
    private final Setting<Boolean> checkFirework = sgGeneral.add(new BoolSetting.Builder()
        .name("CheckFireWork")
        .description("自动检查烟花")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> checkTime = sgGeneral.add(new IntSetting.Builder()
        .name("CheckTime")
        .description("检查时间")
        .defaultValue(30)
        .min(0)
        .sliderMax(50)
        .build()
    );
    public final Setting<Boolean> inventorySwap = sgGeneral.add(new BoolSetting.Builder()
        .name("InventorySwap")
        .description("背包鬼手")
        .defaultValue(true)
        .build()
    );
    public final Setting<Boolean> control = sgGeneral.add(new BoolSetting.Builder()
        .name("Control")
        .description("甲飞控制")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> flySpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("Speed")
        .description("飞行速度")
        .defaultValue(1.7)
        .min(0)
        .sliderMax(5.0)
        .build()
    );
    private final Setting<Double> fallSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("FallSpeed")
        .description("下落速度")
        .defaultValue(0.02)
        .sliderRange(0.0, 3.0)
        .build()
    );
    private final Setting<Boolean> horizontalNoGravity = sgGeneral.add(new BoolSetting.Builder()
        .name("HorizontalNoGravity")
        .description("关闭重力")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> deBug = sgGeneral.add(new BoolSetting.Builder()
        .name("DeBug")
        .description("dev查bug的，没iq不要开")
        .defaultValue(false)
        .build()
    );

    // 自动穿甲
    private final Setting<Boolean> autoArmor = sgArmor.add(new BoolSetting.Builder()
        .name("AutoArmor")
        .description("自动穿甲")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> armorDelay = sgArmor.add(new IntSetting.Builder()
        .name("ArmorDelay")
        .description("操作延迟(毫秒MS)")
        .defaultValue(10)
        .min(0)
        .sliderMax(1000)
        .build()
    );
    private final Setting<Boolean> autoElytra = sgArmor.add(new BoolSetting.Builder()
        .name("AutoElytra")
        .description("自动切换鞘翅")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> ignoreBinding = sgArmor.add(new BoolSetting.Builder()
        .name("IgnoreBinding")
        .description("忽略绑定诅咒")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> snowBug = sgArmor.add(new BoolSetting.Builder()
        .name("SnowBug")
        .description("")
        .defaultValue(false)
        .build()
    );

    public float yaw = 0;
    public float pitch = 0;
    public boolean isUsingFirework = false;
    public boolean isFallFlying = false;
    public int packetDelayInt = 0;
    public int spearDelayInt = 0;
    public boolean shouldJump = false, shouldRestore = false;
    public PlayerInput bypassInput = null;
    public boolean hasSpear = false;

    private final Timer fireworkTimer = new Timer();
    private final Timer fireworkPendingTimer = new Timer();
    private final Timer armorTimer = new Timer();
    private final Timer spearTimer = new Timer();
    private boolean fireworkPending = false;

    public FireworkElytraFly() {
        super(AddonTemplate.SURVIVAL, "FireworkElytraFly", "skid LeavesHack");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        if (noSprint.get()) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            mc.player.setSprinting(false);
        }
        hasSpear = false;
        spearTimer.setMs(99999);
        fireworkTimer.setMs(99999);
        fireworkPendingTimer.setMs(99999);
        armorTimer.setMs(99999);
        fireworkPending = false;
        packetDelayInt = 0;
        spearDelayInt = 0;
        shouldJump = false;
        isFallFlying = false;
        isUsingFirework = false;
    }

    @Override
    public void onDeactivate() {
        fireworkPending = false;
        if (mc.player == null) return;
        if (pressSneak.get()) mc.player.setSneaking(true);
        if (releaseSneak.get()) {
            long d = releaseDelay.get();
            java.util.Timer timer = new java.util.Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mc.execute(() -> {
                        if (mc.player != null) mc.player.setSneaking(true);
                    });
                }
            }, d);
        }
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        shouldJump = false;
        if (!shouldRestore) return;
        mc.player.input.playerInput = bypassInput;
        bypassInput = null;
        shouldRestore = false;
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (shouldJump) {
            event.setJump(true);
            shouldJump = false;
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!fireworkPending || mc.player == null) return;
        if (event.entity instanceof FireworkRocketEntity firework && firework.getOwner() == mc.player) {
            fireworkPending = false;
        }
    }

    // 移动前改速度，不然鞘翅会按原输入飞
    private void onTravelPre() {
        if (!isFallFlying) return;
        if (!control.get()) return;
        if (mc.currentScreen instanceof ChatScreen || (!isUsingFirework && !wantToMove())) {
            mc.player.setVelocity(0, fallSpeed.get(), 0);
            return;
        }
        if (!wantToMove()) mc.player.setVelocity(0, fallSpeed.get(), 0);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        isFallFlying = mc.player.isGliding();
        isUsingFirework = false;

        if (fireworkPending && fireworkPendingTimer.passedMs(FIREWORK_PENDING_TIMEOUT_MS)) fireworkPending = false;

        if (checkFirework.get() || fireworkPending) {
            for (var entity : mc.world.getEntities()) {
                if (entity instanceof FireworkRocketEntity firework && firework.getOwner() == mc.player) {
                    if (checkFirework.get() && ((FireworkRocketEntityAccessor) firework).yuio$getLife() <= checkTime.get()) {
                        isUsingFirework = true;
                        fireworkPending = false;
                        break;
                    }
                }
            }
        }

        if (mc.currentScreen != null && mc.currentScreen instanceof HandledScreen<?>
            && !(mc.currentScreen instanceof InventoryScreen || mc.currentScreen instanceof CreativeInventoryScreen)) return;

        onTravelPre();
        tickArmor();

        if (mc.player.isOnGround() && autoJump.get()) {
            shouldJump = true;
            mc.player.jump();
            return;
        }

        yaw = getSprintYaw(mc.player.getYaw());
        pitch = getPitch(mc.player.getPitch());
        if (deBug.get() && mc.currentScreen != null) info("screen" + mc.currentScreen.getClass().getSimpleName());

        syncInput();
        packetDelayInt++;
        if (control.get() && isUsingFirework && wantToMove()) {
            RotationManager.INSTANCE.setRotations(new Rot2f(yaw, pitch), 127.0);
        }

        int elytra = findItemInventorySlot(Items.ELYTRA);
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean wearingElytra = chest.isOf(Items.ELYTRA) && chest.isDamageable() && chest.getDamage() < chest.getMaxDamage();

        if (wearingElytra && !isFallFlying && !mc.player.isOnGround() && mode.get() == Mode.Legit) {
            shouldJump = true;
            sendGlidingStart();
        }

        if (mode.get() == Mode.GrimDurability && !isFallFlying) {
            if (elytra != -1 && packetDelayInt > packetDealy.get()) {
                swapElytraGrim(elytra);
                shouldJump = true;
                sendGlidingStart();
                if (!isUsingFirework && !fireworkPending && fireWorkMode.get() == FireWorkMode.Auto) {
                    offFirework();
                } else if (fireWorkMode.get() == FireWorkMode.Delay && wantToMove()) {
                    if (!checkFirework.get() || !isUsingFirework) offFirework();
                }
                swapElytraGrim(elytra);
                packetDelayInt = 0;
            }
        }

        if (mode.get() == Mode.Legit) {
            if (wearingElytra && isFallFlying) {
                if (!isUsingFirework && !fireworkPending && fireWorkMode.get() == FireWorkMode.Auto) {
                    offFirework();
                } else if (fireWorkMode.get() == FireWorkMode.Delay && wantToMove()) {
                    if (!checkFirework.get() || !isUsingFirework) offFirework();
                }
            }
        }

        if (mode.get() == Mode.AutoSpear) {
            ItemStack stack = mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot());
            if (stack.isIn(ItemTags.SPEARS) && spearTimer.passedMs(spearDelay.get()) && wantToMove()) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STAB, BlockPos.ORIGIN, Direction.DOWN));
                mc.player.swingHand(Hand.MAIN_HAND);
                spearTimer.reset();
            }
            if (elytra != -1 && packetDelayInt > packetDealy.get()) {
                swapElytraGrim(elytra);
                if (!mc.player.isOnGround()) {
                    shouldJump = true;
                    sendGlidingStart();
                }
                swapElytraGrim(elytra);
                packetDelayInt = 0;
            }
        }
    }

    private void sendGlidingStart() {
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        mc.player.startGliding();
    }

    private void swapElytraGrim(int elytra) {
        int sync = mc.player.currentScreenHandler.syncId;
        mc.interactionManager.clickSlot(sync, elytra, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(sync, 6, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(sync, elytra, 0, SlotActionType.PICKUP, mc.player);
    }

    private void syncInput() {
        if (shouldRestore) return;
        bypassInput = mc.player.input.playerInput;
        mc.player.input.playerInput = new PlayerInput(false, false, false, false, false, false, false);
        mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
        shouldRestore = true;
    }

    private boolean offFirework() {
        if (fireworkPending) return false;
        if (fireWorkMode.get() == FireWorkMode.Delay && !fireworkTimer.passedMs(delay.get())) return false;

        if (mc.player.getMainHandStack().getItem() == Items.FIREWORK_ROCKET) {
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, yaw, pitch));
            fireworkTimer.reset();
            markFireworkPending();
            return true;
        } else if (mc.player.getOffHandStack().getItem() == Items.FIREWORK_ROCKET) {
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, yaw, pitch));
            fireworkTimer.reset();
            markFireworkPending();
            return true;
        } else if (inventorySwap.get() && findItemInventorySlot(Items.FIREWORK_ROCKET) != -1) {
            int firework = findItemInventorySlot(Items.FIREWORK_ROCKET);
            int selected = mc.player.getInventory().getSelectedSlot();
            inventorySwap(firework, selected);
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, yaw, pitch));
            inventorySwap(firework, selected);
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            fireworkTimer.reset();
            markFireworkPending();
            return true;
        } else {
            int firework = findItem(Items.FIREWORK_ROCKET);
            if (firework != -1) {
                int old = mc.player.getInventory().getSelectedSlot();
                switchToSlot(firework);
                sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, yaw, pitch));
                switchToSlot(old);
                fireworkTimer.reset();
                markFireworkPending();
                return true;
            }
        }
        return false;
    }

    private void markFireworkPending() {
        fireworkPending = true;
        fireworkPendingTimer.reset();
    }

    private void sendSequencedPacket(SequencedPacketCreator creator) {
        if (mc.getNetworkHandler() == null || mc.world == null) return;
        try (PendingUpdateManager manager = ((ClientWorldAccessor) mc.world).yuio$getPendingUpdateManager().incrementSequence()) {
            int seq = manager.getSequence();
            mc.getNetworkHandler().sendPacket(creator.predict(seq));
        }
    }

    private int findItemInventorySlot(Item item) {
        for (int i = 0; i < 45; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private int findItem(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private void switchToSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    private void inventorySwap(int slot, int hotbarSlot) {
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, hotbarSlot, SlotActionType.SWAP, mc.player);
    }

    private boolean wantToMove() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() || mc.options.leftKey.isPressed()
            || mc.options.rightKey.isPressed() || mc.options.jumpKey.isPressed() || mc.options.sneakKey.isPressed();
    }

    private boolean isMoving() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
            || mc.options.rightKey.isPressed() || mc.options.leftKey.isPressed();
    }

    private float getSprintYaw(float yaw) {
        if (mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed()) {
            if (mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed()) yaw -= 45f;
            else if (mc.options.rightKey.isPressed() && !mc.options.leftKey.isPressed()) yaw += 45f;
        } else if (mc.options.backKey.isPressed() && !mc.options.forwardKey.isPressed()) {
            yaw += 180f;
            if (mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed()) yaw += 45f;
            else if (mc.options.rightKey.isPressed() && !mc.options.leftKey.isPressed()) yaw -= 45f;
        } else if (mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed()) {
            yaw -= 90f;
        } else if (mc.options.rightKey.isPressed() && !mc.options.leftKey.isPressed()) {
            yaw += 90f;
        }
        return yaw;
    }

    private float getPitch(float pitch) {
        if (mc.currentScreen instanceof ChatScreen) return pitch;
        if (mc.options.sneakKey.isPressed() && mc.options.jumpKey.isPressed()) {
            pitch = -3;
        } else if (mc.options.jumpKey.isPressed()) {
            pitch = isMoving() ? -45 : -90;
        } else if (mc.options.sneakKey.isPressed()) {
            pitch = isMoving() ? 45 : 90;
        }
        if (isMoving() && !mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed() && !mc.options.forwardKey.isPressed()) {
            pitch = horizontalNoGravity.get() ? 0 : -1.9f;
        }
        if (!freePitch.get() && isMoving() && !mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
            pitch = -1.9f;
        }
        return pitch;
    }

    @Override
    public String getInfoString() {
        if (mc.player == null || mc.world == null) return null;
        int fireworks = 0;
        int end = inventorySwap.get() ? 45 : 9;
        for (int i = 0; i < end; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.FIREWORK_ROCKET) fireworks += stack.getCount();
        }
        return "[F:" + fireworks + "]";
    }

    // 自动穿甲逻辑
    private void tickArmor() {
        if (!autoArmor.get()) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)
            && !(mc.currentScreen instanceof InventoryScreen) && !(mc.currentScreen instanceof WidgetScreen)) return;
        if (mc.player.playerScreenHandler != mc.player.currentScreenHandler) return;
        if (!armorTimer.passedMs(armorDelay.get())) return;
        armorTimer.reset();

        Map<EquipmentSlot, int[]> armorMap = new HashMap<>(4);
        armorMap.put(EquipmentSlot.FEET, new int[]{36, getProtection(mc.player.getInventory().getStack(36)), -1, -1});
        armorMap.put(EquipmentSlot.LEGS, new int[]{37, getProtection(mc.player.getInventory().getStack(37)), -1, -1});
        armorMap.put(EquipmentSlot.CHEST, new int[]{38, getProtection(mc.player.getInventory().getStack(38)), -1, -1});
        armorMap.put(EquipmentSlot.HEAD, new int[]{39, getProtection(mc.player.getInventory().getStack(39)), -1, -1});

        for (int s = 0; s < 36; s++) {
            ItemStack stack = mc.player.getInventory().getStack(s);
            if (!stack.contains(DataComponentTypes.EQUIPPABLE) && stack.getItem() != Items.ELYTRA) continue;
            int protection = getProtection(stack);
            EquipmentSlot slot = stack.getItem() == Items.ELYTRA
                ? EquipmentSlot.CHEST : stack.get(DataComponentTypes.EQUIPPABLE).slot();
            for (Map.Entry<EquipmentSlot, int[]> e : armorMap.entrySet()) {
                if (e.getKey() == EquipmentSlot.FEET && mc.player.hurtTime > 1 && snowBug.get()) {
                    if (!mc.player.getInventory().getStack(36).isEmpty() && mc.player.getInventory().getStack(36).getItem() == Items.LEATHER_BOOTS) continue;
                    if (!stack.isEmpty() && stack.getItem() == Items.LEATHER_BOOTS) {
                        e.getValue()[2] = s;
                        continue;
                    }
                }
                if (autoElytra.get() && isActive() && e.getKey() == EquipmentSlot.CHEST) {
                    if (mode.get() == Mode.GrimDurability || mode.get() == Mode.AutoSpear) continue;
                    if (isValidElytra(mc.player.getInventory().getStack(38))) continue;
                    if (e.getValue()[2] != -1 && isValidElytra(mc.player.getInventory().getStack(e.getValue()[2]))) continue;
                    if (isValidElytra(stack)) e.getValue()[2] = s;
                    continue;
                }
                if (protection > 0 && e.getKey() == slot) {
                    if (protection > e.getValue()[1] && protection > e.getValue()[3]) {
                        e.getValue()[2] = s;
                        e.getValue()[3] = protection;
                    }
                }
            }
        }

        for (Map.Entry<EquipmentSlot, int[]> entry : armorMap.entrySet()) {
            if (entry.getValue()[2] == -1) continue;
            if (entry.getValue()[1] == -1 && entry.getValue()[2] < 9) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 36 + entry.getValue()[2], 1, SlotActionType.QUICK_MOVE, mc.player);
                sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            } else if (mc.player.playerScreenHandler == mc.player.currentScreenHandler) {
                int armorSlot = (entry.getValue()[0] - 34) + (39 - entry.getValue()[0]) * 2;
                int newArmorSlot = entry.getValue()[2] < 9 ? 36 + entry.getValue()[2] : entry.getValue()[2];
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, newArmorSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, armorSlot, 0, SlotActionType.PICKUP, mc.player);
                if (entry.getValue()[1] != -1)
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, newArmorSlot, 0, SlotActionType.PICKUP, mc.player);
                sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            }
            return;
        }
    }

    private boolean isValidElytra(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.ELYTRA && stack.isDamageable() && stack.getDamage() < stack.getMaxDamage();
    }

    private void sendPacket(net.minecraft.network.packet.Packet<?> packet) {
        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendPacket(packet);
    }

    private int getProtection(ItemStack is) {
        if (is.contains(DataComponentTypes.EQUIPPABLE) || is.getItem() == Items.ELYTRA) {
            int prot = 0;
            if (is.getItem() == Items.ELYTRA) {
                if (!(is.isDamageable() && is.getDamage() < is.getMaxDamage())) return 0;
                prot = 1;
            }
            if (is.hasEnchantments()) {
                if (ignoreBinding.get() && getEnchantLevel(is, Enchantments.BINDING_CURSE) > 0) return -1;
                prot += getEnchantLevel(is, Enchantments.PROTECTION);
            }
            return (is.contains(DataComponentTypes.EQUIPPABLE) ? getBaseArmorScore(is) : 0) + prot;
        }
        return is.isEmpty() ? -1 : 0;
    }

    private static int getEnchantLevel(ItemStack stack, RegistryKey<Enchantment> key) {
        for (var entry : stack.getEnchantments().getEnchantmentEntries()) {
            if (entry.getKey().matchesKey(key)) return entry.getIntValue();
        }
        return 0;
    }

    private int getBaseArmorScore(ItemStack itemStack) {
        if (!itemStack.contains(DataComponentTypes.ATTRIBUTE_MODIFIERS)) return 0;
        int score = 0;
        AttributeModifiersComponent component = itemStack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        for (AttributeModifiersComponent.Entry modifier : component.modifiers()) {
            if (modifier.attribute() == EntityAttributes.ARMOR || modifier.attribute() == EntityAttributes.ARMOR_TOUGHNESS) {
                double e = modifier.modifier().value();
                score += switch (modifier.modifier().operation()) {
                    case ADD_VALUE -> (int) e;
                    case ADD_MULTIPLIED_BASE -> (int) (e * mc.player.getAttributeBaseValue(modifier.attribute()));
                    case ADD_MULTIPLIED_TOTAL -> 0;
                };
            }
        }
        return score;
    }

    private static class Timer {
        private long time = System.currentTimeMillis();

        void reset() {
            time = System.currentTimeMillis();
        }

        boolean passedMs(double ms) {
            return System.currentTimeMillis() - time >= ms;
        }

        void setMs(double ms) {
            time = System.currentTimeMillis() - (long) ms;
        }
    }
}
