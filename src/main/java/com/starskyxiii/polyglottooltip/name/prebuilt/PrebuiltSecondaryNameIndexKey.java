package com.starskyxiii.polyglottooltip.name.prebuilt;

/**
 * Key for the prebuilt secondary name cache: registry name + item damage.
 * NBT-sensitive variants are out of scope for this MVP.
 */
public final class PrebuiltSecondaryNameIndexKey {

    public final String registryName;
    public final int damage;
    private final int hashCode;

    public PrebuiltSecondaryNameIndexKey(String registryName, int damage) {
        this.registryName = registryName == null ? "" : registryName;
        this.damage = damage;
        this.hashCode = 31 * this.registryName.hashCode() + damage;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PrebuiltSecondaryNameIndexKey)) {
            return false;
        }
        PrebuiltSecondaryNameIndexKey other = (PrebuiltSecondaryNameIndexKey) obj;
        return damage == other.damage && registryName.equals(other.registryName);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
