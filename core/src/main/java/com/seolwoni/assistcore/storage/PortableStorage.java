package com.seolwoni.assistcore.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import com.seolwoni.assistcore.engine.Action;
import com.seolwoni.assistcore.log.UsageLog;
import com.seolwoni.assistcore.util.Aim;
import com.seolwoni.assistcore.util.Alerts;
import com.seolwoni.assistcore.util.Blocks2;
import com.seolwoni.assistcore.util.Body;
import com.seolwoni.assistcore.util.Human;
import com.seolwoni.assistcore.util.InvBase;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Storing into a box carried along, the way a player does it far from home: put an ender chest or a shulker box
 * down right next to us, shift-click the loot in, close it and pick the box up again.
 * <ul>
 * <li>A shulker box keeps what is in it when broken, so it always comes back with us.</li>
 * <li>An ender chest only comes back with a Silk Touch pickaxe (otherwise it breaks into obsidian). Without one, an
 * ender chest already standing within reach is used; otherwise one is put down and left there, and its position is
 * written to the usage log.</li>
 * </ul>
 * Each mod says which inventory slots may be stored and which cells must stay free (a tunnel, a farm path).
 */
public final class PortableStorage implements Action {
	/** The "보관 방식" setting every mod shares. */
	public static final int CHEST = 0, ENDER = 1, SHULKER = 2, AUTO = 3;
	public static final String[] LABELS = { "상자", "엔더 상자", "셜커 상자", "자동" };
	public static final String HELP = "자동: 엔더 상자(실크터치 곡괭이 있을 때) → 셜커 상자 → 지정한 상자 순서";

	private static final double REACH = 4.3;
	/** Shulker boxes found full in this session (same contents = same components): not tried again. */
	private static final List<ItemStack> FULL = new ArrayList<>();
	/** Finished trips that put something away, this session (for the HUD and the tests). */
	public static int trips;
	/** Ender chests this mod put down and had to leave. */
	private static final List<BlockPos> LEFT = new ArrayList<>();

	public enum Kind { NONE, ENDER_SILK, ENDER_REUSE, ENDER_LEAVE, SHULKER }

	private final String mod;
	private final Kind kind;
	private final IntPredicate store;
	private final Predicate<BlockPos> avoid;
	private int stage, delay, ticks, moved, prevSlot = -1, tries;
	private BlockPos box;
	private Direction face;
	private Vec3 aim;
	private float savedYaw, savedPitch;
	private boolean placedByUs, full;
	private ItemStack placedStack = ItemStack.EMPTY;
	/** The other shulker boxes we carry, to tell which one came back (its contents changed). */
	private final List<ItemStack> otherShulkers = new ArrayList<>();
	private boolean deposited;
	private int walkTicks;
	/** The trip under way, so a mod that drops its queue (to run from a mob) can pick it up again. */
	private static PortableStorage current;
	private String reason = "";

	/**
	 * @param store which inventory indices (0..35) to put in the box
	 * @param avoid cells the box may not be put in (only checked for an ender chest that will be left behind,
	 *              everything else is picked up again a few seconds later)
	 */
	private PortableStorage(String mod, Kind kind, IntPredicate store, Predicate<BlockPos> avoid) {
		this.mod = mod;
		this.kind = kind;
		this.store = store;
		this.avoid = avoid;
	}

	// ------------------------------------------------------------------ choosing

	/**
	 * Which box to use for setting {@code mode}, or {@link Kind#NONE} (then: designated chests, or stop).
	 * {@code canLeave}: whether leaving an ender chest behind is acceptable (the mod has a free cell for it).
	 */
	public static Kind choose(LocalPlayer p, int mode, boolean canLeave) {
		if (p == null || mode == CHEST) return Kind.NONE;
		boolean ender = InvBase.count(p, PortableStorage::isEnderItem) > 0;
		boolean silk = InvBase.count(p, PortableStorage::isSilkPick) > 0;
		boolean nearEnder = nearEnderChest(p) != null;
		boolean shulker = InvBase.count(p, PortableStorage::usableShulker) > 0;
		if (mode == ENDER || mode == AUTO) {
			if (nearEnder) return Kind.ENDER_REUSE;
			if (ender && silk) return Kind.ENDER_SILK;
		}
		if ((mode == SHULKER || mode == AUTO) && shulker) return Kind.SHULKER;
		if (mode == ENDER && ender && canLeave) return Kind.ENDER_LEAVE;
		return Kind.NONE;
	}

