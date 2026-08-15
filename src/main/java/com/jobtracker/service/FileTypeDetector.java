package com.jobtracker.service;

import com.jobtracker.enums.FilePurpose;
import com.jobtracker.exception.InvalidFileException;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class FileTypeDetector {

    private static final Set<String> DOCUMENT_TYPES = Set.of("pdf", "doc", "docx");
    private static final Set<String> IMAGE_TYPES = Set.of("png", "jpeg", "webp");

    /**
     * @return the detected format, guaranteed to be one the given purpose accepts
     * @throws InvalidFileException if the content isn't recognised, or isn't valid for the purpose
     */
    public String detectAndValidate(byte[] head, FilePurpose purpose) {
        String detected = sniff(head);
        if (detected == null) {
            throw new InvalidFileException("Unrecognised file type — the file's contents don't match any accepted format");
        }

        Set<String> allowed = purpose == FilePurpose.AVATAR ? IMAGE_TYPES : DOCUMENT_TYPES;
        if (!allowed.contains(detected)) {
            throw new InvalidFileException(
                    "A " + detected + " file isn't valid for " + purpose.name().toLowerCase()
                            + ". Accepted: " + String.join(", ", allowed));
        }
        return detected;
    }

    /** Returns null when nothing matches. */
    private String sniff(byte[] b) {
        if (startsWith(b, 0x25, 0x50, 0x44, 0x46)) {                       // %PDF
            return "pdf";
        }
        if (startsWith(b, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) { // PNG
            return "png";
        }
        if (startsWith(b, 0xFF, 0xD8, 0xFF)) {                             // JPEG
            return "jpeg";
        }
        // WEBP is RIFF....WEBP — the size field sits between, so check both ends.
        if (startsWith(b, 0x52, 0x49, 0x46, 0x46) && b.length >= 12
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        // .docx (and every other OOXML file) is a zip archive.
        if (startsWith(b, 0x50, 0x4B, 0x03, 0x04)) {
            return "docx";
        }
        // Legacy .doc — OLE2 compound document.
        if (startsWith(b, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
            return "doc";
        }
        return null;
    }

    private boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
