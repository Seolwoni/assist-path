package com.seolwoni.assistcore.gui;

import java.util.function.IntConsumer;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider for an integer setting. */
public class IntSlider extends AbstractSliderButton {
	private final String label;
	private final String unit;
	private final int min, max;
	private final IntConsumer onChange;

	public IntSlider(int x, int y, int w, String label, String unit, int min, int max, int current, IntConsumer onChange) {
		super(x, y, w, 20, Component.empty(), (current - min) / (double) Math.max(1, max - min));
		this.label = label;
		this.unit = unit;
		this.min = min;
		this.max = max;
		this.onChange = onChange;
		updateMessage();
	}

	private int current() {
		return min + (int) Math.round(this.value * (max - min));
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(label + ": " + current() + unit));
	}

	@Override
	protected void applyValue() {
		onChange.accept(current());
	}
}
