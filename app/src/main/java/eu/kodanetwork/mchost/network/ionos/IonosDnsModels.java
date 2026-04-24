package eu.kodanetwork.mchost.network.ionos;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public final class IonosDnsModels {
    private IonosDnsModels() {}

    public static class RecordRequest {
        @SerializedName("name") public String name;
        @SerializedName("type") public String type;
        @SerializedName("content") public String content;
        @SerializedName("ttl") public int ttl;
        @SerializedName("priority") public Integer priority;
        @SerializedName("disabled") public boolean disabled;

        public static RecordRequest cname(String name, String content) {
            RecordRequest r = new RecordRequest();
            r.name = name;
            r.type = "CNAME";
            r.content = content;
            r.ttl = 60;
            r.priority = null;
            r.disabled = false;
            return r;
        }

        public static RecordRequest srv(String name, String target, int port) {
            RecordRequest r = new RecordRequest();
            r.name = name;
            r.type = "SRV";
            // IONOS format: "weight port target"
            r.content = "0 " + port + " " + target;
            r.ttl = 60;
            r.priority = 0;
            r.disabled = false;
            return r;
        }
    }

    public static class Zone {
        @SerializedName("id") public String id;
        @SerializedName("name") public String name;
    }

    public static class Record {
        @SerializedName("id") public String id;
        @SerializedName("name") public String name;
        @SerializedName("type") public String type;
        @SerializedName("content") public String content;
    }

    public static class ZonesResponse {
        @SerializedName("items") public List<Zone> items = Collections.emptyList();
    }

    public static class RecordsResponse {
        @SerializedName("items") public List<Record> items = Collections.emptyList();
    }
}
