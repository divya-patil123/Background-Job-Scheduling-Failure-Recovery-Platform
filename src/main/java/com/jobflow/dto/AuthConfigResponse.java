package com.jobflow.dto;

public record AuthConfigResponse(
        boolean enabled,
        boolean developmentMode,
        String domain,
        String clientId,
        String audience) {
}
