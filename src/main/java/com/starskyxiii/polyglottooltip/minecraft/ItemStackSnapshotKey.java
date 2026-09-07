package com.starskyxiii.polyglottooltip.minecraft;

import net.minecraft.world.item.ItemStack;

public final class ItemStackSnapshotKey {
    private final ItemStack stackSnapshot;
    private final int hash;

    private ItemStackSnapshotKey(ItemStack stackSnapshot) {
        this.stackSnapshot = stackSnapshot;
        this.hash = ItemStack.hashItemAndComponents(stackSnapshot);
    }

    public static ItemStackSnapshotKey from(ItemStack stack) {
        return new ItemStackSnapshotKey(stack.copyWithCount(1));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ItemStackSnapshotKey other)) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(stackSnapshot, other.stackSnapshot);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
