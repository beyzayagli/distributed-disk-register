package com.example.family;

public class ToleranceConfig {
    private static int tolerance = -1;
    
    public static int getTolerance() {
        if (tolerance == -1) {
            loadTolerance();
        }
        return tolerance;
    }
    
    private static void loadTolerance() {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.FileReader("tolerance.conf"));
            String line = br.readLine();
            br.close();
            
            if (line != null && line.startsWith("TOLERANCE=")) {
                String value = line.substring("TOLERANCE=".length());
                tolerance = Integer.parseInt(value);
                System.out.println("Tolerance yüklendi: " + tolerance);
            }
        } catch (Exception e) {
            System.out.println("Tolerance dosyası okunamadı: " + e.getMessage());
            tolerance = 1;
        }
    }
}
