package io.livelattice.core.service.snapshot;

import java.util.Map;
import java.util.UUID;

public interface SnapshotContentStore {
    String put(UUID workspaceId, UUID canvasId, long version, Map<String, Object> content);

    Map<String, Object> get(String path);
}
