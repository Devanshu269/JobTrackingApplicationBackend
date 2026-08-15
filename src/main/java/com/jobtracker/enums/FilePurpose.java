package com.jobtracker.enums;

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
