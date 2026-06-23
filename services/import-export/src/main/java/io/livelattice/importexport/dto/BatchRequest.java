package io.livelattice.importexport.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record BatchRequest(
    @NotBlank String workspaceId,
    List<String> canvasIds,
    List<String> dashboardIds,
    String format
) {
}
