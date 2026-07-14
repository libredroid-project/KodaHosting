package org.librecommunications.app.crypto;

import java.util.HashMap;
import java.util.Map;

public class SessionManager {
    private static SessionManager instance;
    private final Map<String, E2EEManager> activeSessions = new HashMap<>();
    
    // My identity key pair
    private final E2EEManager myIdentity;

    private SessionManager() {
        myIdentity = new E2EEManager();
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public String getMyPublicKey() {
        return myIdentity.getMyPublicKeyBase64();
    }

    public E2EEManager getSession(String deviceAddress) {
        if (!activeSessions.containsKey(deviceAddress)) {
            // E2EEManager internally generates a unique ephemeral keypair per session for Forward Secrecy
            E2EEManager session = new E2EEManager();
            activeSessions.put(deviceAddress, session);
        }
        return activeSessions.get(deviceAddress);
    }
    
    public void establishSession(String deviceAddress, String peerPublicKey) {
        E2EEManager session = getSession(deviceAddress);
        session.establishSharedSecret(peerPublicKey);
    }

    public boolean hasSecureSession(String deviceAddress) {
        return activeSessions.containsKey(deviceAddress) && activeSessions.get(deviceAddress).isSecureSessionEstablished();
    }
}
