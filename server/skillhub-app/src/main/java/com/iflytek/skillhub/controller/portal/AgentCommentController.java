package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.agent.social.AgentVersionCommentService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.AgentVersionCommentRequest;
import com.iflytek.skillhub.dto.AgentVersionCommentResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.CommentPinRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Edit / delete / pin endpoints for agent-version comments. Mirrors the Skill
 * side {@code CommentController}; we route under {@code /api/.../agent-comments}
 * so the resource id namespace stays separate from skill comments.
 */
@RestController
@RequestMapping({"/api/v1/agent-comments", "/api/web/agent-comments"})
public class AgentCommentController extends BaseApiController {

    private final AgentVersionCommentService service;
    private final UserAccountRepository userAccountRepository;

    public AgentCommentController(ApiResponseFactory responseFactory,
                                  AgentVersionCommentService service,
                                  UserAccountRepository userAccountRepository) {
        super(responseFactory);
        this.service = service;
        this.userAccountRepository = userAccountRepository;
    }

    @PatchMapping("/{commentId}")
    public ApiResponse<AgentVersionCommentResponse> edit(
            @PathVariable Long commentId,
            @Valid @RequestBody AgentVersionCommentRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        AgentVersionCommentService.CommentWithPerms row =
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
    public ApiResponse<AgentVersionCommentResponse> pin(
            @PathVariable Long commentId,
            @RequestBody CommentPinRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        AgentVersionCommentService.CommentWithPerms row =
                service.setPinned(commentId, principal.userId(), request.pinned());
        return ok("response.success.updated", responseFor(row));
    }

    private AgentVersionCommentResponse responseFor(AgentVersionCommentService.CommentWithPerms row) {
        String authorId = row.comment().getAuthorId();
        Optional<UserAccount> u = userAccountRepository.findById(authorId);
        AgentVersionCommentResponse.AuthorRef author = u
                .map(a -> new AgentVersionCommentResponse.AuthorRef(a.getId(), a.getDisplayName(), a.getAvatarUrl()))
                .orElseGet(() -> new AgentVersionCommentResponse.AuthorRef(authorId, authorId, null));
        return AgentVersionCommentResponse.from(row.comment(), author, row.permissions());
    }
}
