import { corsHeaders } from "../_shared/cors.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405, headers: corsHeaders });

  try {
    const body = await req.json();
    const host = String(body.host ?? "").toLowerCase();
    const target = String(body.target ?? "").toLowerCase();
    const port = Number(body.port ?? 0);

    if (!host || !target || !port) {
      return new Response(JSON.stringify({ error: "missing_parameters" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const apiKeyPrefix = Deno.env.get("IONOS_API_PREFIX") ?? "";
    const apiSecret = Deno.env.get("IONOS_API_SECRET") ?? "";
    if (!apiKeyPrefix || !apiSecret) {
      return new Response(JSON.stringify({ error: "missing_ionos_credentials" }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const fullApiKey = `${apiKeyPrefix}.${apiSecret}`;
    const domain = "kodanetwork.eu";
    
    // 1. Get Zone ID
    const zonesRes = await fetch("https://api.hosting.ionos.com/dns/v1/zones", {
      headers: { "X-API-Key": fullApiKey }
    });
    if (!zonesRes.ok) throw new Error("Failed to fetch zones: " + await zonesRes.text());
    
    const zonesData = await zonesRes.json();
    const zone = (zonesData as any[])?.find((z: any) => z.name === domain);
    if (!zone) throw new Error("Zone kodanetwork.eu not found");
    const zoneId = zone.id;

    const fqdn = `${host}.${domain}`;
    const srvName = `_minecraft._tcp.${host}`;
    const finalTarget = target.endsWith(".") ? target : target + ".";

    // 2. Clean up old CNAME records to avoid conflict
    const cnameRes = await fetch(`https://api.hosting.ionos.com/dns/v1/zones/${zoneId}/records?recordName=${fqdn}&recordType=CNAME`, {
      headers: { "X-API-Key": fullApiKey }
    });
    if (cnameRes.ok) {
      const existing = await cnameRes.json();
      for (const rec of (existing as any[]) || []) {
        await fetch(`https://api.hosting.ionos.com/dns/v1/zones/${zoneId}/records/${rec.id}`, {
          method: "DELETE",
          headers: { "X-API-Key": fullApiKey }
        });
      }
    }

    // 3. Clean up old SRV records
    const srvRes = await fetch(`https://api.hosting.ionos.com/dns/v1/zones/${zoneId}/records?recordName=${srvName}.${domain}&recordType=SRV`, {
      headers: { "X-API-Key": fullApiKey }
    });
    if (srvRes.ok) {
      const existing = await srvRes.json();
      for (const rec of (existing as any[]) || []) {
        await fetch(`https://api.hosting.ionos.com/dns/v1/zones/${zoneId}/records/${rec.id}`, {
          method: "DELETE",
          headers: { "X-API-Key": fullApiKey }
        });
      }
    }

    // 4. Create new CNAME and SRV records
    const cnamePayload = [{
      name: fqdn,
      type: "CNAME",
      content: finalTarget,
      ttl: 3600,
      disabled: false
    }];

    const srvPayload = [{
      name: srvName + "." + domain,
      type: "SRV",
      content: `0 ${port} ${finalTarget}`,
      ttl: 3600,
      prio: 0,
      disabled: false
    }];

    const createCnameRes = await fetch(`https://api.hosting.ionos.com/dns/v1/zones/${zoneId}/records`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-API-Key": fullApiKey
      },
      body: JSON.stringify(cnamePayload)
    });

    if (!createCnameRes.ok) {
      const errorText = await createCnameRes.text();
      console.error("IONOS CNAME Error:", errorText);
      throw new Error(`Failed to create CNAME: HTTP ${createCnameRes.status} - ${errorText}`);
    }

    const createSrvRes = await fetch(`https://api.hosting.ionos.com/dns/v1/zones/${zoneId}/records`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-API-Key": fullApiKey
      },
      body: JSON.stringify(srvPayload)
    });

    if (!createSrvRes.ok) {
      const errorText = await createSrvRes.text();
      console.error("IONOS SRV Error:", errorText);
      throw new Error(`Failed to create SRV: HTTP ${createSrvRes.status} - ${errorText}`);
    }

    return new Response(JSON.stringify({ ok: true, host, target, port }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });

  } catch (e: any) {
    return new Response(JSON.stringify({ error: "internal_error", detail: e.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
