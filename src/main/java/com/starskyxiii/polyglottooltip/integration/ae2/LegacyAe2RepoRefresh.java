package com.starskyxiii.polyglottooltip.integration.ae2;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.me.items.ItemRepo;
import appeng.container.me.common.GridInventoryEntry;
import com.starskyxiii.polyglottooltip.minecraft.MinecraftLanguageAdapter;
import net.minecraft.client.Minecraft;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class LegacyAe2RepoRefresh {
    private static final List<WeakReference<Repo<?>>> REPOS = new ArrayList<>();
    private static boolean registered;

    private LegacyAe2RepoRefresh() {
    }

    public static synchronized void register(Repo<?> repo) {
        REPOS.add(new WeakReference<>(repo));
        if (!registered) {
            registered = true;
            MinecraftLanguageAdapter.getInstance().registerDependentCacheInvalidator(LegacyAe2RepoRefresh::refreshAll);
        }
    }

    private static void refreshAll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(LegacyAe2RepoRefresh::refreshNow);
        }
    }

    private static synchronized void refreshNow() {
        Iterator<WeakReference<Repo<?>>> iterator = REPOS.iterator();
        while (iterator.hasNext()) {
            Repo<?> repo = iterator.next().get();
            if (repo == null) {
                iterator.remove();
                continue;
            }
            if (repo instanceof ItemRepo) {
                ItemRepo itemRepo = (ItemRepo) repo;
                for (GridInventoryEntry<IAEItemStack> entry : itemRepo.getAllEntries()) {
                    IAEItemStack stack = entry.getStack();
                    if (stack instanceof CachedItemStack) {
                        ((CachedItemStack) stack).polyglottooltip$clearLanguageCaches();
                    }
                }
            }
            repo.updateView();
        }
    }

    public interface CachedItemStack {
        void polyglottooltip$clearLanguageCaches();
    }
}
