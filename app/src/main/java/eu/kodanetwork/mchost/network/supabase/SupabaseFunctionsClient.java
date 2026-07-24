package eu.kodanetwork.mchost.network.supabase;

import android.content.Context;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import eu.kodanetwork.mchost.BuildConfig;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SupabaseFunctionsClient {
    private final SupabaseFunctionApi api;
    private final String anonKey;

    public SupabaseFunctionsClient(Context context) {
        String baseUrl = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalStateException("SUPABASE_URL missing in BuildConfig");
        }
        if (!baseUrl.endsWith("/")) baseUrl += "/";

        this.anonKey = eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseKey();
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
        OkHttpClient okHttpClient = new OkHttpClient.Builder().addInterceptor(logging).build();
        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build();
        this.api = retrofit.create(SupabaseFunctionApi.class);
    }

    public ProvisionResponse provisionJava(String userJwt, String arch) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("arch", arch);
        Response<Map<String, Object>> response = api.callFunction(
            "provision-java",
            anonKey,
            withBearer(userJwt),
            body
        ).execute();
        if (!response.isSuccessful() || response.body() == null) {
            throw new IOException("provision-java failed: HTTP " + response.code());
        }
        Map<String, Object> map = response.body();
        return new ProvisionResponse(
            str(map.get("signedUrl")),
            str(map.get("checksum")),
            str(map.get("objectPath"))
        );
    }

    public PlayitBootstrapResponse bootstrapPlayit(String userJwt, String serverId, int port) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("serverId", serverId);
        body.put("port", port);
        Response<Map<String, Object>> response = api.callFunction(
            "bootstrap-playit",
            anonKey,
            withBearer(userJwt),
            body
        ).execute();
        if (!response.isSuccessful() || response.body() == null) {
            throw new IOException("bootstrap-playit failed: HTTP " + response.code());
        }
        Map<String, Object> map = response.body();
        return new PlayitBootstrapResponse(str(map.get("token")), str(map.get("mode")));
    }

    public void createDnsLink(String userJwt, String host, String baseDomain, String target, int port, String type) throws IOException {
        createDnsLink(userJwt, host, baseDomain, target, port, type, "_minecraft");
    }

    public void createDnsLink(String userJwt, String host, String baseDomain, String target, int port, String type, String service) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("host", host);
        body.put("base_domain", baseDomain);
        body.put("target", target);
        body.put("port", port);
        body.put("type", type);
        body.put("service", service);
        Response<Map<String, Object>> response = api.callFunction(
            "create-dns-link",
            anonKey,
            withBearer(userJwt),
            body
        ).execute();
        if (!response.isSuccessful()) {
            throw new IOException(extractErrorMsg(response, "create-dns-link failed: HTTP " + response.code()));
        }
    }

    public String deleteDnsLink(String userJwt, String host, String baseDomain) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("host", host);
        body.put("base_domain", baseDomain);
        Response<Map<String, Object>> response = api.callFunction(
            "delete-dns-link",
            anonKey,
            withBearer(userJwt),
            body
        ).execute();
        
        String result = "HTTP " + response.code();
        if (response.body() != null) {
            result += " Body: " + response.body().toString();
        }
        
        if (!response.isSuccessful()) {
            throw new IOException(extractErrorMsg(response, "delete-dns-link failed: HTTP " + response.code()));
        }
        return result;
    }

    public boolean checkServerName(String userJwt, String host, String baseDomain) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("host", host);
        body.put("base_domain", baseDomain);
        Response<Map<String, Object>> response = api.callFunction(
            "check-server-name",
            anonKey,
            withBearer(userJwt),
            body
        ).execute();
        
        if (!response.isSuccessful()) {
            throw new IOException(extractErrorMsg(response, "check-server-name failed: HTTP " + response.code()));
        }
        if (response.body() == null) {
            throw new IOException("check-server-name failed: Empty body");
        }
        Map<String, Object> map = response.body();
        return "taken".equals(map.get("status"));
    }

    public int allocatePort(String userJwt, String host, String type) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("host", host);
        body.put("type", type);
        
        Response<Map<String, Object>> response = api.callFunction(
            "allocate-port",
            anonKey,
            withBearer(userJwt),
            body
        ).execute();
        
        if (!response.isSuccessful()) {
            if (response.code() == 409) {
                throw new IOException("ports_exhausted");
            }
            throw new IOException(extractErrorMsg(response, "allocate-port failed: HTTP " + response.code()));
        }
        if (response.body() == null) {
            throw new IOException("allocate-port failed: Empty body");
        }
        
        Number portNum = (Number) response.body().get("port");
        if (portNum == null) throw new IOException("allocate-port returned null port");
        return portNum.intValue();
    }

    private String withBearer(String jwt) {
        if (jwt == null || jwt.isEmpty()) return "Bearer " + anonKey;
        return "Bearer " + jwt;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String extractErrorMsg(Response<?> response, String defaultMsg) {
        try {
            if (response.errorBody() != null) {
                String errorBodyStr = response.errorBody().string();
                org.json.JSONObject errJson = new org.json.JSONObject(errorBodyStr);
                if (errJson.has("error")) {
                    return errJson.getString("error");
                }
                return errorBodyStr;
            }
        } catch (Exception e) {
            // ignore
        }
        return defaultMsg;
    }

    public static class ProvisionResponse {
        public final String signedUrl;
        public final String checksum;
        public final String objectPath;
        public ProvisionResponse(String signedUrl, String checksum, String objectPath) {
            this.signedUrl = signedUrl;
            this.checksum = checksum;
            this.objectPath = objectPath;
        }
    }

    public static class PlayitBootstrapResponse {
        public final String token;
        public final String mode;
        public PlayitBootstrapResponse(String token, String mode) {
            this.token = token;
            this.mode = mode;
        }
    }
}
