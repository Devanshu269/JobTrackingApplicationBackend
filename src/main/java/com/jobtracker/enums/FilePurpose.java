package com.jobtracker.enums;

/**
 * What an uploaded file is for. Drives the storage folder, the size cap, the accepted types,
 * and — most importantly — whether the stored object is publicly readable.
 *
 * <p>Only {@link #AVATAR} is public. Avatars have to work as a bare {@code <img src>}, which
 * cannot send an Authorization header. Resumes and cover letters are PII documents that are
 * always fetched behind a click, so they stay private and go through the auth-gated download
 * route instead.
 */
public enum FilePurpose {

    RESUME("resumes", 5 * 1024 * 1024, false),
    COVER_LETTER("cover-letters", 5 * 1024 * 1024, false),
    AVATAR("avatars", 2 * 1024 * 1024, true);

    private final String folder;
    private final long maxBytes;
    private final boolean publiclyReadable;

    FilePurpose(String folder, long maxBytes, boolean publiclyReadable) {
        this.folder = folder;
        this.maxBytes = maxBytes;
        this.publiclyReadable = publiclyReadable;
    }

    public String getFolder() {
        return folder;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public boolean isPubliclyReadable() {
        return publiclyReadable;
    }
}
