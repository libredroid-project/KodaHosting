package eu.kodanetwork.mchost.network.ionos;

import android.content.Context;

import java.io.IOException;

import eu.kodanetwork.mchost.security.SecureConfigStore;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class IonosDnsClient {
    private static final String BASE_URL = "https://api.hosting.ionos.com/dns/v1/";

    private final IonosDnsApi api;
    private final SecureConfigStore secureConfigStore;

    public IonosDnsClient(Context context) throws Exception {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
        OkHttpClient okHttpClient = new OkHttpClient.Builder().addInterceptor(logging).build();
        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build();
        api = retrofit.create(IonosDnsApi.class);
        secureConfigStore = new SecureConfigStore(context);
    }

    public void createKodaSubdomainCname(String host, String playitHost) throws IOException {
        String apiHeader = secureConfigStore.getCombinedHeader();
        if (apiHeader.isEmpty()) throw new IOException("IONOS API credentials missing");

        Response<IonosDnsModels.ZonesResponse> zonesResponse = api.listZones(apiHeader).execute();
        if (!zonesResponse.isSuccessful() || zonesResponse.body() == null) {
            throw new IOException("Could not load IONOS zones: HTTP " + zonesResponse.code());
        }

        String zoneId = null;
        for (IonosDnsModels.Zone z : zonesResponse.body().items) {
            if ("kodanetwork.eu".equalsIgnoreCase(z.name)) {
                zoneId = z.id;
                break;
            }
        }
        if (zoneId == null) throw new IOException("Zone kodanetwork.eu not found");

        // IONOS API expects names RELATIVE to the zone.
        // For survival.kodanetwork.eu, the name is just "survival".
        String relativeName = host.toLowerCase();
        String fullFqdn = relativeName + ".kodanetwork.eu";
        
        // Clean up existing CNAME records for this subdomain
        Response<IonosDnsModels.RecordsResponse> existing = api.listRecords(apiHeader, zoneId, fullFqdn, "CNAME").execute();
        if (existing.isSuccessful() && existing.body() != null) {
            for (IonosDnsModels.Record r : existing.body().items) {
                if (r.content.equalsIgnoreCase(playitHost)) return; // Already correct
                api.deleteRecord(apiHeader, zoneId, r.id).execute();
            }
        }

        IonosDnsModels.RecordRequest request = IonosDnsModels.RecordRequest.cname(relativeName, playitHost);
        Response<Void> create = api.createRecords(apiHeader, zoneId, java.util.Collections.singletonList(request)).execute();
        if (!create.isSuccessful()) {
            String error = "";
            try { error = create.errorBody().string(); } catch (Exception ignored) {}
            throw new IOException("IONOS create CNAME failed: HTTP " + create.code() + " " + error);
        }
    }

    public void createKodaSubdomainSrv(String host, String targetHost, int port) throws IOException {
        String apiHeader = secureConfigStore.getCombinedHeader();
        if (apiHeader.isEmpty()) throw new IOException("IONOS API credentials missing");

        Response<IonosDnsModels.ZonesResponse> zonesResponse = api.listZones(apiHeader).execute();
        if (!zonesResponse.isSuccessful() || zonesResponse.body() == null) {
            throw new IOException("Could not load IONOS zones: HTTP " + zonesResponse.code());
        }

        String zoneId = null;
        for (IonosDnsModels.Zone z : zonesResponse.body().items) {
            if ("kodanetwork.eu".equalsIgnoreCase(z.name)) {
                zoneId = z.id;
                break;
            }
        }
        if (zoneId == null) throw new IOException("Zone kodanetwork.eu not found");

        // Minecraft SRV relative name: _minecraft._tcp.subdomain
        String relativeSrvName = "_minecraft._tcp." + host.toLowerCase();
        String fullSrvFqdn = relativeSrvName + ".kodanetwork.eu";
        
        // Target often needs a trailing dot if it's an external domain
        String finalTarget = targetHost.endsWith(".") ? targetHost : targetHost + ".";

        // Clean up existing SRV records
        Response<IonosDnsModels.RecordsResponse> existing = api.listRecords(apiHeader, zoneId, fullSrvFqdn, "SRV").execute();
        if (existing.isSuccessful() && existing.body() != null) {
            for (IonosDnsModels.Record r : existing.body().items) {
                api.deleteRecord(apiHeader, zoneId, r.id).execute();
            }
        }

        IonosDnsModels.RecordRequest request = IonosDnsModels.RecordRequest.srv(relativeSrvName, finalTarget, port);
        Response<Void> create = api.createRecords(apiHeader, zoneId, java.util.Collections.singletonList(request)).execute();
        if (!create.isSuccessful()) {
            String error = "";
            try { error = create.errorBody().string(); } catch (Exception ignored) {}
            throw new IOException("IONOS create SRV failed: HTTP " + create.code() + " " + error);
        }
    }
}
