package com.iotiq.partnererp.modules.sequences.repository;

import com.iotiq.partnererp.modules.sequences.entity.DocumentSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, DocumentSequence.Key> {

    /** Row lock for number allocation; callers must be inside a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.id = :id")
    Optional<DocumentSequence> findForUpdate(DocumentSequence.Key id);
}
