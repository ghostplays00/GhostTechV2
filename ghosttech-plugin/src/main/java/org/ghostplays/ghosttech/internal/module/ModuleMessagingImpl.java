package org.ghostplays.ghosttech.internal.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.ghostplays.ghosttech.api.module.ModuleMessaging;
import org.ghostplays.ghosttech.api.util.GhostId;

/**
 * Channel-based {@link ModuleMessaging}.
 *
 * <h2>Isolation</h2>
 * Subscriptions are tracked per owning module, so unloading a module removes its subscriptions
 * automatically. Without that, a disabled module would keep receiving messages and could throw on
 * every publish.
 *
 * <h2>Delivery</h2>
 * Delivery is synchronous on the calling thread, which is always the main server thread. Handlers
 * must stay off disk and off the network. A handler that throws is logged and skipped so one broken
 * module cannot stop delivery to the others.
 */
public final class ModuleMessagingImpl implements ModuleMessaging {

    /**
     * One subscription.
     *
     * @param owner   the module that subscribed
     * @param handler receives a message and may reply
     */
    private record Subscription(GhostId owner, Function<Object, Object> handler) {
    }

    private final Map<String, List<Subscription>> channels = new ConcurrentHashMap<>();
    private final java.util.logging.Logger logger;

    /**
     * @param logger used to report handler failures
     */
    public ModuleMessagingImpl(java.util.logging.Logger logger) {
        this.logger = logger;
    }

    @Override
    public int publish(String channel, Object payload, GhostId senderId) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("Channel must not be blank");
        }
        List<Subscription> subscriptions = channels.get(channel);
        if (subscriptions == null || subscriptions.isEmpty()) {
            return 0;
        }
        int handled = 0;
        // Iterate a copy: a handler is allowed to subscribe or unsubscribe during delivery.
        for (Subscription subscription : new ArrayList<>(subscriptions)) {
            try {
                subscription.handler().apply(payload);
                handled++;
            } catch (RuntimeException exception) {
                logger.log(java.util.logging.Level.WARNING,
                        "Module " + subscription.owner() + " failed handling a message on channel "
                                + channel, exception);
            }
        }
        return handled;
    }

    @Override
    public Object request(String channel, Object payload, GhostId senderId) {
        List<Subscription> subscriptions = channels.get(channel);
        if (subscriptions == null) {
            return null;
        }
        for (Subscription subscription : new ArrayList<>(subscriptions)) {
            try {
                Object reply = subscription.handler().apply(payload);
                if (reply != null) {
                    return reply;
                }
            } catch (RuntimeException exception) {
                logger.log(java.util.logging.Level.WARNING,
                        "Module " + subscription.owner() + " failed handling a request on channel "
                                + channel, exception);
            }
        }
        return null;
    }

    @Override
    public void subscribe(String channel, GhostId owner, Function<Object, Object> handler) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("Channel must not be blank");
        }
        if (owner == null) {
            throw new IllegalArgumentException("A subscription needs an owning module");
        }
        channels.computeIfAbsent(channel, key -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(new Subscription(owner, handler));
    }

    @Override
    public void unsubscribeAll(GhostId owner) {
        if (owner == null) {
            return;
        }
        for (List<Subscription> subscriptions : channels.values()) {
            subscriptions.removeIf(subscription -> subscription.owner().equals(owner));
        }
        // Drop channels that no longer have subscribers, so hasSubscribers stays accurate.
        channels.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    @Override
    public boolean hasSubscribers(String channel) {
        List<Subscription> subscriptions = channels.get(channel);
        return subscriptions != null && !subscriptions.isEmpty();
    }

    /** Channels with at least one subscriber, for diagnostics. */
    public Map<String, Integer> activeChannels() {
        Map<String, Integer> active = new LinkedHashMap<>();
        channels.forEach((channel, subscriptions) -> active.put(channel, subscriptions.size()));
        return active;
    }
}