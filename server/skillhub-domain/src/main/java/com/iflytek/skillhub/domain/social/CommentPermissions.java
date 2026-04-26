package com.iflytek.skillhub.domain.social;

public record CommentPermissions(boolean canEdit, boolean canDelete, boolean canPin) {
    public static final CommentPermissions NONE = new CommentPermissions(false, false, false);
}
