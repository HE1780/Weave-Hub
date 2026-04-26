package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.social.SkillVersionCommentService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping({"/api/v1/comments", "/api/web/comments"})
public class CommentController extends BaseApiController {

    private final SkillVersionCommentService service;
    private final UserAccountRepository userAccountRepository;

    public CommentController(ApiResponseFactory responseFactory,
                             SkillVersionCommentService service,
                             UserAccountRepository userAccountRepository) {
        super(responseFactory);
        this.service = service;
        this.userAccountRepository = userAccountRepository;
    }

    @PatchMapping("/{commentId}")
    public ApiResponse<SkillVersionCommentResponse> edit(
            @PathVariable Long commentId,
            @Valid @RequestBody SkillVersionCommentRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        SkillVersionCommentService.CommentWithPerms row =
                service.edit(commentId, principal.userId(), request.body());
        return ok("response.success.updated", responseFor(row));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long commentId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        service.delete(commentId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{commentId}/pin")
    public ApiResponse<SkillVersionCommentResponse> pin(
            @PathVariable Long commentId,
            @RequestBody CommentPinRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        SkillVersionCommentService.CommentWithPerms row =
                service.setPinned(commentId, principal.userId(), request.pinned());
        return ok("response.success.updated", responseFor(row));
    }

    private SkillVersionCommentResponse responseFor(SkillVersionCommentService.CommentWithPerms row) {
        String authorId = row.comment().getAuthorId();
        Optional<UserAccount> u = userAccountRepository.findById(authorId);
        SkillVersionCommentResponse.AuthorRef author = u
                .map(a -> new SkillVersionCommentResponse.AuthorRef(a.getId(), a.getDisplayName(), a.getAvatarUrl()))
                .orElseGet(() -> new SkillVersionCommentResponse.AuthorRef(authorId, authorId, null));
        return SkillVersionCommentResponse.from(row.comment(), author, row.permissions());
    }
}
