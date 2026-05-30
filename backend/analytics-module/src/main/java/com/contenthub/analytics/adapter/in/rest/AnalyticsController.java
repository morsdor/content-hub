package com.contenthub.analytics.adapter.in.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@Slf4j
public class AnalyticsController {

	@GetMapping("/workspaces/{workspaceId}/metrics")
	public Map<String, Object> getMetrics(@PathVariable UUID workspaceId) {
		return Map.of("workspaceId", workspaceId, "metrics", Map.of());
	}
}
