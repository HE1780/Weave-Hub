package com.iflytek.skillhub.domain.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface SkillVersionCommentRepository {
    Page<SkillVersionComment> findActiveByVersionId(Long skillVersionId, Pageable pageable);
    Optional<SkillVersionComment> findById(Long id);
    SkillVersionComment save(SkillVersionComment comment);
}