	/** Why {@link #choose} found nothing, for the message when there are no chests either. */
	public static String whyNone(LocalPlayer p, int mode) {
		if (mode == ENDER) {
			if (InvBase.count(p, PortableStorage::isEnderItem) == 0) return "엔더 상자가 없어요";
			if (InvBase.count(p, PortableStorage::isSilkPick) == 0) return "실크터치 곡괭이가 없어 엔더 상자를 둘 곳이 없어요";
			return "엔더 상자를 놓을 자리가 없어요";
		}
		if (mode == SHULKER) return InvBase.count(p, PortableStorage::isShulkerItem) == 0 ? "셜커 상자가 없어요" : "셜커 상자가 모두 가득 찼어요";
		return "들고 다니는 보관함이 없어요";
	}

	/** After a failed try, wait a minute before trying a box again (the mod falls back to chests meanwhile). */
	private static long blockedUntil;

	/**
	 * The usual way in: a storage trip into a box for setting {@code mode}, or null when there is none to use (or
	 * one just failed), in which case the mod goes to its chests or stops as before.
	 */
	public static PortableStorage trip(String mod, LocalPlayer p, int mode, IntPredicate store, Predicate<BlockPos> avoid, boolean canLeave) {
		if (mode == CHEST || System.currentTimeMillis() < blockedUntil) return null;
		Kind k = choose(p, mode, canLeave);
		if (k == Kind.NONE) return null;
		return create(mod, k, store, avoid);
	}

	/** What the HUD can say while this runs. */
	public String label() {
		return kind == Kind.SHULKER ? "셜커 상자에 보관하는 중" : "엔더 상자에 보관하는 중";
	}

	public static PortableStorage create(String mod, Kind kind, IntPredicate store, Predicate<BlockPos> avoid) {
		return new PortableStorage(mod, kind, store, avoid == null ? q -> false : avoid);
	}

	public static boolean isShulkerItem(ItemStack s) {
		return !s.isEmpty() && Block.byItem(s.getItem()) instanceof ShulkerBoxBlock;
	}

	public static boolean isEnderItem(ItemStack s) {
		return !s.isEmpty() && s.is(Items.ENDER_CHEST);
	}

	/** Boxes are never stored into boxes (a shulker box will not take them anyway). */
	public static boolean isPortable(ItemStack s) {
		return isShulkerItem(s) || isEnderItem(s);
	}

	private static boolean usableShulker(ItemStack s) {
		if (!isShulkerItem(s)) return false;
		for (ItemStack f : FULL) if (ItemStack.isSameItemSameComponents(f, s)) return false;
		return true;
	}

	public static boolean isSilkPick(ItemStack s) {
		if (s.isEmpty() || !s.is(net.minecraft.tags.ItemTags.PICKAXES)) return false;
		if (s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() < 4) return false;
		ItemEnchantments e = s.get(DataComponents.ENCHANTMENTS);
		if (e == null) return false;
		for (Holder<Enchantment> h : e.keySet()) if ("minecraft:silk_touch".equals(h.getRegisteredName())) return true;
		return false;
	}

