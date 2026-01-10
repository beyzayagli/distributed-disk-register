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
            int port = CommandParser.getSelfPort();
            try (java.io.FileWriter fw = new java.io.FileWriter("messages_" + port + "/" + key + ".msg")) {
                fw.write(value);
            }
            CommandParser.incrementLocalMessageCount();
            System.out.println(key + ".msg kaydedildi [toplam: " + CommandParser.getLocalMessageCount() + "]");

            // gRPC ile de gönder
            try {
                int id = Integer.parseInt(key);
                family.StoredMessage msg = family.StoredMessage.newBuilder()
                        .setId(id)
                        .setText(value)
                        .build();
                
                int tolerance = ToleranceConfig.getTolerance();
                List<family.NodeInfo> selected = CommandParser.selectMembers(tolerance);
                
                if (selected.size() > 0) {
                    System.out.println("Mesaj " + id + " için " + selected.size() + " üyeye gönderiliyor");
                    
                    for (family.NodeInfo member : selected) {
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
        int port = CommandParser.getSelfPort();

        try{
            //once diskten oku
            try (java.io.BufferedReader br = new java.io.BufferedReader( 
                new java.io.FileReader("messages_" + port + "/" + key + ".msg"))) {
                String content = br.readLine();
                if (content != null){
                    System.out.println("GET " + key + " -> LOCAL (messages_" + port + "/" + key + ".msg)");
                    return content;
                }
            }
        }catch (Exception e) {
            //dosya yoksa üyelerden dene
        }

        // Mesajı tutan üyelerden gRPC ile oku
        try {
            int id = Integer.parseInt(key);
            List<String> locations = CommandParser.getMessageLocations(id);
            System.out.println("GET " + key + " -> LOCAL'da yok, üyelerden aranıyor: " + locations);

            for (String location : locations) {
                String[] parts = location.split(":");
                String host = parts[0];
                int memberPort = Integer.parseInt(parts[1]);
                
                io.grpc.ManagedChannel channel = null;
                try {
                    family.MessageId msgId = family.MessageId.newBuilder()
                            .setId(id)
                            .build();
                
                    channel = io.grpc.ManagedChannelBuilder
                            .forAddress(host, memberPort)
                            .usePlaintext()
                            .build();
                
                    family.StorageServiceGrpc.StorageServiceBlockingStub stub = 
                            family.StorageServiceGrpc.newBlockingStub(channel);
                
                    family.StoredMessage result = stub.retrieve(msgId);
                
                    if (result != null && !result.getText().isEmpty()) {
                        System.out.println("GET " + key + " -> REMOTE (" + location + ")");
                        return result.getText();
                    }                

                } catch (Exception ex) {
                    System.out.println("Üye erişim hatası (" + location + "): " + ex.getMessage());
                } finally {
                    if (channel != null) {
                        channel.shutdownNow();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("GET hatası: " + e.getMessage());
        }    
        return "NOT_FOUND";
    }
}

public class CommandParser {
    // Mesajları burada tutuyoruz
    public static Map<String, String> messages = new HashMap<>();
    private static List<family.NodeInfo> members = new ArrayList<>();
    private static Map<Integer, List<String>> messageLocations = new HashMap<>();
    private static int roundRobinIndex = 0;
    private static NodeRegistry registry = null;
    private static int selfPort = 5555;
    private static int localMessageCount = 0;
    
    public static void setSelfPort(int port) {
        selfPort = port;
        java.io.File dir = new java.io.File("messages_" + port);
        if (!dir.exists()) {
            dir.mkdir();
        }
    }
    
    public static int getSelfPort() {
        return selfPort;
    }
    
    public static void incrementLocalMessageCount() {
        localMessageCount++;
    }
    
    public static int getLocalMessageCount() {
        return localMessageCount;
    }
    
    public static void setRegistry(NodeRegistry reg) {
        registry = reg;
    }

    public static void setMembers(List<family.NodeInfo> memberList) {
        members = memberList;
    }
    
    public static List<family.NodeInfo> getMembers() {
        if (registry != null) {
            return registry.snapshot();
        }

        return members;
    }

    public static void addMessageLocation(int messageId, String location) {
        messageLocations.computeIfAbsent(messageId, k -> new ArrayList<>()).add(location);
    }
    
    public static List<String> getMessageLocations(int messageId) {
        return messageLocations.getOrDefault(messageId, new ArrayList<>());
    }

    public static List<family.NodeInfo> selectMembers(int count) {
        List<family.NodeInfo> selected = new ArrayList<>();
        List<family.NodeInfo> currentMembers = getMembers();
        
        // Kendimizi hariç tut
        List<family.NodeInfo> others = new ArrayList<>();
        for (family.NodeInfo m : currentMembers) {
            if (m.getPort() != selfPort) {
                others.add(m);
            }
        }
        
        if (others.size() == 0) return selected;
        
        for (int i = 0; i < count && i < others.size(); i++) {
            int idx = (roundRobinIndex + i) % others.size();
            selected.add(others.get(idx));
        }

        roundRobinIndex = (roundRobinIndex + count) % Math.max(1, others.size());
        return selected;
    }

    public static Map<String, Integer> getMessageDistribution() {
        Map<String, Integer> distribution = new HashMap<>();
        for (List<String> locations : messageLocations.values()) {
            for (String loc : locations) {
                distribution.merge(loc, 1, Integer::sum);
            }
        }
        return distribution;
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
