import { corsHeaders } from "../_shared/cors.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "GET" && req.method !== "POST") return new Response("Method not allowed", { status: 405, headers: corsHeaders });

  const authHeader = req.headers.get("Authorization")?.replace("Bearer ", "");
  const apikey = req.headers.get("apikey");
  if (!authHeader || !apikey) {
    return new Response(JSON.stringify({ error: "unauthorized" }), {
      status: 401,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  try {
    const apiKeyPrefix = Deno.env.get("IONOS_API_PREFIX") ?? "";
    const apiSecret = Deno.env.get("IONOS_API_SECRET") ?? "";
    const fullApiKey = `${apiKeyPrefix}.${apiSecret}`;
    const domain = "kodanetwork.eu";
    
    // 1. Get Zone ID
    const zonesRes = await fetch("https://api.hosting.ionos.com/dns/v1/zones", {
      headers: { "X-API-Key": fullApiKey }
    });
    
    if (!zonesRes.ok) {
        throw new Error("Failed to fetch zones from IONOS");
    }
    
    const zonesData = await zonesRes.json();
    const zone = (zonesData as any[])?.find((z: any) => z.name === domain);
    if (!zone) throw new Error("Zone kodanetwork.eu not found");
    const zoneId = zone.id;

    // 2. Get all records for the zone
    const recordsRes = await fetch(`https://api.hosting.ionos.com/dns/v1/zones/${zoneId}?recordType=A`, {
      headers: { "X-API-Key": fullApiKey }
    });
    
    if (!recordsRes.ok) {
        throw new Error("Failed to fetch records from IONOS");
    }
    
    const recordsData = await recordsRes.json();
    const records = Array.isArray(recordsData.records) ? recordsData.records : [];
    
    // 3. Filter for subdomains of kodanetwork.eu
    const servers: { host: string, target: string }[] = [];
    
    for (const record of records) {
        if (record.type === "A" && record.name.endsWith("." + domain)) {
            const subdomain = record.name.replace("." + domain, "");
            // Filter out system or wildcard records if any, although KodaHosting creates direct A records
            if (subdomain !== "*" && subdomain !== "@" && subdomain !== "www") {
                servers.push({
                    host: subdomain,
                    target: record.content
                });
            }
        }
    }
    const activeHosts = servers.map(s => s.host);
    
    // 4. Cleanup old servers from koda_servers table in Supabase
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    
    if (supabaseUrl && supabaseKey && activeHosts.length > 0) {
      const activeHostsStr = activeHosts.join(",");
      await fetch(`${supabaseUrl}/rest/v1/koda_servers?host=not.in.(${activeHostsStr})`, {
        method: "DELETE",
        headers: {
          "apikey": supabaseKey,
          "Authorization": `Bearer ${supabaseKey}`
        }
      });
    } else if (supabaseUrl && supabaseKey && activeHosts.length === 0) {
      console.warn("IONOS returned 0 active servers. Skipping deletion to prevent data loss.");
    }

    return new Response(JSON.stringify({ ok: true, servers }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });

  } catch (e: any) {
    return new Response(JSON.stringify({ error: "internal_error", detail: e.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
