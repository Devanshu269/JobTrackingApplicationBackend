package com.jobtracker.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Returned by {@code GET /api/files/{id}} for a private file.
 *
 * <p>Deliberately a JSON body rather than a 302 redirect or a byte stream: the caller already
 * has an axios instance that attaches the Bearer token, whereas a browser following a redirect
 * or an {@code <a href>} would not send it. The client fetches this, then opens
 * {@code downloadUrl} directly.
 */
@Getter
@Setter
public class FileDownloadResponseDto {

    /** Short-lived signed URL. Treat as single-use; re-request rather than storing it. */
    private String downloadUrl;

    private String filename;

    private String contentType;
}
