
package bridge;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ProxyConnection implements Runnable {
    private final Socket clientSocket;
    private final String serversFile;
    
    public ProxyConnection(Socket clientSocket, String serversFile) {
        this.clientSocket = clientSocket;
        this.serversFile = serversFile;
    }
    
    @Override
    public void run() {
        try {
            String targetServer = getTargetServer(clientSocket.getInputStream());
            if (targetServer == null) {
                clientSocket.close();
                return;
            }
            
            String[] parts = targetServer.split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 25565;
            
            Socket serverSocket = new Socket(host, port);
            
            new Thread(new ProxyPipe(clientSocket, serverSocket, true)).start();
            new Thread(new ProxyPipe(serverSocket, clientSocket, false)).start();
            
        } catch (Exception e) {
            try { clientSocket.close(); } catch (Exception ignored) {}
        }
    }
    
    private String getTargetServer(InputStream input) throws IOException {
        byte[] handshake = new byte[1024];
        int len = input.read(handshake);
        
        if (len < 5) return null;
        
        int packetLength = getVarInt(handshake, 0);
        if (len < packetLength) return null;
        
        int packetId = getVarInt(handshake, 1);
        if (packetId != 0x00) return null; // Handshake
        
        int protocol = getVarInt(handshake, getVarIntOffset(handshake, 1));
        int addressLength = getVarInt(handshake, getVarIntOffset(handshake, getVarIntOffset(handshake, 1)));
        
        String address = new String(handshake, 
            getVarIntOffset(handshake, getVarIntOffset(handshake, 1)), 
            addressLength);
        
        Scanner scanner = new Scanner(new File(serversFile));
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("=");
            if (parts.length == 2 && address.equals(parts[0])) {
                scanner.close();
                return parts[1];
            }
        }
        scanner.close();
        return null;
    }
    
    private int getVarInt(byte[] data, int offset) {
        int result = 0;
        int shift = 0;
        int i = offset;
        while (true) {
            byte b = data[i++];
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return result;
    }
    
    private int getVarIntOffset(byte[] data, int offset) {
        int i = offset;
        while ((data[i] & 0x80) != 0) i++;
        return i - offset + 1;
    }
}
