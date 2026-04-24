package eu.kodanetwork.mchost.network.ionos;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface IonosDnsApi {
    @GET("zones")
    Call<IonosDnsModels.ZonesResponse> listZones(@Header("X-API-Key") String apiKeyHeader);

    @GET("zones/{zoneId}/records")
    Call<IonosDnsModels.RecordsResponse> listRecords(
        @Header("X-API-Key") String apiKeyHeader,
        @Path("zoneId") String zoneId,
        @Query("recordName") String recordName,
        @Query("recordType") String recordType
    );

    @POST("zones/{zoneId}/records")
    Call<Void> createRecords(
        @Header("X-API-Key") String apiKeyHeader,
        @Path("zoneId") String zoneId,
        @Body java.util.List<IonosDnsModels.RecordRequest> requests
    );

    @retrofit2.http.DELETE("zones/{zoneId}/records/{recordId}")
    Call<Void> deleteRecord(
        @Header("X-API-Key") String apiKeyHeader,
        @Path("zoneId") String zoneId,
        @Path("recordId") String recordId
    );
}
