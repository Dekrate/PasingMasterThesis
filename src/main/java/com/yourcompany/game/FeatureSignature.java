package com.yourcompany.game;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class FeatureSignature {
    private final String content;
    private final String contextHash;

    public FeatureSignature(String featureContent, String surroundingContext) {
        this.content = featureContent;
        // Zakładamy, że featureContent zawiera teraz również hash commita (dodany przez analizatory)
        // w formacie "ścieżka::linia::zawartość::commitHash"
        this.contextHash = calculateContextHash(featureContent, surroundingContext);
    }

    private String calculateContextHash(String featureContent, String surroundingContext) {
        // Użyj całej sygnatury (która zawiera hash commita) oraz kontekstu do wygenerowania unikalnego hasha
        String combinedContent = featureContent + "#" + normalizeContext(surroundingContext);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combinedContent.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(combinedContent.hashCode());
        }
    }

    private String normalizeContext(String context) {
        // Usuń białe znaki i komentarze, zostaw tylko istotną treść
        return context.replaceAll("\\s+", "")
                     .replaceAll("//.*|/\\*.*?\\*/", "");
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FeatureSignature that = (FeatureSignature) o;
        return Objects.equals(contextHash, that.contextHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contextHash);
    }

    public String getSignature() {
        return contextHash;
    }
}
