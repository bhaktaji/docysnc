package com.docsync.analysis.parser;

import com.docsync.analysis.entity.CodeEntity;
import com.docsync.analysis.entity.CodeEntityType;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.javadoc.Javadoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class JavaSourceParser implements SourceCodeParser {

    private static final Logger log = LoggerFactory.getLogger(JavaSourceParser.class);

    private final JavaParser javaParser;

    public JavaSourceParser() {
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        this.javaParser = new JavaParser(configuration);
    }

    @Override
    public boolean supports(String filePath) {
        return filePath != null && filePath.endsWith(".java");
    }

    @Override
    public List<CodeEntity> parse(String filePath, String content, UUID repositoryId, UUID analysisId, String commitSha) {
        List<CodeEntity> entities = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return entities;
        }

        try {
            var result = javaParser.parse(content);
            if (result.getResult().isEmpty()) {
                log.debug("JavaParser produced no result for {}", filePath);
                return entities;
            }
            CompilationUnit unit = result.getResult().get();
            String packageName = unit.getPackageDeclaration()
                    .map(decl -> decl.getNameAsString())
                    .orElse("");

            for (TypeDeclaration<?> type : unit.getTypes()) {
                addType(entities, repositoryId, analysisId, commitSha, filePath, packageName, type, null);
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to parse Java file {}: {}", filePath, ex.getMessage());
        }
        return entities;
    }

    private void addType(List<CodeEntity> entities, UUID repositoryId, UUID analysisId, String commitSha,
                         String filePath, String packageName, TypeDeclaration<?> type, UUID parentEntityId) {
        String simpleName = type.getNameAsString();
        String qualifiedName = packageName.isBlank() ? simpleName : packageName + "." + simpleName;
        boolean isInterface = type instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
                && ((com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) type).isInterface();
        CodeEntityType entityType = isInterface ? CodeEntityType.INTERFACE
                : type instanceof EnumDeclaration ? CodeEntityType.ENUM
                : type instanceof RecordDeclaration ? CodeEntityType.RECORD
                : CodeEntityType.CLASS;

        String modifiers = type.getModifiers().toString();
        String javadoc = type.getJavadoc().map(Javadoc::toText).orElse(null);
        Integer line = type.getBegin().map(pos -> pos.line).orElse(null);

        if (isPublicOrProtected(modifiers) || modifiers.contains("public") || modifiers.contains("protected")) {
            entities.add(new CodeEntity(repositoryId, analysisId, entityType, qualifiedName, simpleName,
                    filePath, line, modifiers, type.toString(), hash(type.toString()), javadoc, parentEntityId, commitSha));
        }

        type.getMethods().forEach(method -> {
            if (!isPublicOrProtected(method.getModifiers().toString())) {
                return;
            }
            String methodSignature = method.getDeclarationAsString(false, false, false);
            entities.add(new CodeEntity(repositoryId, analysisId, CodeEntityType.METHOD,
                    qualifiedName + "." + method.getNameAsString(),
                    method.getNameAsString(), filePath,
                    method.getBegin().map(pos -> pos.line).orElse(null),
                    method.getModifiers().toString(), methodSignature,
                    hash(methodSignature), method.getJavadoc().map(Javadoc::toText).orElse(null),
                    null, commitSha));
        });

        type.getFields().forEach(field -> {
            if (!isPublicOrProtected(field.getModifiers().toString())) {
                return;
            }
            for (var variable : field.getVariables()) {
                String fieldSignature = field.getElementType() + " " + variable.getNameAsString();
                entities.add(new CodeEntity(repositoryId, analysisId, CodeEntityType.FIELD,
                        qualifiedName + "." + variable.getNameAsString(),
                        variable.getNameAsString(), filePath,
                        field.getBegin().map(pos -> pos.line).orElse(null),
                        field.getModifiers().toString(), fieldSignature,
                        hash(fieldSignature), field.getJavadoc().map(Javadoc::toText).orElse(null),
                        null, commitSha));
            }
        });

        type.getMembers().stream()
                .filter(member -> member instanceof TypeDeclaration)
                .map(member -> (TypeDeclaration<?>) member)
                .forEach(nested -> addType(entities, repositoryId, analysisId, commitSha, filePath,
                        qualifiedName, nested, null));
    }

    private boolean isPublicOrProtected(String modifiers) {
        return modifiers != null && (modifiers.contains("public") || modifiers.contains("protected"));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            return null;
        }
    }
}
