package eu.kodanetwork.mchost.orchestration;

import android.content.Context;

import java.io.File;

import eu.kodanetwork.mchost.integration.PlayitManager;
import eu.kodanetwork.mchost.integration.TermuxBridge;
import eu.kodanetwork.mchost.integration.TermuxScriptInstaller;
import eu.kodanetwork.mchost.model.ServerInstance;
import eu.kodanetwork.mchost.network.supabase.SupabaseFunctionsClient;
import eu.kodanetwork.mchost.service.TermuxServerService;

public class StartOrchestrator {
    public enum Step {
        PREPARE,
        JAVA_DOWNLOAD,
        JAVA_INSTALL,
        SERVER_START,
        TUNNEL_START,
        DNS_LINK,
        READY
    }

    public interface Callback {
        void onStep(Step step, String message);
        void onCompleted(String playitAddress, String domainLink);
        void onError(Step step, String message);
        void requestServerStartIntent();
    }

    private final Context context;
    private final SupabaseFunctionsClient supabaseClient;
    private final Callback callback;

    public StartOrchestrator(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.supabaseClient = new SupabaseFunctionsClient(context);
    }

    public void run(ServerInstance server, String userJwt) throws Exception {
        eu.kodanetwork.mchost.util.AppLogger.log("Orchestrator", "Running native Termux start sequence...");
        
        if (!TermuxBridge.isTermuxInstalled(context)) {
            callback.onError(Step.PREPARE, "Termux is missing");
            return;
        }

        callback.onStep(Step.PREPARE, "Termux ok");
        callback.onStep(Step.SERVER_START, "Launching...");
        
        // Just trigger the service action
        callback.requestServerStartIntent();
        
        callback.onStep(Step.READY, "Sequence finished.");
    }
}
