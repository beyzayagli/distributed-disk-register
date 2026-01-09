package com.example.family;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import family.ChatMessage;
import family.Empty;
import family.FamilyServiceGrpc;
import family.FamilyView;
import family.NodeInfo;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class NodeMain {

    private static final int START_PORT = 5555;
    private static final int PRINT_INTERVAL_SECONDS = 10;

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = findFreePort(START_PORT);

        NodeInfo self = NodeInfo.newBuilder()
                .setHost(host)
                .setPort(port)
                .build();

        NodeRegistry registry = new NodeRegistry();
        FamilyServiceImpl service = new FamilyServiceImpl(registry, self);
        StorageServiceImpl storageService = new StorageServiceImpl(port);

        Server server = ServerBuilder
                .forPort(port)
                .addService(service)
                .addService(storageService)

                .build()
                .start();

                System.out.printf("Node started on %s:%d%n", host, port);

                // Eğer bu ilk node ise (port 5555), TCP 6666'da text dinlesin
                if (port == START_PORT) {
                    startLeaderTextListener(registry, self);
                }

                discoverExistingNodes(host, port, registry, self);
                CommandParser.setRegistry(registry);
                CommandParser.setSelfPort(port);
                startFamilyPrinter(registry, self, storageService);
                startHealthChecker(registry, self);

                server.awaitTermination();




    }

    private static void startLeaderTextListener(NodeRegistry registry, NodeInfo self) {
    // Sadece lider (5555 portlu node) bu methodu çağırmalı
    new Thread(() -> {
        try (ServerSocket serverSocket = new ServerSocket(6666)) {
            System.out.printf("Leader listening for text on TCP %s:%d%n",
                    self.getHost(), 6666);

            while (true) {
                Socket client = serverSocket.accept();
                new Thread(() -> handleClientTextConnection(client, registry, self)).start();
            }

        } catch (IOException e) {
            System.err.println("Error in leader text listener: " + e.getMessage());
        }
    }, "LeaderTextListener").start();
}

private static void handleClientTextConnection(Socket client,
                                               NodeRegistry registry,
                                               NodeInfo self) {
    System.out.println("New TCP client connected: " + client.getRemoteSocketAddress());
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(client.getInputStream()));
         java.io.PrintWriter writer = new java.io.PrintWriter(client.getOutputStream(), true)) {

        String line;
        while ((line = reader.readLine()) != null) {
            String text = line.trim();
            if (text.isEmpty()) continue;

            Object cmd = CommandParser.parse(text);
            String response = null;
            
            if (cmd instanceof SetCommand) {
                SetCommand setCmd = (SetCommand) cmd;
                response = setCmd.execute();
                System.out.println("SET yapıldı: " + setCmd.key + " = " + setCmd.value);
            } else if (cmd instanceof GetCommand) {
                GetCommand getCmd = (GetCommand) cmd;
                response = getCmd.execute();
                System.out.println("GET yapıldı: " + getCmd.key);
            }
            
            // İstemciye response gönder
            if (response != null) {
                writer.println(response);
                System.out.println("Response gönderildi: " + response);
            }

        }

    } catch (IOException e) {
        System.err.println("TCP client handler error: " + e.getMessage());
    } finally {
        try { client.close(); } catch (IOException ignored) {}
    }
}

