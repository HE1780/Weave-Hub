package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.social.SkillVersionCommentService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping({"/api/v1/skill-versions", "/api/web/skill-versions"})
public class SkillVersionCommentController extends BaseApiController {

    private static final int MAX_PAGE_SIZE = 50;

    private final SkillVersionCommentService service;
    private final UserAccountRepository userAccountRepository;

    public SkillVersionCommentController(ApiResponseFactory responseFactory,
                                         SkillVersionCommentService service,
                                         UserAccountRepository userAccountRepository) {
        super(responseFactory);
        this.service = service;
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping("/{versionId}/comments")
    public ApiResponse<SkillVersionCommentPageResponse> list(
            @PathVariable Long versionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<SkillVersionCommentService.CommentWithPerms> result = service.listForVersion(
                versionId,
                principal == null ? null : principal.userId(),
                principal == null ? Set.of() : principal.platformRoles(),
                PageRequest.of(Math.max(page, 0), safeSize)
        );

        Map<String, UserAccount> authors = lookupAuthors(result);
        List<SkillVersionCommentResponse> content = result.getContent().stream()
                .map(row -> SkillVersionCommentResponse.from(
                        row.comment(),
                        authorRefFor(row.comment().getAuthorId(), authors),
                        row.permissions()))
                .toList();

        return ok("response.success.read", new SkillVersionCommentPageResponse(
                result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext(), content));
    }

    @PostMapping("/{versionId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SkillVersionCommentResponse> create(
            @PathVariable Long versionId,
            @Valid @RequestBody SkillVersionCommentRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        SkillVersionCommentService.CommentWithPerms row =
                service.post(versionId, principal.userId(), request.body());
        Map<String, UserAccount> authors = lookupAuthors(List.of(row.comment().getAuthorId()));
        return ok("response.success.created", SkillVersionCommentResponse.from(
                row.comment(),
                authorRefFor(row.comment().getAuthorId(), authors),
                row.permissions()));
    }

    private Map<String, UserAccount> lookupAuthors(Page<SkillVersionCommentService.CommentWithPerms> page) {
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

    private SkillVersionCommentResponse.AuthorRef authorRefFor(String authorId, Map<String, UserAccount> authors) {
        UserAccount u = authors.get(authorId);
        if (u == null) {
            return new SkillVersionCommentResponse.AuthorRef(authorId, authorId, null);
        }
        return new SkillVersionCommentResponse.AuthorRef(u.getId(), u.getDisplayName(), u.getAvatarUrl());
    }
}
