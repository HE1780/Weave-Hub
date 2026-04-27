package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.agent.social.AgentVersionComment;
import com.iflytek.skillhub.domain.agent.social.AgentVersionCommentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentVersionCommentJpaRepository
        extends JpaRepository<AgentVersionComment, Long>, AgentVersionCommentRepository {

    @Override
    @Query("""
        SELECT c FROM AgentVersionComment c
        WHERE c.agentVersionId = :versionId
          AND c.deletedAt IS NULL
        ORDER BY c.pinned DESC, c.createdAt DESC
        """)
    Page<AgentVersionComment> findActiveByVersionId(
        @Param("versionId") Long agentVersionId,
        Pageable pageable
    );

    @Override
    Optional<AgentVersionComment> findById(Long id);
}