private static void broadcastToFamily(NodeRegistry registry,
                                      NodeInfo self,
                                      ChatMessage msg) {

    List<NodeInfo> members = registry.snapshot();

    for (NodeInfo n : members) {
        // Kendimize tekrar gönderme
        if (n.getHost().equals(self.getHost()) && n.getPort() == self.getPort()) {
            continue;
        }

        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder
                    .forAddress(n.getHost(), n.getPort())
                    .usePlaintext()
                    .build();

            FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                    FamilyServiceGrpc.newBlockingStub(channel);

            stub.receiveChat(msg);

            System.out.printf("Broadcasted message to %s:%d%n", n.getHost(), n.getPort());

        } catch (Exception e) {
            System.err.printf("Failed to send to %s:%d (%s)%n",
                    n.getHost(), n.getPort(), e.getMessage());
        } finally {
            if (channel != null) channel.shutdownNow();
        }
    }
}


    private static int findFreePort(int startPort) {
        int port = startPort;
        while (true) {
            try (ServerSocket ignored = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                port++;
            }
        }
    }

    private static void discoverExistingNodes(String host,
                                              int selfPort,
                                              NodeRegistry registry,
                                              NodeInfo self) {

        for (int port = START_PORT; port < selfPort; port++) {
            ManagedChannel channel = null;
            try {
                channel = ManagedChannelBuilder
                        .forAddress(host, port)
                        .usePlaintext()
                        .build();

                FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                        FamilyServiceGrpc.newBlockingStub(channel);

                FamilyView view = stub.join(self);
                registry.addAll(view.getMembersList());

                System.out.printf("Joined through %s:%d, family size now: %d%n",
                        host, port, registry.snapshot().size());

            } catch (Exception ignored) {
            } finally {
                if (channel != null) channel.shutdownNow();
            }
        }
    }

    private static void startFamilyPrinter(NodeRegistry registry, NodeInfo self, StorageServiceImpl storageService) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        boolean isLeader = self.getPort() == START_PORT;

        scheduler.scheduleAtFixedRate(() -> {
            List<NodeInfo> members = registry.snapshot();
            System.out.println("======================================");
            System.out.printf("Family at %s:%d (me)%n", self.getHost(), self.getPort());
            System.out.println("Time: " + LocalDateTime.now());
            System.out.println("Members:");

            Map<String, Integer> distribution = CommandParser.getMessageDistribution();


            for (NodeInfo n : members) {
                boolean isMe = n.getHost().equals(self.getHost()) && n.getPort() == self.getPort();
                String key = n.getHost() + ":" + n.getPort();
                int msgCount = distribution.getOrDefault(key, 0);
                
                if (isLeader && !isMe) {
                    System.out.printf(" - %s:%d [%d msg]%n", n.getHost(), n.getPort(), msgCount);
                } else {
                    System.out.printf(" - %s:%d%s%n", n.getHost(), n.getPort(), isMe ? " (me)" : "");
                }
            }
            
            int localMsgCount = isLeader ? CommandParser.getLocalMessageCount() : storageService.getMessageCount();
            System.out.println("Local messages: " + localMsgCount);
            
            if (isLeader) {
                int total = 0;
                for (int count : distribution.values()) {
                    total += count;
                }
                System.out.println("Total tracked: " + total);
            }
            
            System.out.println("======================================");
        }, 3, PRINT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private static void startHealthChecker(NodeRegistry registry, NodeInfo self) {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    scheduler.scheduleAtFixedRate(() -> {
        List<NodeInfo> members = registry.snapshot();

        for (NodeInfo n : members) {
            // Kendimizi kontrol etmeyelim
            if (n.getHost().equals(self.getHost()) && n.getPort() == self.getPort()) {
                continue;
            }

            ManagedChannel channel = null;
            try {
                channel = ManagedChannelBuilder
                        .forAddress(n.getHost(), n.getPort())
                        .usePlaintext()
                        .build();

                FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                        FamilyServiceGrpc.newBlockingStub(channel);

                // Ping gibi kullanıyoruz: cevap bizi ilgilendirmiyor,
                // sadece RPC'nin hata fırlatmaması önemli.
                stub.getFamily(Empty.newBuilder().build());

            } catch (Exception e) {
                // Bağlantı yok / node ölmüş → listeden çıkar
                System.out.printf("Node %s:%d unreachable, removing from family%n",
                        n.getHost(), n.getPort());
                registry.remove(n);
            } finally {
                if (channel != null) {
                    channel.shutdownNow();
                }
            }
        }

    }, 5, 10, TimeUnit.SECONDS); // 5 sn sonra başla, 10 sn'de bir kontrol et
}

}
