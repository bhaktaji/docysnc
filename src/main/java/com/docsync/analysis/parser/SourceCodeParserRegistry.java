package com.docsync.analysis.parser;

import com.docsync.analysis.entity.CodeEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class SourceCodeParserRegistry {

    private final List<SourceCodeParser> parsers;

    public SourceCodeParserRegistry(List<SourceCodeParser> parsers) {
        this.parsers = parsers;
    }

    public SourceCodeParser getParser(String filePath) {
        return parsers.stream().filter(parser -> parser.supports(filePath)).findFirst().orElse(null);
    }

    public List<CodeEntity> parse(String filePath, String content, UUID repositoryId, UUID analysisId, String commitSha) {
        SourceCodeParser parser = getParser(filePath);
        if (parser == null) {
            return List.of();
        }
        return parser.parse(filePath, content, repositoryId, analysisId, commitSha);
    }
}
