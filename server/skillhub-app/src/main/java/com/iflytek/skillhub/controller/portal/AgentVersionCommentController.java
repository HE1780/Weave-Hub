package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.agent.social.AgentVersionCommentService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.AgentVersionCommentPageResponse;
import com.iflytek.skillhub.dto.AgentVersionCommentRequest;
import com.iflytek.skillhub.dto.AgentVersionCommentResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent-version comment list + post endpoints. Mirrors
 * {@code SkillVersionCommentController}.
 */
@RestController
@RequestMapping({"/api/v1/agent-versions", "/api/web/agent-versions"})
public class AgentVersionCommentController extends BaseApiController {

    private static final int MAX_PAGE_SIZE = 50;

    private final AgentVersionCommentService service;
    private final UserAccountRepository userAccountRepository;

    public AgentVersionCommentController(ApiResponseFactory responseFactory,
                                         AgentVersionCommentService service,
                                         UserAccountRepository userAccountRepository) {
        super(responseFactory);
        this.service = service;
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping("/{versionId}/comments")
    public ApiResponse<AgentVersionCommentPageResponse> list(
            @PathVariable Long versionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<AgentVersionCommentService.CommentWithPerms> result = service.listForVersion(
                versionId,
                principal == null ? null : principal.userId(),
                principal == null ? Set.of() : principal.platformRoles(),
                PageRequest.of(Math.max(page, 0), safeSize)
        );

        Map<String, UserAccount> authors = lookupAuthors(result);
        List<AgentVersionCommentResponse> content = result.getContent().stream()
                .map(row -> AgentVersionCommentResponse.from(
                        row.comment(),
                        authorRefFor(row.comment().getAuthorId(), authors),
                        row.permissions()))
                .toList();

        return ok("response.success.read", new AgentVersionCommentPageResponse(
                result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext(), content));
    }

    @PostMapping("/{versionId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AgentVersionCommentResponse> create(
            @PathVariable Long versionId,
            @Valid @RequestBody AgentVersionCommentRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        AgentVersionCommentService.CommentWithPerms row =
                service.post(versionId, principal.userId(), request.body());
        Map<String, UserAccount> authors = lookupAuthors(List.of(row.comment().getAuthorId()));
        return ok("response.success.created", AgentVersionCommentResponse.from(
                row.comment(),
                authorRefFor(row.comment().getAuthorId(), authors),
                row.permissions()));
    }

    private Map<String, UserAccount> lookupAuthors(Page<AgentVersionCommentService.CommentWithPerms> page) {
        List<String> ids = page.getContent().stream()
                .map(r -> r.comment().getAuthorId())
                .distinct()
                .toList();
        return lookupAuthors(ids);
    }

    private Map<String, UserAccount> lookupAuthors(List<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, UserAccount> result = new HashMap<>();
        for (UserAccount u : userAccountRepository.findByIdIn(ids)) {
            result.put(u.getId(), u);
        }
        return result;
    }

    private AgentVersionCommentResponse.AuthorRef authorRefFor(String authorId, Map<String, UserAccount> authors) {
        UserAccount u = authors.get(authorId);
        if (u == null) {
            return new AgentVersionCommentResponse.AuthorRef(authorId, authorId, null);
        }
        return new AgentVersionCommentResponse.AuthorRef(u.getId(), u.getDisplayName(), u.getAvatarUrl());
    }
}
