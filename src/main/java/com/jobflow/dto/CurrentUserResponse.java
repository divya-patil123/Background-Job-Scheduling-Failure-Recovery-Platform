package com.jobflow.dto;

import java.util.Set;

public record CurrentUserResponse(String subject, String name, String email, Set<String> roles) {
}
