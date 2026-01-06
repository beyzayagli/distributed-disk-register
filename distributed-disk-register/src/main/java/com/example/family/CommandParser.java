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
        CommandParser.messages.put(key, value);
        return "OK";
    }
}

class GetCommand {
    public String key;
    
    public GetCommand(String key) {
        this.key = key;
    }
    
    public String execute() {
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
