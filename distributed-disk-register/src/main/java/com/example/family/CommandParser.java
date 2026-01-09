package com.example.family;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
                
                int tolerance = ToleranceConfig.getTolerance();
                List<family.NodeInfo> members = CommandParser.getMembers();
                
                if (members.size() > 0) {
                    int replicaCount = Math.min(tolerance, members.size());
                    System.out.println("Mesaj " + id + " için " + replicaCount + " üyeye gönderiliyor");
                    
                    for (int i = 0; i < replicaCount; i++) {
                        family.NodeInfo member = members.get(i);
                        System.out.println("Gönderiliyor: " + member.getHost() + ":" + member.getPort());

                          
                        try {
                            io.grpc.ManagedChannel channel = io.grpc.ManagedChannelBuilder
                                    .forAddress(member.getHost(), member.getPort())
                                    .usePlaintext()
                                    .build();
                            
                            family.StorageServiceGrpc.StorageServiceBlockingStub stub = 
                                    family.StorageServiceGrpc.newBlockingStub(channel);
                            
                            family.StoreResult result = stub.store(msg);
                            
                            if (result.getSuccess()) {
                                System.out.println("Başarılı: " + member.getHost() + ":" + member.getPort());
                                CommandParser.addMessageLocation(id, member.getHost() + ":" + member.getPort());
                            } else {
                                System.out.println("Başarısız: " + result.getMessage());
                            }

                            channel.shutdownNow();

                            
                        } catch (Exception ex) {
                            System.out.println("gRPC hatası: " + ex.getMessage());
                        }
                    }
                }
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
                    System.out.println("Mesaj diskten bulundu: " + key);
                    return content;
                }
            }
        }catch (Exception e) {
            //dosya yoksa RAM'den oku
        }

        String value = CommandParser.messages.get(key);
        if (value != null) {
            return value;
        }

        // Üyelerden gRPC ile oku
        List<family.NodeInfo> members = CommandParser.getMembers();
        for (family.NodeInfo member : members) {
            try {
                int id = Integer.parseInt(key);
                family.MessageId msgId = family.MessageId.newBuilder()
                        .setId(id)
                        .build();
                
                io.grpc.ManagedChannel channel = io.grpc.ManagedChannelBuilder
                        .forAddress(member.getHost(), member.getPort())
                        .usePlaintext()
                        .build();
                
                family.StorageServiceGrpc.StorageServiceBlockingStub stub = 
                        family.StorageServiceGrpc.newBlockingStub(channel);
                
                family.StoredMessage result = stub.retrieve(msgId);
                
                if (result != null && !result.getText().isEmpty()) {
                    System.out.println("Mesaj üyeden bulundu: " + member.getHost() + ":" + member.getPort());
                    channel.shutdownNow();
                    return result.getText();
                }
                
                channel.shutdownNow();
                
            } catch (Exception ex) {
                System.out.println("Üye erişim hatası: " + ex.getMessage());
            }
        }
        
        return "NOT_FOUND";
    }
}

public class CommandParser {
    // Mesajları burada tutuyoruz
    public static Map<String, String> messages = new HashMap<>();
    private static List<family.NodeInfo> members = new ArrayList<>();
    private static Map<Integer, List<String>> messageLocations = new HashMap<>();

    public static void setMembers(List<family.NodeInfo> memberList) {
        members = memberList;
    }
    
    public static List<family.NodeInfo> getMembers() {
        return members;
    }

    public static void addMessageLocation(int messageId, String location) {
        messageLocations.computeIfAbsent(messageId, k -> new ArrayList<>()).add(location);
    }
    
    public static List<String> getMessageLocations(int messageId) {
        return messageLocations.getOrDefault(messageId, new ArrayList<>());
    }

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
