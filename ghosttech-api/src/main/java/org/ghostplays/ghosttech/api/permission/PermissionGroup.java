package org.ghostplays.ghosttech.api.permission;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A named bundle of permissions that players can be assigned to.
 *
 * <p>Groups may inherit from other groups, resolved transitively. This keeps definitions DRY:
 * a {@code builder} group can inherit {@code member} rather than repeating its nodes.
 *
 * @param name    the group name
 * @param nodes   nodes granted directly by this group
 * @param parents groups this group inherits from
 */
public record PermissionGroup(String name, Collection<String> nodes, Collection<String> parents) {

    public PermissionGroup {
        nodes = nodes == null ? Set.of() : Set.copyOf(nodes);
        parents = parents == null ? Set.of() : Set.copyOf(parents);
    }

    /**
     * Flattens this group's inherited nodes.
     *
     * @param allGroups every defined group, used to resolve parents
     * @return all nodes reachable from this group, including its own
     */
    public Set<String> resolve(Collection<PermissionGroup> allGroups) {
        Set<String> resolved = new LinkedHashSet<>(nodes);
        for (String parent : parents) {
            for (PermissionGroup candidate : allGroups) {
                if (candidate.name().equalsIgnoreCase(parent)) {
                    resolved.addAll(candidate.resolve(allGroups));
                    break;
                }
            }
        }
        return resolved;
    }
}