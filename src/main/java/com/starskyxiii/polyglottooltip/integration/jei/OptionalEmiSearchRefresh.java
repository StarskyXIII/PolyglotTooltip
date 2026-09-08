package com.starskyxiii.polyglottooltip.integration.jei;

import com.starskyxiii.polyglottooltip.PolyglotTooltip;
import net.minecraft.client.Minecraft;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class OptionalEmiSearchRefresh {
    private static final String EMI_API_CLASS = "dev.emi.emi.api.EmiApi";
    private static final String EMI_SEARCH_CLASS = "dev.emi.emi.search.EmiSearch";
    private static final String EMI_RELOAD_MANAGER_CLASS = "dev.emi.emi.runtime.EmiReloadManager";
    private static final long RETRY_DELAY_MILLIS = 100L;
    private static final PendingRefreshState PENDING = new PendingRefreshState();
    private static Object retryRegistration;

    private OptionalEmiSearchRefresh() {
    }

    public static void register() {
        Object registration = PENDING.registerIfAbsent();
        JeiSearchRefreshBridge.setEmiRefreshActions(
                () -> scheduleRefresh(registration, RefreshKind.SEARCH),
                () -> scheduleRefresh(registration, RefreshKind.REBUILD)
        );
    }

    private static void scheduleRefresh(Object registration, RefreshKind kind) {
        Minecraft.getInstance().execute(() -> {
            if (PENDING.request(registration, kind)) {
                flushPendingRefresh(registration);
            }
        });
    }

    static void refreshOnClientThread(boolean rebuildSearchIndex) {
        Object registration = PENDING.currentRegistration();
        if (registration != null && PENDING.request(
                registration, rebuildSearchIndex ? RefreshKind.REBUILD : RefreshKind.SEARCH)) {
            flushPendingRefresh(registration);
        }
    }

    private static void flushPendingRefresh(Object registration) {
        if (!PENDING.hasPending(registration)) {
            return;
        }

        RefreshKind claimed = RefreshKind.NONE;
        try {
            ClassLoader loader = OptionalEmiSearchRefresh.class.getClassLoader();
            if (!isEmiLoaded(loader)) {
                scheduleRetry(registration);
                return;
            }

            claimed = PENDING.claim(registration);
            if (claimed == RefreshKind.NONE) {
                return;
            }
            Class<?> emiApi = Class.forName(EMI_API_CLASS, true, loader);
            Class<?> emiSearch = Class.forName(EMI_SEARCH_CLASS, true, loader);
            Method getSearchText = emiApi.getMethod("getSearchText");
            Method search = emiSearch.getMethod("search", String.class);
            String query = (String) getSearchText.invoke(null);
            if (claimed == RefreshKind.REBUILD) {
                emiSearch.getMethod("bake").invoke(null);
                if (!isEmiLoaded(loader)) {
                    PENDING.restore(registration);
                    scheduleRetry(registration);
                    return;
                }
            }
            search.invoke(null, query);
            PENDING.complete(registration);
            if (PENDING.hasPending(registration)) {
                flushPendingRefresh(registration);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Throwable cause = unwrap(exception);
            if (claimed == RefreshKind.NONE) {
                PENDING.drop(registration);
                PolyglotTooltip.LOGGER.warn("Unable to refresh EMI search results", cause);
                return;
            }
            try {
                if (isEmiLoaded(OptionalEmiSearchRefresh.class.getClassLoader())) {
                    PENDING.drop(registration);
                    PolyglotTooltip.LOGGER.warn("Unable to refresh EMI search results", cause);
                    return;
                }
                PENDING.restore(registration);
                scheduleRetry(registration);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError readinessException) {
                PENDING.drop(registration);
                PolyglotTooltip.LOGGER.warn("Unable to determine EMI reload readiness", unwrap(readinessException));
            }
        }
    }

    private static boolean isEmiLoaded(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> reloadManager = Class.forName(EMI_RELOAD_MANAGER_CLASS, true, loader);
        return (boolean) reloadManager.getMethod("isLoaded").invoke(null);
    }

    private static void scheduleRetry(Object registration) {
        synchronized (OptionalEmiSearchRefresh.class) {
            if (retryRegistration == registration || !PENDING.hasPending(registration)) {
                return;
            }
            retryRegistration = registration;
        }
        CompletableFuture.delayedExecutor(RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS).execute(() ->
                Minecraft.getInstance().execute(() -> {
                    synchronized (OptionalEmiSearchRefresh.class) {
                        if (retryRegistration == registration) {
                            retryRegistration = null;
                        }
                    }
                    flushPendingRefresh(registration);
                }));
    }

    private static Throwable unwrap(Throwable exception) {
        return exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : exception;
    }

    enum RefreshKind {
        NONE,
        SEARCH,
        REBUILD;

        private static RefreshKind merge(RefreshKind first, RefreshKind second) {
            return first.ordinal() >= second.ordinal() ? first : second;
        }
    }

    static final class PendingRefreshState {
        private Object registration;
        private RefreshKind pending = RefreshKind.NONE;
        private RefreshKind claimed = RefreshKind.NONE;

        synchronized Object registerIfAbsent() {
            if (registration == null) {
                registration = new Object();
            }
            return registration;
        }

        synchronized Object currentRegistration() {
            return registration;
        }

        synchronized boolean request(Object expectedRegistration, RefreshKind kind) {
            if (registration != expectedRegistration || kind == RefreshKind.NONE) {
                return false;
            }
            pending = RefreshKind.merge(pending, kind);
            return true;
        }

        synchronized boolean hasPending(Object expectedRegistration) {
            return registration == expectedRegistration && pending != RefreshKind.NONE;
        }

        synchronized RefreshKind claim(Object expectedRegistration) {
            if (registration != expectedRegistration) {
                return RefreshKind.NONE;
            }
            claimed = pending;
            pending = RefreshKind.NONE;
            return claimed;
        }

        synchronized void restore(Object expectedRegistration) {
            if (registration == expectedRegistration) {
                pending = RefreshKind.merge(pending, claimed);
                claimed = RefreshKind.NONE;
            }
        }

        synchronized void complete(Object expectedRegistration) {
            if (registration == expectedRegistration) {
                claimed = RefreshKind.NONE;
            }
        }

        synchronized void drop(Object expectedRegistration) {
            if (registration == expectedRegistration) {
                pending = RefreshKind.NONE;
                claimed = RefreshKind.NONE;
            }
        }
    }
}
