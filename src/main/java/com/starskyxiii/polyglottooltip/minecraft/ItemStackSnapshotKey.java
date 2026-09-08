package com.starskyxiii.polyglottooltip.minecraft;

import net.minecraft.world.item.ItemStack;
//? if <=1.20.1 {
/*import java.util.Objects;
*///?}

public final class ItemStackSnapshotKey {
    private final ItemStack stackSnapshot;
    private final int hash;

    private ItemStackSnapshotKey(ItemStack stackSnapshot) {
        this.stackSnapshot = stackSnapshot;
        //? if <=1.20.1 {
        /*this.hash = Objects.hash(stackSnapshot.getItem(), stackSnapshot.getTag());
        *///?} else {
        this.hash = ItemStack.hashItemAndComponents(stackSnapshot);
        //?}
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
        //? if <=1.20.1 {
        /*return ItemStack.matches(stackSnapshot, other.stackSnapshot);
        *///?} else {
        return ItemStack.isSameItemSameComponents(stackSnapshot, other.stackSnapshot);
        //?}
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
