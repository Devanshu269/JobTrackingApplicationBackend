package com.jobtracker.controller;

import com.jobtracker.dto.FileDownloadResponseDto;
import com.jobtracker.dto.FileUploadResponseDto;
import com.jobtracker.enums.FilePurpose;
import com.jobtracker.exception.InvalidFileException;
import com.jobtracker.model.User;
import com.jobtracker.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    /**
     * @param purpose {@code resume} | {@code cover-letter} | {@code avatar}. Hyphens are accepted
     *                and mapped to the enum, so the frontend's existing values work unchanged.
     */
    @PostMapping
    public ResponseEntity<FileUploadResponseDto> upload(Authentication authentication,
                                                        @RequestParam("file") MultipartFile file,
                                                        @RequestParam("purpose") String purpose) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fileStorageService.upload(user, file, parsePurpose(purpose)));
    }

    /**
     * Exchanges an opaque file id for a short-lived download URL. Returns JSON rather than
     * redirecting or streaming bytes: the client has a Bearer token on its axios instance, but a
     * browser following a redirect (or an {@code <a href>}) would not send it.
     */
    @GetMapping("/{fileId}")
    public ResponseEntity<FileDownloadResponseDto> download(Authentication authentication,
                                                            @PathVariable Integer fileId) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(fileStorageService.getDownload(user, fileId));
    }

    /**
     * Hand-parsed rather than letting Spring bind straight to the enum, because the frontend
     * sends {@code cover-letter} and the default converter is exact-match only.
     *
     * <p>A raw {@code valueOf} would throw IllegalArgumentException, which has no handler and so
     * escapes to /error — behind the security filter chain — and comes back as a bodyless 401.
     * Converting it to InvalidFileException keeps it a 400 with a readable message.
     */
    private FilePurpose parsePurpose(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidFileException("purpose is required — one of: resume, cover-letter, avatar");
        }
        try {
            return FilePurpose.valueOf(raw.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new InvalidFileException(
                    "Invalid purpose '" + raw + "' — expected one of: resume, cover-letter, avatar");
        }
    }
}
