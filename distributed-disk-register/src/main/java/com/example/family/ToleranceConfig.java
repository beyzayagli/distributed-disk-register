package com.example.family;

public class ToleranceConfig {
    private static int tolerance = -1;
    private static String ioMode = null;
    
    public static int getTolerance() {
        if (tolerance == -1) {
            loadConfig();
        }
        return tolerance;
    }

    public static String getIoMode() {
        if (ioMode == null) {
            loadConfig();
        }
        return ioMode;
    }
    
    private static void loadConfig() {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.FileReader("tolerance.conf"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("TOLERANCE=")) {
                String value = line.substring("TOLERANCE=".length());
                tolerance = Integer.parseInt(value);
                System.out.println("Tolerance yüklendi: " + tolerance);
            } else if (line.startsWith("IO_MODE=")) {
                    ioMode = line.substring("IO_MODE=".length());
                    System.out.println("IO Mode yüklendi: " + ioMode);
                }
            }
            br.close();
        } catch (Exception e) {
            System.out.println("Config dosyası okunamadı: " + e.getMessage());
            tolerance = 1;
            ioMode = "BUFFERED";
        }

        if (ioMode == null) {
            ioMode = "BUFFERED";
        }
    }
}
