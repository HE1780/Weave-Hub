package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.social.SkillVersionComment;
import com.iflytek.skillhub.domain.social.SkillVersionCommentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SkillVersionCommentJpaRepository
        extends JpaRepository<SkillVersionComment, Long>, SkillVersionCommentRepository {

    @Override
    @Query("""
        SELECT c FROM SkillVersionComment c
        WHERE c.skillVersionId = :versionId
          AND c.deletedAt IS NULL
        ORDER BY c.pinned DESC, c.createdAt DESC
        """)
    Page<SkillVersionComment> findActiveByVersionId(
        @Param("versionId") Long skillVersionId,
        Pageable pageable
    );

    @Override
    Optional<SkillVersionComment> findById(Long id);
}
