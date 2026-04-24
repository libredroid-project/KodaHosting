package eu.kodanetwork.mchost.network.supabase;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SupabaseFunctionApi {
    @POST("functions/v1/{name}")
    Call<Map<String, Object>> callFunction(
        @Path("name") String functionName,
        @Header("apikey") String anonKey,
        @Header("Authorization") String bearer,
        @Body Map<String, Object> body
    );
}
