package org.ghostplays.ghosttech.api.content;

/**
 * The kinds of content the plugin can register.
 *
 * <p>Content is keyed by {@link org.ghostplays.ghosttech.api.util.GhostId} within a type, so
 * {@code example:legendary_forge} may exist simultaneously as a {@link #BLOCK} and as a
 * {@link #RECIPE}.
 */
public enum ContentType {

    /** A single custom block placed in the world. See the {@code block} package. */
    BLOCK,

    /** A multi-block structure built from several ordinary blocks. See the {@code multiblock} package. */
    MULTIBLOCK,

    /** A custom item, obtainable through the GUI or commands. */
    ITEM,

    /** A stored GUI layout, e.g. a menu or a machine interface. */
    GUI,

    /** A reusable dialog definition. */
    DIALOG
}