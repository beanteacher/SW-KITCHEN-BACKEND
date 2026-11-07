package com.swkitchen.common.audit;

public enum AuditAction {
    READ, CREATE, UPDATE, DELETE, DOWNLOAD;

    // 내려받기 API 가 생기면 그 경로를 DOWNLOAD 로 정한다
    static AuditAction of(String method) {
        return switch (method) {
            case "GET", "HEAD" -> READ;
            case "POST" -> CREATE;
            case "PUT", "PATCH" -> UPDATE;
            case "DELETE" -> DELETE;
            default -> null;
        };
    }
}
