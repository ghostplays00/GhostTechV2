package org.ghostplays.ghosttech.internal.module;

/**
 * Thrown when a module cannot be loaded, enabled or disabled.
 *
 * <p>Always carries the module ID so the console message identifies which module failed, which is the
 * single most useful piece of information when several modules are installed.
 */
public class ModuleLoadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message   what failed
     * @param throwable the underlying cause, may be {@code null}
     */
    public ModuleLoadException(String message, Throwable throwable) {
        super(message, throwable);
    }

    /**
     * @param message what failed
     */
    public ModuleLoadException(String message) {
        super(message);
    }
}