	/** An ender chest standing within reach (one we left earlier, or the player's own), usable from here. */
	private static BlockPos nearEnderChest(LocalPlayer p) {
		BlockPos f = p.blockPosition();
		BlockPos best = null;
		double bd = REACH;
		for (BlockPos q : BlockPos.betweenClosed(f.offset(-4, -2, -4), f.offset(4, 3, 4))) {
			if (!Blocks2.state(q).is(Blocks.ENDER_CHEST)) continue;
			if (Blocks2.state(q.above()).isRedstoneConductor(Blocks2.level(), q.above())) continue; // cannot open
			double d = p.getEyePosition().distanceTo(Vec3.atCenterOf(q));
			if (d < bd) {
				bd = d;
				best = q.immutable();
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ the trip

	@Override
	public int failMode() {
		return SKIP;
	}

	@Override
	public String failReason() {
		return reason;
	}

	/** Moved something into the box, and whether the box filled up. */
	public int moved() {
		return moved;
	}

	public boolean filledUp() {
		return full;
	}

	@Override
	public int tick(Minecraft mc, LocalPlayer p) {
		if (++ticks > 20 * 60) return fail(mc, p, "보관함 정리가 너무 오래 걸려요");
		switch (stage) {
			case 0 -> { // where
				current = this;
				savedYaw = p.getYRot();
				savedPitch = p.getXRot();
				prevSlot = p.getInventory().getSelectedSlot();
				Body.release(mc);
				if (kind == Kind.ENDER_REUSE) {
					box = nearEnderChest(p);
					if (box == null) return fail(mc, p, "엔더 상자가 없어졌어요");
					placedByUs = false;
					stage = 4;
					return RUN;
				}
				box = spot(p, kind == Kind.ENDER_LEAVE);
				if (box == null) return fail(mc, p, "보관함을 놓을 자리가 없어요");
				stage = 1;
				return RUN;
			}
			case 1 -> { // box in hand
				Predicate<ItemStack> which = kind == Kind.SHULKER ? PortableStorage::usableShulker : PortableStorage::isEnderItem;
				int h = InvBase.bringToHotbar(mc, p, which, PortableStorage::worthKeepingInHotbar);
				if (h < 0) return fail(mc, p, "보관함을 손에 들지 못했어요");
				InvBase.select(p, h);
				placedStack = p.getInventory().getItem(h).copy();
				otherShulkers.clear();
				for (int i = 0; i < 36; i++) {
					ItemStack o = p.getInventory().getItem(i);
					if (i != h && isShulkerItem(o)) otherShulkers.add(o.copy());
				}
				face = Direction.UP;
				aim = new Vec3(box.getX() + 0.5 + Human.jitter(0.15), box.getY(), box.getZ() + 0.5 + Human.jitter(0.15));
				Body.newTurn();
				delay = Human.delay(3, 8);
				stage = 2;
				return RUN;
			}
			case 2 -> { // look at the floor there and put it down
				if (!Body.lookAt(p, aim, 3f) || delay-- > 0) return RUN;
				ItemStack held = p.getMainHandItem();
				if (!(kind == Kind.SHULKER ? isShulkerItem(held) : isEnderItem(held))) {
					stage = 1;
					if (++tries > 3) return fail(mc, p, "보관함을 손에 들지 못했어요");
					return RUN;
				}
				mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(aim, face, box.below(), false));
				p.swing(InteractionHand.MAIN_HAND);
				delay = 30;
				stage = 3;
				return RUN;
			}
			case 3 -> { // wait until it stands there
				if (isBox(Blocks2.state(box))) {
					placedByUs = true;
					if (kind == Kind.ENDER_LEAVE) {
						LEFT.add(box);
						UsageLog.event(mod, "엔더 상자를 두고 감 (실크터치 곡괭이 없음): " + box.toShortString());
					}
					delay = Human.delay(2, 6);
					stage = 4;
					return RUN;
				}
				if (--delay <= 0) {
					if (++tries > 3) return fail(mc, p, "보관함을 놓지 못했어요");
					stage = 2;
					delay = Human.delay(4, 8);
				}
				return RUN;
			}
			case 4 -> { // look at it and open
				if (!isBox(Blocks2.state(box))) return fail(mc, p, "보관함이 없어졌어요");
				if (!inReach(mc, p)) return RUN;
				if (delay-- > 0) return RUN;
				face = Aim.faceToward(p.getEyePosition(), box);
				aim = Aim.facePoint(box, face, 0.1);
				Body.newTurn();
				stage = 5;
				return RUN;
			}
			case 5 -> {
				if (!Body.lookAt(p, aim, 3f)) return RUN;
				if (p.getEyePosition().distanceTo(aim) > REACH + 0.5) return fail(mc, p, "보관함에 손이 닿지 않아요");
				mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(aim, face, box, false));
				p.swing(InteractionHand.MAIN_HAND);
				delay = 40;
				stage = 6;
				return RUN;
			}
			case 6 -> { // wait for the screen
				if (Chests.openScreen(mc) != null) {
					delay = Human.delay(3, 8);
					stage = 7;
					return RUN;
				}
				if (--delay <= 0) {
					if (++tries > 4) {
						stage = 9; // take the box back anyway
						reason = "보관함을 열지 못했어요";
						return RUN;
					}
					stage = 4;
				}
				return RUN;
			}
			case 7 -> { // shift-click the loot in
				AbstractContainerScreen<?> s = Chests.openScreen(mc);
				if (s == null) {
					stage = 9;
					return RUN;
				}
				if (delay-- > 0) return RUN;
				if (depositOne(mc, p, s.getMenu())) {
					delay = Human.delay(1, 4);
					return RUN;
				}
				deposited = true;
				delay = Human.delay(3, 8);
				stage = 8;
				return RUN;
			}
			case 8 -> { // close
				if (delay-- > 0) return RUN;
				if (Chests.openScreen(mc) != null) p.closeContainer();
				delay = Human.delay(3, 8);
				stage = 9;
				return RUN;
			}
			case 9 -> { // take it back, if it comes back
				if (delay-- > 0) return RUN;
				if (!placedByUs || kind == Kind.ENDER_LEAVE || kind == Kind.ENDER_REUSE) {
					stage = 13;
					return RUN;
				}
				if (!isBox(Blocks2.state(box))) {
					delay = 40;
					stage = 12;
					return RUN;
				}
				if (!inReach(mc, p)) return RUN;
				Predicate<ItemStack> tool = kind == Kind.ENDER_SILK ? PortableStorage::isSilkPick
						: s -> s.is(net.minecraft.tags.ItemTags.PICKAXES) && (!s.isDamageableItem() || s.getMaxDamage() - s.getDamageValue() > 3);
				int h = InvBase.bringToHotbar(mc, p, tool, PortableStorage::worthKeepingInHotbar);
				if (h < 0 && kind == Kind.ENDER_SILK) {
					// The pickaxe is gone (broke?): leave the ender chest rather than turn it into obsidian.
					LEFT.add(box);
					UsageLog.event(mod, "엔더 상자를 두고 감 (실크터치 곡괭이가 없어짐): " + box.toShortString());
					stage = 13;
					return RUN;
				}
				if (h < 0) h = emptyHandSlot(p);
				InvBase.select(p, h);
				face = Aim.faceToward(p.getEyePosition(), box);
				aim = Aim.facePoint(box, face, 0.1);
				Body.newTurn();
				delay = Human.delay(2, 5);
				ticks = Math.min(ticks, 20 * 50);
				stage = 10;
				return RUN;
			}
			case 10 -> {
				if (!Body.lookAt(p, aim, 2.5f) || delay-- > 0) return RUN;
				stage = 11;
				delay = 400;
				return RUN;
			}
			case 11 -> { // break it
				if (!isBox(Blocks2.state(box))) {
					mc.gameMode.stopDestroyBlock();
					delay = 40;
					stage = 12;
					return RUN;
				}
				if (kind == Kind.ENDER_SILK && !isSilkPick(p.getMainHandItem())) {
					mc.gameMode.stopDestroyBlock();
					stage = 9;
					return RUN;
				}
				Body.lookAt(p, aim, 1.5f);
				mc.gameMode.continueDestroyBlock(box, face);
				p.swing(InteractionHand.MAIN_HAND);
				if (--delay <= 0) {
					mc.gameMode.stopDestroyBlock();
					LEFT.add(box);
					UsageLog.event(mod, "보관함을 다시 줍지 못함: " + box.toShortString());
					stage = 13;
				}
				return RUN;
			}
			case 12 -> { // pick the dropped box up (it pops out next to us; walk onto it if it rolled away)
				ItemEntity drop = dropNear(p);
				if (drop == null || drop.isRemoved()) {
					Body.release(mc);
					stage = 13;
					return RUN;
				}
				if (--delay <= 0) {
					BlockPos at = drop.blockPosition();
					Body.walkTo(p, at);
					if (delay < -100) {
						Body.release(mc);
						UsageLog.event(mod, "보관함이 굴러가 줍지 못함: " + at.toShortString());
						Alerts.actionBar("보관함을 줍지 못했어요: " + at.toShortString(), 0xFFFF5555);
						stage = 13;
					}
				}
				return RUN;
			}
			default -> { // hands and eyes back the way they were
				if (prevSlot >= 0) InvBase.select(p, prevSlot);
				if (!Body.turnTo(p, savedYaw, savedPitch, 3f)) return RUN;
				if (kind == Kind.SHULKER && full) {
					// The one that came back is the shulker box whose contents match none we carried before.
					for (int i = 0; i < 36; i++) {
						ItemStack o = p.getInventory().getItem(i);
						if (!isShulkerItem(o)) continue;
						boolean old = false;
						for (ItemStack k : otherShulkers) if (ItemStack.isSameItemSameComponents(k, o)) old = true;
						if (!old) FULL.add(o.copy());
					}
				}
				current = null;
				if (!reason.isEmpty()) {
					blockedUntil = System.currentTimeMillis() + 60_000;
					Alerts.actionBar(reason, 0xFFFFAA00);
					return FAIL;
				}
				String what = kind == Kind.SHULKER ? "셜커 상자" : "엔더 상자";
				Alerts.actionBar(what + "에 보관 (" + moved + "칸)" + (kind == Kind.ENDER_LEAVE ? " · 엔더 상자는 " + box.toShortString() + "에 두었어요" : ""), 0xFF55FF55);
				UsageLog.event(mod, what + "에 보관 " + moved + "칸");
				if (moved > 0) trips++;
				return DONE;
			}
		}
	}

	private int fail(Minecraft mc, LocalPlayer p, String why) {
		if (current == this && !placedByUs) current = null;
		reason = why;
		Body.release(mc);
		if (Chests.openScreen(mc) != null) p.closeContainer();
		if (prevSlot >= 0) InvBase.select(p, prevSlot);
		UsageLog.event(mod, "보관함 사용 실패: " + why);
		Alerts.actionBar(why, 0xFFFFAA00);
		blockedUntil = System.currentTimeMillis() + 60_000;
		return FAIL;
	}

	/**
	 * A mod that threw its queue away in the middle of this (to run from a mob, say) asks for it back here: the box
	 * may still be standing there with our things in it. Null if nothing is left to finish.
	 */
	public static Action interrupted(Minecraft mc) {
		PortableStorage c = current;
		if (c == null || !c.placedByUs || c.stage >= 13) return null;
		if (mc.player != null && Chests.openScreen(mc) != null) mc.player.closeContainer();
		c.stage = c.deposited ? 9 : 4;
		c.delay = Human.delay(4, 10);
		c.walkTicks = 0;
		c.ticks = 0;
		return c;
	}

	/** Within reach of the box, walking up to it first if something moved us away. */
	private boolean inReach(Minecraft mc, LocalPlayer p) {
		if (p.getEyePosition().distanceTo(Vec3.atCenterOf(box)) <= REACH) {
			if (walkTicks > 0) {
				Body.release(mc);
				walkTicks = 0;
				Body.newTurn();
			}
			return true;
		}
		if (walkTicks++ == 0) Body.resetWalk();
		BlockPos best = null;
		double bd = Double.MAX_VALUE;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int dy = -1; dy <= 1; dy++) {
				BlockPos c = box.relative(d).above(dy);
				if (!Blocks2.walkable(c)) continue;
				double dist = c.distSqr(p.blockPosition());
				if (dist < bd) {
					bd = dist;
					best = c;
				}
			}
		}
		if (best == null || walkTicks > 300) {
			Body.release(mc);
			LEFT.add(box);
			UsageLog.event(mod, "보관함으로 돌아가지 못함: " + box.toShortString());
			Alerts.actionBar("보관함을 두고 왔어요: " + box.toShortString(), 0xFFFF5555);
			reason = "보관함으로 돌아가지 못했어요 (" + box.toShortString() + ")";
			stage = 13;
			return false;
		}
		Body.walkTo(p, best);
		return false;
	}

	private static boolean isBox(BlockState s) {
		return s.is(Blocks.ENDER_CHEST) || s.getBlock() instanceof ShulkerBoxBlock;
	}

	private boolean depositOne(Minecraft mc, LocalPlayer p, AbstractContainerMenu menu) {
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.slots.get(i);
			if (!(slot.container instanceof Inventory)) continue;
			int inv = slot.getContainerSlot();
			if (inv >= 36) continue;
			ItemStack s = slot.getItem();
			if (s.isEmpty() || isPortable(s) || !store.test(inv)) continue;
			int before = s.getCount();
			ItemStack copy = s.copy();
			mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, p);
			ItemStack after = slot.getItem();
			if (!after.isEmpty() && after.getCount() == before && ItemStack.isSameItemSameComponents(after, copy)) {
				full = true;
				return false;
			}
			moved++;
			return true;
		}
		return false;
	}

	/** A dropped box item lying close by. */
	private ItemEntity dropNear(LocalPlayer p) {
		AABB a = new AABB(box).inflate(4);
		for (Entity e : p.level().getEntities(p, a)) {
			if (e instanceof ItemEntity ie && isPortable(ie.getItem())) return ie;
		}
		return null;
	}

	/**
	 * A cell right next to us where a box fits: open, with a solid floor and room above (a shulker box opens upward,
	 * an ender chest will not open under a solid block), within reach, not in our own body, no mob in it.
	 */
	private BlockPos spot(LocalPlayer p, boolean leaving) {
		BlockPos feet = p.blockPosition();
		AABB body = p.getBoundingBox();
		BlockPos best = null;
		double bd = Double.MAX_VALUE;
		for (int dy = 0; dy >= -1; dy--) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) continue;
					BlockPos c = feet.offset(dx, dy, dz);
					if (!Blocks2.isOpen(c) || Blocks2.isFluid(c) || !Blocks2.isOpen(c.above()) || Blocks2.isFluid(c.above())) continue;
					if (!Blocks2.isSolid(c.below()) || Blocks2.level().getBlockEntity(c.below()) != null) continue;
					if (Blocks2.state(c.below()).getMenuProvider(Blocks2.level(), c.below()) != null) continue;
					if (new AABB(c).intersects(body)) continue;
					if (!p.level().getEntities(p, new AABB(c)).isEmpty()) continue;
					if (leaving && avoid.test(c)) continue;
					if (p.getEyePosition().distanceTo(Vec3.atCenterOf(c)) > REACH) continue;
					// Prefer behind and beside us over in front (where the work is), and level over a step down.
					double d = Math.abs(dx) + Math.abs(dz) - dy * 2 + (avoid.test(c) ? 3 : 0);
					if (d < bd) {
						bd = d;
						best = c.immutable();
					}
				}
			}
		}
		return best;
	}

	private static boolean worthKeepingInHotbar(ItemStack s) {
		return !s.isEmpty() && (s.isDamageableItem() || InvBase.isFood(s));
	}

	private static int emptyHandSlot(LocalPlayer p) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < 9; i++) if (inv.getItem(i).isEmpty()) return i;
		for (int i = 0; i < 9; i++) if (!inv.getItem(i).isDamageableItem()) return i;
		return inv.getSelectedSlot();
	}
}
