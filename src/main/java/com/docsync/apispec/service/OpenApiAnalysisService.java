package com.docsync.apispec.service;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OpenApiAnalysisService {

    public OpenApiSummary analyze(String content) {
        if (content == null || content.isBlank()) {
            return new OpenApiSummary(0, List.of());
        }
        Object loaded = new Yaml().load(content);
        if (!(loaded instanceof Map<?, ?> root)) {
            return new OpenApiSummary(0, List.of());
        }
        Object pathsObject = root.get("paths");
        if (!(pathsObject instanceof Map<?, ?> paths)) {
            return new OpenApiSummary(0, List.of());
        }

        List<String> endpoints = new ArrayList<>();
        for (Object path : paths.keySet()) {
            Object operations = paths.get(path);
            if (operations instanceof Map<?, ?> operationMap) {
                for (Object method : operationMap.keySet()) {
                    endpoints.add(String.valueOf(method).toUpperCase() + " " + path);
                }
            }
        }
        return new OpenApiSummary(endpoints.size(), endpoints);
    }

    public List<String> diff(String baseContent, String headContent) {
        List<String> changes = new ArrayList<>();
        OpenApiSummary base = analyze(baseContent);
        OpenApiSummary head = analyze(headContent);
        for (String endpoint : head.endpoints()) {
            if (!base.endpoints().contains(endpoint)) {
                changes.add("ADDED " + endpoint);
            }
        }
        for (String endpoint : base.endpoints()) {
            if (!head.endpoints().contains(endpoint)) {
                changes.add("REMOVED " + endpoint);
            }
        }
        return changes;
    }

    public record OpenApiSummary(int endpointCount, List<String> endpoints) {
    }
}
