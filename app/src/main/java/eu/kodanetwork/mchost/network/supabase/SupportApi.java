package eu.kodanetwork.mchost.network.supabase;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import eu.kodanetwork.mchost.security.PraetorSecurity;

public class SupportApi {

    public static String makeSupabaseRequest(String endpoint, String method, String jsonBody) throws Exception {
        URL url = new URL(PraetorSecurity.getSupabaseUrl() + "/" + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("apikey", PraetorSecurity.getSupabaseKey());
        conn.setRequestProperty("Authorization", "Bearer " + PraetorSecurity.getSupabaseKey());
        conn.setRequestProperty("Content-Type", "application/json");

        if (jsonBody != null && (method.equals("POST") || method.equals("PATCH"))) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
        }

        int code = conn.getResponseCode();
        try (Scanner scanner = new Scanner(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(), "UTF-8")) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}
