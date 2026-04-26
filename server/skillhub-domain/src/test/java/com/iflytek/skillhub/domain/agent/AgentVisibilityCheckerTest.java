package com.iflytek.skillhub.domain.agent;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVisibilityCheckerTest {

    private final AgentVisibilityChecker checker = new AgentVisibilityChecker();

    private Agent agent(AgentVisibility visibility) {
        return new Agent(1L, "a", "A", "owner-1", visibility);
    }

    private Agent archived(AgentVisibility visibility) {
        Agent a = agent(visibility);
        a.archive();
        return a;
    }

    @Test
    void public_agent_visible_to_anonymous() {
        assertTrue(checker.canAccess(agent(AgentVisibility.PUBLIC), null, Map.of()));
    }

    @Test
    void namespace_only_blocks_non_member() {
        assertFalse(checker.canAccess(agent(AgentVisibility.NAMESPACE_ONLY), "stranger", Map.of()));
    }

    @Test
    void namespace_only_allows_member() {
        Map<Long, NamespaceRole> roles = Map.of(1L, NamespaceRole.MEMBER);
        assertTrue(checker.canAccess(agent(AgentVisibility.NAMESPACE_ONLY), "user-x", roles));
    }

    @Test
    void private_blocks_non_owner_member() {
        Map<Long, NamespaceRole> roles = Map.of(1L, NamespaceRole.MEMBER);
        assertFalse(checker.canAccess(agent(AgentVisibility.PRIVATE), "user-x", roles));
    }

    @Test
    void private_allows_owner() {
        assertTrue(checker.canAccess(agent(AgentVisibility.PRIVATE), "owner-1", Map.of()));
    }

    @Test
    void private_allows_namespace_admin() {
        Map<Long, NamespaceRole> roles = Map.of(1L, NamespaceRole.ADMIN);
        assertTrue(checker.canAccess(agent(AgentVisibility.PRIVATE), "user-x", roles));
    }

    @Test
    void super_admin_can_access_anything() {
        Set<String> platformRoles = Set.of("SUPER_ADMIN");
        assertTrue(checker.canAccess(agent(AgentVisibility.PRIVATE), "stranger", Map.of(), platformRoles));
    }

    @Test
    void archived_blocks_public_viewer_but_allows_owner() {
        Agent a = archived(AgentVisibility.PUBLIC);
        assertFalse(checker.canAccess(a, "stranger", Map.of()));
        assertTrue(checker.canAccess(a, "owner-1", Map.of()));
    }
}
