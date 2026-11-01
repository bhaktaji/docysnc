package com.docsync.docgen.service;

import com.docsync.common.exception.ValidationException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DocumentationPromptBuilder {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*\\}\\}");

    public com.docsync.common.adapter.llm.LlmClient.LlmRequest buildUpdatePrompt(
            String filePath, String existingContent, String codeChanges, String codeEntities,
            String model, int maxTokens) {
        String template = loadTemplate("prompts/doc-update.md");
        Map<String, String> variables = Map.of(
                "filePath", filePath == null ? "" : filePath,
                "existingContent", existingContent == null ? "" : existingContent,
                "codeChanges", codeChanges == null ? "" : codeChanges,
                "codeEntities", codeEntities == null ? "" : codeEntities
        );

        String prompt = substitute(template, variables);
        String systemPrompt = "You are a technical documentation expert. Follow the template instructions.";
        int estimatedTokens = (systemPrompt.length() + prompt.length()) / 4;
        if (estimatedTokens > maxTokens) {
            // Truncate the lowest-priority context first.
            String truncatedChanges = truncate(codeChanges, Math.max(200, maxTokens * 2));
            prompt = substitute(template, Map.of(
                    "filePath", filePath == null ? "" : filePath,
                    "existingContent", existingContent == null ? "" : existingContent,
                    "codeChanges", truncatedChanges,
                    "codeEntities", codeEntities == null ? "" : codeEntities
            ));
        }

        return new com.docsync.common.adapter.llm.LlmClient.LlmRequest(
                systemPrompt, prompt, model, 0.2, maxTokens, Map.of("filePath", filePath == null ? "" : filePath));
    }

    private String loadTemplate(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Prompt template not found: " + path, ex);
        }
    }

    private String substitute(String template, Map<String, String> variables) {
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!variables.containsKey(name)) {
                throw new ValidationException("prompt", "Missing template variable: " + name);
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(variables.get(name)));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxChars) + "\n...[truncated]";
    }
}
