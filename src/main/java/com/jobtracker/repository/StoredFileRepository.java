package com.jobtracker.repository;

import com.jobtracker.model.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, Integer> {

    /**
     * Ownership-scoped, same pattern as findByJobIdAndUser_UserId: both conditions in one query
     * so "not yours" and "doesn't exist" are indistinguishable and both 404.
     */
    Optional<StoredFile> findByFileIdAndUserId(Integer fileId, Integer userId);
}
