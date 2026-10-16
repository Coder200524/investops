package com.investops.exceptionmgmt.dto;

import com.investops.exceptionmgmt.entity.ExceptionComment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentDto {

    private UUID id;
    private UUID exceptionId;
    private UUID authorId;
    private String authorUsername;
    private String content;
    private Instant createdAt;

    public static CommentDto from(ExceptionComment comment) {
        return CommentDto.builder()
                .id(comment.getId())
                .exceptionId(comment.getException().getId())
                .authorId(comment.getAuthor() != null ? comment.getAuthor().getId() : null)
                .authorUsername(comment.getAuthor() != null ? comment.getAuthor().getUsername() : "System")
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
