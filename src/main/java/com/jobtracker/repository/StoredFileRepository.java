package com.jobtracker.repository;

import com.jobtracker.model.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, Integer> {

    Optional<StoredFile> findByFileIdAndUserId(Integer fileId, Integer userId);
}
