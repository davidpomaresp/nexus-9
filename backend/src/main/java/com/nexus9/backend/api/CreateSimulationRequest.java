package com.nexus9.backend.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSimulationRequest(@NotNull Long seed, @NotBlank String scenario) {
}
