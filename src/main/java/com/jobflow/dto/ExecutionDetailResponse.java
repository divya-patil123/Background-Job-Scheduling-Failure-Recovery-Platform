package com.jobflow.dto;

import java.util.List;

public record ExecutionDetailResponse(ExecutionResponse execution, List<AttemptResponse> attempts) {
}
