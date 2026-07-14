package org.librecommunications.app.network.p2p;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class P2pClientThread extends Thread {

    private static final String TAG = "P2pClientThread";
    private final String hostAddress;
    private final String message;
    private final int port = 8888;
    
    private final SendCallback callback;

    public interface SendCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    public P2pClientThread(String hostAddress, String message, SendCallback callback) {
        this.hostAddress = hostAddress;
        this.message = message;
        this.callback = callback;
    }

    @Override
    public void run() {
        Socket socket = new Socket();
        try {
            Log.d(TAG, "Opening client socket to " + hostAddress);
            socket.bind(null);
            socket.connect(new InetSocketAddress(hostAddress, port), 5000);
            
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(message.getBytes());
            outputStream.flush();
            Log.d(TAG, "Message sent successfully");
            
            if (callback != null) {
                callback.onSuccess();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to send message", e);
            if (callback != null) {
                callback.onFailure(e);
            }
        } finally {
            if (socket != null && socket.isConnected()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
