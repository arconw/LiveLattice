package io.livelattice.importexport.dto;

import java.util.Map;

public record DashboardExportData(
    String dashboardId,
    String title,
    java.util.List<Map<String, Object>> rows
) {
}
