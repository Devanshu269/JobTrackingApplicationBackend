package com.jobtracker.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.jobtracker.dto.FileDownloadResponseDto;
import com.jobtracker.dto.FileUploadResponseDto;
import com.jobtracker.enums.FilePurpose;
import com.jobtracker.exception.InvalidFileException;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.model.StoredFile;
import com.jobtracker.model.User;
import com.jobtracker.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /** How long a signed download URL stays valid. Long enough to click, short enough not to leak. */
    private static final long SIGNED_URL_TTL_SECONDS = 300;

    private final Cloudinary cloudinary;
    private final StoredFileRepository storedFileRepository;
    private final FileTypeDetector fileTypeDetector;

    public FileUploadResponseDto upload(User user, MultipartFile file, FilePurpose purpose) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("No file was uploaded");
        }
        if (file.getSize() > purpose.getMaxBytes()) {
            throw new InvalidFileException("File is too large — max %d MB for %s"
                    .formatted(purpose.getMaxBytes() / (1024 * 1024), purpose.name().toLowerCase()));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidFileException("Could not read the uploaded file");
        }
        // Validated on actual content, never on the client-declared Content-Type.
        String detected = fileTypeDetector.detectAndValidate(bytes, purpose);

        // Fresh UUID every time: files are immutable, so an older resume attached to a past
        // application keeps resolving after a newer one is uploaded.
        String publicId = "users/%d/%s/%s".formatted(user.getUserId(), purpose.getFolder(), UUID.randomUUID());
        String resourceType = purpose == FilePurpose.AVATAR ? "image" : "raw";

        Map<String, Object> options = ObjectUtils.asMap(
                "public_id", publicId,
                "resource_type", resourceType,
                // "authenticated" keeps the asset out of public delivery entirely; only a signed
                // URL resolves it. Avatars stay "upload" so they work in a bare <img src>.
                "type", purpose.isPubliclyReadable() ? "upload" : "authenticated",
                "overwrite", false);

        Map<?, ?> result;
        try {
            result = cloudinary.uploader().upload(bytes, options);
        } catch (Exception e) {
            log.error("Cloudinary upload failed for user {} purpose {}", user.getUserId(), purpose, e);
            throw new InvalidFileException("Upload failed — please try again");
        }

        StoredFile stored = new StoredFile();
        stored.setUserId(user.getUserId());
        stored.setPurpose(purpose);
        stored.setPublicId(String.valueOf(result.get("public_id")));
        stored.setResourceType(resourceType);
        stored.setOriginalFilename(sanitiseFilename(file.getOriginalFilename(), detected));
        stored.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        stored.setSizeBytes(file.getSize());
        if (purpose.isPubliclyReadable()) {
            stored.setPublicUrl(String.valueOf(result.get("secure_url")));
        }
        StoredFile saved = storedFileRepository.save(stored);

        FileUploadResponseDto dto = new FileUploadResponseDto();
        if (purpose.isPubliclyReadable()) {
            // Directly usable — no exchange step needed.
            dto.setUrl(saved.getPublicUrl());
        } else {
            // Opaque reference; the caller exchanges it via GET for a short-lived signed URL.
            dto.setUrl("/api/files/" + saved.getFileId());
            dto.setFileId(saved.getFileId());
        }
        return dto;
    }

    public FileDownloadResponseDto getDownload(User user, Integer fileId) {
        StoredFile stored = storedFileRepository.findByFileIdAndUserId(fileId, user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        String url = stored.getPurpose().isPubliclyReadable()
                ? stored.getPublicUrl()
                : signedUrlFor(stored);

        FileDownloadResponseDto dto = new FileDownloadResponseDto();
        dto.setDownloadUrl(url);
        dto.setFilename(stored.getOriginalFilename());
        dto.setContentType(stored.getContentType());
        return dto;
    }

    private String signedUrlFor(StoredFile stored) {
        long expiresAt = (System.currentTimeMillis() / 1000L) + SIGNED_URL_TTL_SECONDS;
        try {
            return cloudinary.privateDownload(stored.getPublicId(), null, ObjectUtils.asMap(
                    "resource_type", stored.getResourceType(),
                    "type", "authenticated",
                    "attachment", true,
                    "expires_at", expiresAt));
        } catch (Exception e) {
            log.error("Could not sign download URL for file {}", stored.getFileId(), e);
            throw new InvalidFileException("Could not generate a download link — please try again");
        }
    }

    private String sanitiseFilename(String original, String detectedType) {
        if (original == null || original.isBlank()) {
            return "upload." + detectedType;
        }
        String base = original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }
}
