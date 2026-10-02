package org.ghostplays.ghosttech.api.permission;

/** Whether a permission is granted to operators out of the box. */
public enum DefaultValue {

    /** Granted to operators, denied to everyone else. The usual choice. */
    OP,

    /** Granted to everyone. Use sparingly, for things like {@code /gt help}. */
    TRUE,

    /** Denied to everyone until granted explicitly. */
    FALSE
}