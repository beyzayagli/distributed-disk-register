package com.example.family;

import family.MessageId;
import family.StorageServiceGrpc;
import family.StoreResult;
import family.StoredMessage;
import io.grpc.stub.StreamObserver;

public class StorageServiceImpl extends StorageServiceGrpc.StorageServiceImplBase {
        
    private int port;
    private int messageCount = 0;
    
    public StorageServiceImpl(int port) {
        this.port = port;
        java.io.File dir = new java.io.File("messages_" + port);
        if (!dir.exists()) {
            dir.mkdir();
        }
    }
    
    public int getMessageCount() {
        return messageCount;
    }

    @Override
    public void store(StoredMessage request, StreamObserver<StoreResult> responseObserver) {
        try {
            // Dosyaya yaz
            String filename = "messages_" + port + "/" + request.getId() + ".msg";
            java.io.FileWriter fw = new java.io.FileWriter(filename);
            fw.write(request.getText());
            fw.close();
            
            // Response gönder
            messageCount++;
            System.out.println(request.getId() + ".msg kaydedildi [toplam: " + messageCount + "]");

            StoreResult result = StoreResult.newBuilder()
                    .setSuccess(true)
                    .setMessage("Kaydedildi")
                    .build();
            
            responseObserver.onNext(result);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            System.out.println("Store hatası: " + e.getMessage());
            StoreResult result = StoreResult.newBuilder()
                    .setSuccess(false)
                    .setMessage("Hata: " + e.getMessage())
                    .build();
            responseObserver.onNext(result);
            responseObserver.onCompleted();
        }
    }
    
    @Override
    public void retrieve(MessageId request, StreamObserver<StoredMessage> responseObserver) {
        try {
            // Dosyadan oku
            String filename = "messages_" + port + "/" + request.getId() + ".msg";
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.FileReader(filename));
            String content = br.readLine();
            br.close();

            System.out.println("RETRIEVE " + request.getId() + ".msg");

            
            if (content != null) {
                StoredMessage msg = StoredMessage.newBuilder()
                        .setId(request.getId())
                        .setText(content)
                        .build();
                responseObserver.onNext(msg);
            }
            
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            System.out.println("Retrieve hatası: " + e.getMessage());
            responseObserver.onCompleted();
        }
    }
}
