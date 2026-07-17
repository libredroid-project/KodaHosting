import { corsHeaders } from "../_shared/cors.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405, headers: corsHeaders });

  try {
    const body = await req.json();
    const host = String(body.host ?? "").toLowerCase();

    if (!host) {
      return new Response(JSON.stringify({ error: "missing_parameters" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const apiKeyPrefix = Deno.env.get("IONOS_API_PREFIX") ?? "";
    const apiSecret = Deno.env.get("IONOS_API_SECRET") ?? "";
    const fullApiKey = `${apiKeyPrefix}.${apiSecret}`;
    const domain = String(body.base_domain || "kodanetwork.eu").toLowerCase();
    
    // 1. Get Zone ID
    const zonesRes = await fetch("https://api.hosting.ionos.com/dns/v1/zones", {
      headers: { "X-API-Key": fullApiKey }
    });
    const zonesData = await zonesRes.json();
    const zone = (zonesData as any[])?.find((z: any) => z.name === domain);
    if (!zone) throw new Error(`Zone ${domain} not found`);
    const zoneId = zone.id;

    const fqdn = `${host}.${domain}`;
    
    // 2. Check if A record exists for this subdomain
    const res = await fetch(`https://api.hosting.ionos.com/dns/v1/zones/${zoneId}?recordName=${fqdn}&recordType=A`, {
      headers: { "X-API-Key": fullApiKey }
    });
    
    let isTaken = false;
    if (res.ok) {
        const existing = await res.json();
        const records = Array.isArray(existing.records) ? existing.records : [];
        if (records.length > 0) {
            isTaken = true;
        }
    }

    return new Response(JSON.stringify({ 
        status: isTaken ? "taken" : "allowed",
        host
    }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });

  } catch (e: any) {
    return new Response(JSON.stringify({ error: "internal_error", detail: e.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
