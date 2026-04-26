package com.iflytek.skillhub.domain.agent;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;

import java.util.Map;
import java.util.Set;

/**
 * Read-permission predicate for agents. Mirrors {@code VisibilityChecker} for skills,
 * trimmed: agents have no HIDDEN moderation state and no concept of an unpublished
 * latest-version pointer (the AgentVersion status enum carries that information).
 */
public class AgentVisibilityChecker {

    public boolean canAccess(Agent agent,
                             String currentUserId,
                             Map<Long, NamespaceRole> userNamespaceRoles) {
        return canAccess(agent, currentUserId, userNamespaceRoles, Set.of());
    }

    public boolean canAccess(Agent agent,
                             String currentUserId,
                             Map<Long, NamespaceRole> userNamespaceRoles,
                             Set<String> platformRoles) {
        if (isSuperAdmin(platformRoles)) {
            return true;
        }
        if (agent.getStatus() == AgentStatus.ARCHIVED) {
            return isOwner(agent, currentUserId)
                    || isAdminOrAbove(userNamespaceRoles.get(agent.getNamespaceId()));
        }
        return switch (agent.getVisibility()) {
            case PUBLIC -> true;
            case NAMESPACE_ONLY -> userNamespaceRoles.containsKey(agent.getNamespaceId());
            case PRIVATE -> isOwner(agent, currentUserId)
                    || isAdminOrAbove(userNamespaceRoles.get(agent.getNamespaceId()));
        };
    }

    private boolean isOwner(Agent agent, String currentUserId) {
        return currentUserId != null && agent.getOwnerId().equals(currentUserId);
    }

    private boolean isAdminOrAbove(NamespaceRole role) {
        return role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
    }

    private boolean isSuperAdmin(Set<String> platformRoles) {
        return platformRoles != null && platformRoles.contains("SUPER_ADMIN");
    }
}
