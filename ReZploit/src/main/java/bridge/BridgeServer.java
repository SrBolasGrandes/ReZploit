package bridge;

import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class BridgeServer {
    public static void main(String[] args) {
        int port = 25565;
        String serversFile = "servers.txt";
        
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i])) port = Integer.parseInt(args[++i]);
            if ("--servers".equals(args[i])) serversFile = args[++i];
        }
        
        File file = new File(serversFile);
        if (!file.exists()) {
            System.out.println("Crie " + serversFile + " com formato: nome=ip:porta");
            return;
        }
        
        Scanner scanner = new Scanner(file);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("=");
            if (parts.length == 2) {
                System.out.println("Servidor carregado: " + parts[0] + " -> " + parts[1]);
            }
        }
        scanner.close();
        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Bridge rodando em *: " + port);
            System.out.println("Arquivo servers: " + serversFile);
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ProxyConnection(clientSocket, serversFile)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
                }
