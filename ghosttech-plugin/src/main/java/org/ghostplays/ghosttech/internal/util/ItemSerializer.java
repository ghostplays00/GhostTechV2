package org.ghostplays.ghosttech.internal.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * Serialises item stacks for the database.
 *
 * <p>Uses Bukkit's own object streams, so any item survives a round trip exactly, including items
 * with custom data components from other plugins. Stored as Base64 in a single BLOB column, which
 * keeps the schema stable no matter what items exist.
 */
public final class ItemSerializer {

    /**
     * Serialises a list of slots.
     *
     * @param contents the slots, {@code null} entries are written as air
     * @return Base64 text, or {@code null} when every slot was empty
     */
    public static String serialize(List<ItemStack> contents) {
        if (contents == null || contents.stream().allMatch(ItemSerializer::isEmpty)) {
            return null;
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeInt(contents.size());
            for (ItemStack stack : contents) {
                out.writeObject(isEmpty(stack) ? null : stack);
            }
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize item contents", exception);
        }
    }

    /**
     * Deserialises a list of slots previously written by {@link #serialize(List)}.
     *
     * @param data   Base64 text, may be {@code null}
     * @param length the slot count to return, padding with air
     * @return the slots, or an empty list when {@code data} is {@code null}
     */
    public static List<ItemStack> deserialize(String data, int length) {
        if (data == null || data.isBlank()) {
            return List.of();
        }
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bytes)) {
            int stored = in.readInt();
            java.util.List<ItemStack> contents = new java.util.ArrayList<>(Math.max(stored, length));
            for (int slot = 0; slot < stored; slot++) {
                Object value = in.readObject();
                contents.add(value instanceof ItemStack stack ? stack : null);
            }
            while (contents.size() < length) {
                contents.add(null);
            }
            return contents;
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to deserialize item contents", exception);
        }
    }

    /** {@code true} for {@code null}, air, or a zero-amount stack. */
    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    /** A defensive copy, so stored stacks are never mutated by reference. */
    public static ItemStack copy(ItemStack stack) {
        return isEmpty(stack) ? null : stack.clone();
    }

    private ItemSerializer() {
    }
}