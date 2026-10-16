package com.investops.exceptionmgmt.repository;

import com.investops.exceptionmgmt.entity.ExceptionComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExceptionCommentRepository extends JpaRepository<ExceptionComment, UUID> {

    @EntityGraph(attributePaths = {"author"})
    List<ExceptionComment> findByExceptionIdOrderByCreatedAtAsc(UUID exceptionId);

    @EntityGraph(attributePaths = {"author"})
    Page<ExceptionComment> findByExceptionId(UUID exceptionId, Pageable pageable);
}
