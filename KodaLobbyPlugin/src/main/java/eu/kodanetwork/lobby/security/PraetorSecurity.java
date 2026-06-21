package eu.kodanetwork.lobby.security;

import java.util.Base64;

public class PraetorSecurity {
    
    // Obfuscated Supabase configuration
    private static final String OBF_URL = "==wbj5SZzFmYhBXdz5ya4JGbiBXY1lHctJncmBnelN3Yz9yL6MHc0RHa";
    private static final String OBF_KEY = "==AOUplYOxENR1yNtQ3bsVVSIlEOZt0c6llevhnRhVEeuFESYBXUrZzbyhkcuAjbOl3Z65EeVpWT1EkaNZTSDNGNW1WSzllaNRTRE5UNZpnTzUkaPlWUYlFcKNETpRjMiVnRtlkNJNlWzlTbjl2dplkco5WWzp0RjhmVYV2dx02Y5p1RjZjVyMmaO5WS2kUaaxmSul0cJNlW6ZUbZhmQYRmeKl2Tp10MjBnS5VmL5o0QWhFcrlkNJN0Y1IlbJNXSp5UMJpXVJpUaPl2YHJGaKlXZ";

    private static String decode(String obf) {
        String reversed = new StringBuilder(obf).reverse().toString();
        return new String(Base64.getDecoder().decode(reversed), java.nio.charset.StandardCharsets.UTF_8);
    }

    public static String getSupabaseUrl() {
        return decode(OBF_URL);
    }

    public static String getSupabaseKey() {
        return decode(OBF_KEY);
    }
}
