package org.ghostplays.ghosttech.api.module;

/**
 * Channel-based message bus for module-to-module communication.
 *
 * <p>Modules must not hold direct references to each other. Instead they publish messages on a
 * channel and any module that subscribed receives them. This keeps modules independently loadable:
 * a module can be removed from the server without breaking the ones that referenced it.
 *
 * <p>Two delivery styles are available:
 * <ul>
 *   <li>{@link #publish} — fire and forget, delivered to every subscriber.</li>
 *   <li>{@link #request} — send to the first subscriber and take its reply. Use this for lookups,
 *       e.g. "which module owns {@code module2:rubysword}?".</li>
 * </ul>
 *
 * <p>Delivery is synchronous on the publishing thread, which is always the main server thread.
 * Handlers must therefore be fast and must not perform database or network I/O.
 */
public interface ModuleMessaging {

    /**
     * Delivers {@code payload} to every subscriber of {@code channel}.
     *
     * @param channel  the channel name, conventionally {@code "<moduleId>:<topic>"}
     * @param payload  the message body
     * @param senderId the publishing module, may be {@code null} for plugin-originated messages
     * @return how many subscribers handled the message
     */
    int publish(String channel, Object payload, org.ghostplays.ghosttech.api.util.GhostId senderId);

    /**
     * Sends {@code payload} to the first subscriber that returns a non-{@code null} reply.
     *
     * @param channel  the channel name
     * @param payload  the message body
     * @param senderId the requesting module, may be {@code null}
     * @return the first non-{@code null} reply, or {@code null} when nobody answered
     */
    Object request(String channel, Object payload, org.ghostplays.ghosttech.api.util.GhostId senderId);

    /**
     * Subscribes {@code handler} to {@code channel}.
     *
     * <p>Handlers are invoked in subscription order. Use {@link #unsubscribe} or let the plugin
     * drop them automatically when the owning module is disabled.
     *
     * @param channel the channel name
     * @param owner   the owning module, used for automatic cleanup on unload
     * @param handler receives each published message, must not return {@code null} to keep the
     *                subscription alive for {@link #request}
     */
    void subscribe(String channel, org.ghostplays.ghosttech.api.util.GhostId owner,
            java.util.function.Function<Object, Object> handler);

    /**
     * Removes all subscriptions belonging to {@code owner}.
     *
     * <p>Called by the plugin when a module is disabled, so modules do not have to track their own
     * subscriptions.
     */
    void unsubscribeAll(org.ghostplays.ghosttech.api.util.GhostId owner);

    /** {@code true} if at least one other module is subscribed to {@code channel}. */
    boolean hasSubscribers(String channel);
}