package io.livelattice.core.model.dto;

import java.time.Instant;
import java.util.Map;

public record TemplateResponse(
    String id,
    String workspaceId,
    String name,
    String category,
    String thumbnail,
    Map<String, Object> content,
    String createdBy,
    Instant createdAt
) {
    public static TemplateResponse from(io.livelattice.core.model.entity.CanvasTemplate template) {
        return new TemplateResponse(
            template.getId().toString(),
            template.getWorkspaceId() != null ? template.getWorkspaceId().toString() : null,
            template.getName(),
            template.getCategory(),
            template.getThumbnail(),
            template.getContent(),
            template.getCreatedBy().toString(),
            template.getCreatedAt()
        );
    }
}
