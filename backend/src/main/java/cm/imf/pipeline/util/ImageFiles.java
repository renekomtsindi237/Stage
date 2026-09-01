package cm.imf.pipeline.util;

import org.springframework.web.multipart.MultipartFile;

/**
 * Détection JPEG/PNG/WEBP/GIF : signature binaire, MIME déclaré, extension.
 * Windows envoie souvent un Content-Type vide ou application/octet-stream.
 */
public final class ImageFiles {

    private ImageFiles() {}

    public static String resolveContentType(MultipartFile file, byte[] bytes) {
        String fromMagic = sniff(bytes);
        if (fromMagic != null) {
            return fromMagic;
        }
        String declared = file.getContentType();
        if (declared != null) {
            String normalized = normalizeMime(declared);
            if (isAllowed(normalized)) {
                return normalized;
            }
        }
        String name = file.getOriginalFilename();
        if (name != null) {
            String n = name.toLowerCase();
            if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
            if (n.endsWith(".png")) return "image/png";
            if (n.endsWith(".webp")) return "image/webp";
            if (n.endsWith(".gif")) return "image/gif";
        }
        return null;
    }

    public static String extension(String contentType) {
        String ct = contentType == null ? "" : normalizeMime(contentType);
        return switch (ct) {
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif"  -> ".gif";
            default           -> ".jpg";
        };
    }

    public static boolean isAllowed(String contentType) {
        return "image/jpeg".equals(contentType)
                || "image/png".equals(contentType)
                || "image/webp".equals(contentType)
                || "image/gif".equals(contentType);
    }

    public static String normalizeMime(String contentType) {
        String ct = contentType.toLowerCase().split(";")[0].trim();
        if ("image/jpg".equals(ct) || "image/pjpeg".equals(ct)) {
            return "image/jpeg";
        }
        return ct;
    }

    public static String sniff(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x38) {
            return "image/gif";
        }
        if (bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return "image/webp";
        }
        return null;
    }
}
