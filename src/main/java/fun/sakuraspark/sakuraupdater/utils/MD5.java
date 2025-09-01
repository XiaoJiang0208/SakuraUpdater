package fun.sakuraspark.sakuraupdater.utils;

public class MD5 {
    // MD5计算方法
    public static String calculateMD5(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    // MD5计算方法，接受字符串输入
    public static String calculateStringMD5(String input) {
        return calculateMD5(input.getBytes());
    }

    // MD5计算方法，接受文件路径输入
    public static String calculateMD5(java.io.File file) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            fis.close();
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Error calculating MD5 for file: " + file.getPath(), e);
        }
    }
}

