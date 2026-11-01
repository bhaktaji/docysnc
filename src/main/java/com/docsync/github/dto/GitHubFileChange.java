package com.docsync.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubFileChange(
        String status,
        String filename,
        @JsonProperty("previous_filename") String previousFilename,
        String patch
) {
}
