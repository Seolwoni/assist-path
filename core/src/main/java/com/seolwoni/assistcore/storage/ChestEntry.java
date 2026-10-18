package com.seolwoni.assistcore.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

/** A storage chest marked with K. Saved in each mod's own settings file. */
public class ChestEntry {
	public int x, y, z;
	/** Item ids this chest takes. Empty = overflow chest, filled in order. */
	public List<String> items = new ArrayList<>();

	public BlockPos pos() {
		return new BlockPos(x, y, z);
	}
}
