package com.jobtracker.model;

import com.jobtracker.enums.FilePurpose;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Record of a file uploaded to Cloudinary.
 *
 * <p>Like {@link ActivityLog}, {@code userId} is a plain column rather than a {@code @ManyToOne}:
 * the row is what proves ownership at download time, and it should not be cascade-deleted out
 * from under a job that still references the file. Files are immutable and never overwritten —
 * every upload gets a fresh UUID key, so a resume attached to an old application keeps working
 * after the user uploads a newer one.
 */
@EntityListeners(AuditingEntityListener.class)
@Table(name = "stored_files", indexes = {
        @Index(name = "idx_stored_files_user", columnList = "user_id")
})
@Entity
@Getter
@Setter
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id", nullable = false)
    private Integer fileId;

    /** Plain column, not an FK — this is the ownership check for every download. */
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private FilePurpose purpose;

    /** Cloudinary's identifier, used to build a delivery or signed URL later. */
    @Column(name = "public_id", nullable = false, length = 512)
    private String publicId;

    /** "image" or "raw" — Cloudinary needs this to address the asset again. */
    @Column(name = "resource_type", nullable = false, length = 32)
    private String resourceType;

    /** Only populated for publicly-readable assets (avatars); null for private ones. */
    @Column(name = "public_url", length = 1024)
    private String publicUrl;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
