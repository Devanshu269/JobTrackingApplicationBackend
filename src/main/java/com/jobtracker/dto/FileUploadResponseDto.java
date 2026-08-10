package com.jobtracker.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FileUploadResponseDto {

    /**
     * For avatars: a direct, public Cloudinary URL usable in {@code <img src>}.
     * For resumes/cover letters: an opaque {@code /api/files/{id}} reference — those are private,
     * so the caller must exchange it via GET for a short-lived download URL.
     */
    private String url;

    /** Null for public avatars; set for private files so the client knows it needs the exchange. */
    private Integer fileId;
}
