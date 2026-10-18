package com.seolwoni.assistcore.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Pick a set of blocks or items: a preset list to tick on/off, plus search over everything in the game.
 * The given list is edited in place.
 */
public class PickerScreen extends Screen {
	private final Screen parent;
	private final List<String> list;
	private final boolean blocks;
	private final List<String> presets;
	private final String help;
	private final Runnable save;
	private String query = "";
	private int page;
	private boolean dirty;
	private final List<Object[]> icons = new ArrayList<>();
	private final List<Object[]> labels = new ArrayList<>();

	/** {@code save} persists the edited list (called after every change). */
	public PickerScreen(Screen parent, String title, List<String> list, boolean blocks, List<String> presets, String help, Runnable save) {
		super(Component.literal(title));
		this.parent = parent;
		this.list = list;
		this.blocks = blocks;
		this.presets = presets;
		this.help = help;
		this.save = save;
	}

	private ItemStack icon(String id) {
		Identifier key = Identifier.parse(id);
		if (blocks) {
			Block b = BuiltInRegistries.BLOCK.getValue(key);
			Item i = b.asItem();
			return i == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(i);
		}
		return new ItemStack(BuiltInRegistries.ITEM.getValue(key));
	}

	private String name(String id) {
		Identifier key = Identifier.parse(id);
		if (blocks) return BuiltInRegistries.BLOCK.getValue(key).getName().getString();
		return new ItemStack(BuiltInRegistries.ITEM.getValue(key)).getHoverName().getString();
	}

	private List<String> entries() {
		String q = query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			List<String> out = new ArrayList<>(presets);
			for (String s : list) if (!out.contains(s)) out.add(s);
			return out;
		}
		List<String> out = new ArrayList<>();
		if (blocks) {
			for (Block b : BuiltInRegistries.BLOCK) {
				String id = BuiltInRegistries.BLOCK.getKey(b).toString();
				if (id.contains(q) || b.getName().getString().toLowerCase(Locale.ROOT).contains(q)) out.add(id);
			}
		} else {
			for (Item i : BuiltInRegistries.ITEM) {
				if (i == Items.AIR) continue;
				String id = BuiltInRegistries.ITEM.getKey(i).toString();
				if (id.contains(q) || new ItemStack(i).getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) out.add(id);
			}
		}
		return out;
	}

	@Override
	protected void init() {
		icons.clear();
		labels.clear();
		int cx = this.width / 2;
		int left = cx - 155;
		EditBox box = new EditBox(this.font, left, 24, 230, 20, Component.literal("검색"));
		box.setMaxLength(50);
		box.setValue(query);
		box.setResponder(s -> {
			if (!s.equals(query)) {
				query = s;
				page = 0;
				dirty = true;
			}
		});
		addRenderableWidget(box);
		addRenderableWidget(Button.builder(Component.literal("모두 해제"), b -> {
			list.clear();
			save.run();
			rebuildWidgets();
		}).bounds(left + 236, 24, 74, 20).build());
		setInitialFocus(box);

		List<String> all = entries();
		int y = help != null ? 60 : 50;
		int perPage = Math.max(2, ((this.height - y - 56) / 22) * 2);
		int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
		page = Math.max(0, Math.min(page, pages - 1));
		int idx = 0;
		for (int i = page * perPage; i < Math.min(all.size(), (page + 1) * perPage); i++, idx++) {
			String id = all.get(i);
			boolean on = list.contains(id);
			int x = left + (idx % 2) * 158;
			int yy = y + (idx / 2) * 22;
			Component text = Component.literal("    " + (on ? "✔ " : "") + name(id)).withColor(on ? 0xFF55FF55 : 0xFFFFFFFF);
			addRenderableWidget(Button.builder(text, b -> {
				if (list.contains(id)) list.remove(id);
				else list.add(id);
				save.run();
				rebuildWidgets();
			}).bounds(x, yy, 152, 20).build());
			icons.add(new Object[] {icon(id), x + 3, yy + 2});
		}
		if (all.isEmpty()) labels.add(new Object[] {"찾는 게 없어요", cx, y + 8});
		if (pages > 1) {
			int py = this.height - 50;
			Button prev = Button.builder(Component.literal("◀"), b -> {
				page--;
				rebuildWidgets();
			}).bounds(cx - 60, py, 20, 20).build();
			prev.active = page > 0;
			Button next = Button.builder(Component.literal("▶"), b -> {
				page++;
				rebuildWidgets();
			}).bounds(cx + 40, py, 20, 20).build();
			next.active = page < pages - 1;
			addRenderableWidget(prev);
			addRenderableWidget(next);
			labels.add(new Object[] {(page + 1) + " / " + pages, cx, py + 6});
		}
		addRenderableWidget(Button.builder(Component.literal("완료"), b -> onClose())
				.bounds(cx - 75, this.height - 26, 150, 20).build());
	}

	@Override
	public void tick() {
		super.tick();
		if (dirty) {
			dirty = false;
			rebuildWidgets();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.centeredText(this.font, Component.literal(this.title.getString() + " (" + list.size() + "개 선택)"), this.width / 2, 8, 0xFFFFFFFF);
		for (Object[] i : icons) g.item((ItemStack) i[0], (int) i[1], (int) i[2]);
		for (Object[] l : labels) g.centeredText(this.font, Component.literal((String) l[0]), (int) l[1], (int) l[2], 0xFFAAAAAA);
		if (help != null) g.centeredText(this.font, Component.literal(help), this.width / 2, 48, 0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		save.run();
		this.minecraft.gui.setScreen(parent);
	}
}
