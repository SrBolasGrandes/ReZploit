
package bridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ProxyPipe implements Runnable {
    private final Socket from;
    private final Socket to;
    private final boolean clientToServer;
    
    public ProxyPipe(Socket from, Socket to, boolean clientToServer) {
        this.from = from;
        this.to = to;
        this.clientToServer = clientToServer;
    }
    
    @Override
    public void run() {
        try {
            InputStream input = from.getInputStream();
            OutputStream output = to.getOutputStream();
            
            byte[] buffer = new byte[4096];
            while (true) {
                int count = input.read(buffer);
                if (count == -1) break;
                
                output.write(buffer, 0, count);
                output.flush();
            }
        } catch (IOException e) {
            // Conexão fechada
        } finally {
            try {
                from.close();
                to.close();
            } catch (IOException ignored) {}
        }
    }
}
