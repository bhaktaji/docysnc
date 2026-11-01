package com.docsync.docgen.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ContentDiffGenerator {

    public String generateUnifiedDiff(String original, String generated, String filePath) {
        List<String> oldLines = split(original);
        List<String> newLines = split(generated);
        StringBuilder builder = new StringBuilder();
        builder.append("--- a/").append(filePath).append("\n");
        builder.append("+++ b/").append(filePath).append("\n");
        int max = Math.max(oldLines.size(), newLines.size());
        for (int i = 0; i < max; i++) {
            String oldLine = i < oldLines.size() ? oldLines.get(i) : null;
            String newLine = i < newLines.size() ? newLines.get(i) : null;
            if (oldLine == null) {
                builder.append("+").append(newLine).append("\n");
            } else if (newLine == null) {
                builder.append("-").append(oldLine).append("\n");
            } else if (!oldLine.equals(newLine)) {
                builder.append("-").append(oldLine).append("\n");
                builder.append("+").append(newLine).append("\n");
            } else {
                builder.append(" ").append(oldLine).append("\n");
            }
        }
        return builder.toString();
    }

    private List<String> split(String content) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }
        return List.of(content.split("\\n", -1));
    }
}
