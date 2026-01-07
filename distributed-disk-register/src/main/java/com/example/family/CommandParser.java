package com.example.family;

import java.util.HashMap;
import java.util.Map;

class SetCommand {
    public String key;
    public String value;
    
    public SetCommand(String key, String value) {
        this.key = key;
        this.value = value;
    }
    
    public String execute() {
        try{
            // Dosyaya yaz
            java.io.File dir = new java.io.File("messages");
            if (!dir.exists()){
                dir.mkdir();
            }
            try (java.io.FileWriter fw = new java.io.FileWriter("messages/" + key + ".msg")){
                fw.write(value);
            }
            // gRPC ile de gönder
            try {
                int id = Integer.parseInt(key);
                family.StoredMessage msg = family.StoredMessage.newBuilder()
                        .setId(id)
                        .setText(value)
                        .build();
                
                // gRPC channel açılacak
                System.out.println("gRPC'ye gönderilecek: id=" + id + ", text=" + value);
            } catch (Exception e) {
                System.out.println("gRPC hatası: " + e.getMessage());
            }

            //RAM'e de koy
            CommandParser.messages.put(key, value);
            return "OK";
        }catch (Exception e){
            System.out.println("SET hatasi: " + e.getMessage());
            return "ERROR";
        }
        }
    }


class GetCommand {
    public String key;
    
    public GetCommand(String key) {
        this.key = key;
    }
    
    public String execute() {
        try{
            //once diskten oku
            try (java.io.BufferedReader br = new java.io.BufferedReader( new java.io.FileReader("messages/" + key + ".msg"))) {
                String content = br.readLine();
                if (content != null){
                    return content;
                }
            }
        }catch (Exception e) {
            //dosya yoksa RAM'den oku
        }

        String value = CommandParser.messages.get(key);
        if (value != null) {
            return value;
        } else {
            return "NOT_FOUND";
        }
    }
}

public class CommandParser {
    // Mesajları burada tutuyoruz
    public static Map<String, String> messages = new HashMap<>();
    
    public static Object parse(String line) {
        String[] parts = line.split(" ");
        
        if (parts.length == 0) {
            return null;
        }
        
        String cmd = parts[0];
        
        if (cmd.equals("SET")) {
            if (parts.length < 3) {
                System.out.println("Hata: SET key value");
                return null;
            }
            String key = parts[1];
            String value = parts[2];
            return new SetCommand(key, value);
        } 
        else if (cmd.equals("GET")) {
            if (parts.length < 2) {
                System.out.println("Hata: GET key");
                return null;
            }
            String key = parts[1];
            return new GetCommand(key);
        }
        
        // TODO: değerlerde boşluk işleme
        return null;
    }
}
