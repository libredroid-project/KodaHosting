package org.librecommunications.app.network.p2p;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class P2pServerThread extends Thread {

    private static final String TAG = "P2pServerThread";
    private ServerSocket serverSocket;
    private final MessageHandler handler;
    private volatile boolean running = true;

    public interface MessageHandler {
        void onMessageReceived(String message, String senderAddress);
    }

    public P2pServerThread(MessageHandler handler) {
        this.handler = handler;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(8888);
            Log.d(TAG, "Server socket opened on port 8888");
            
            while (running) {
                Socket client = serverSocket.accept();
                Log.d(TAG, "Client connected: " + client.getInetAddress().getHostAddress());
                
                // Read data
                InputStream inputStream = client.getInputStream();
                byte[] buffer = new byte[4096];
                int bytesRead = inputStream.read(buffer);
                
                if (bytesRead > 0) {
                    String message = new String(buffer, 0, bytesRead);
                    if (handler != null) {
                        handler.onMessageReceived(message, client.getInetAddress().getHostAddress());
                    }
                }
                client.close();
            }
        } catch (IOException e) {
            if (running) {
                Log.e(TAG, "Server socket error", e);
            }
        }
    }

    public void stopServer() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
