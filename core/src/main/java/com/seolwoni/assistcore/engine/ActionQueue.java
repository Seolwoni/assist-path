package com.seolwoni.assistcore.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** A queue of actions run front to back, one tick at a time. */
public final class ActionQueue {
	private final Deque<Action> queue = new ArrayDeque<>();
	/** Actions that have been ticked at least once (identity set). */
	private final java.util.Set<Action> started = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	private final Consumer<Action> onFail;

	/** {@code onFail} is called with an action that returned FAIL, after it was removed from the queue. */
	public ActionQueue(Consumer<Action> onFail) {
		this.onFail = onFail;
	}

	public void add(Action a) {
		queue.addLast(a);
	}

	public void run(Runnable r) {
		add((mc, p) -> {
			r.run();
			return Action.DONE;
		});
	}

	public void pushFront(Action a) {
		queue.addFirst(a);
	}

	/** Puts the list in front of the queue, keeping its order. */
	public void pushFront(List<? extends Action> list) {
		for (int i = list.size() - 1; i >= 0; i--) queue.addFirst(list.get(i));
	}

	public void clear() {
		queue.clear();
		started.clear();
	}

	public boolean isEmpty() {
		return queue.isEmpty();
	}

	public int size() {
		return queue.size();
	}

	/**
	 * Ticks the action at the front. Returns false when there was nothing to run (time to plan more work).
	 * Actions that turn out to have nothing to do (finished on their very first tick, e.g. "dig a block that
	 * is already gone") do not use up the tick: the next one runs right away, up to a few per tick.
	 */
	public boolean tick(Minecraft mc, LocalPlayer p) {
		Action a = queue.peekFirst();
		if (a == null) return false;
		for (int chained = 0; a != null; chained++) {
			boolean fresh = started.add(a);
			int r = a.tick(mc, p);
			if (r == Action.DONE) {
				if (queue.peekFirst() == a) queue.pollFirst();
				else queue.remove(a);
				started.remove(a);
				if (!fresh || chained >= 8) break;
				a = queue.peekFirst();
			} else {
				if (r == Action.FAIL) {
					queue.remove(a);
					started.remove(a);
					onFail.accept(a);
				}
				break;
			}
		}
		return true;
	}
}
