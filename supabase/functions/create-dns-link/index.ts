import { corsHeaders } from "../_shared/cors.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405, headers: corsHeaders });

  try {
    const body = await req.json();
    const host = String(body.host ?? "").toLowerCase();
    const target = String(body.target ?? "").toLowerCase(); // VPS IP
    const port = Number(body.port ?? 0);

    if (!host || !target || !port) {
      return new Response(JSON.stringify({ error: "missing_parameters" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const apiKeyPrefix = Deno.env.get("IONOS_API_PREFIX") ?? "";
    const apiSecret = Deno.env.get("IONOS_API_SECRET") ?? "";
    const fullApiKey = `${apiKeyPrefix}.${apiSecret}`;
    const domain = "kodanetwork.eu";
    
    // 1. Get Zone ID
    const zonesRes = await fetch("https://api.hosting.ionos.com/dns/v1/zones", {
      headers: { "X-API-Key": fullApiKey }
    });
    const zonesData = await zonesRes.json();
    const zone = (zonesData as any[])?.find((z: any) => z.name === domain);
    if (!zone) throw new Error("Zone kodanetwork.eu not found");
    const zoneId = zone.id;

    // Use absolute FQDNs for IONOS API
    const type = String(body.type ?? "tcp").toLowerCase();
    const fqdn = `${host}.${domain}`;
    const srvFqdn = `_minecraft._${type}.${fqdn}`;

    // We no longer blindly delete existing records. If the record exists, the POST will fail and that is the desired behavior to prevent subdomain stealing.

    // 3. Create A-Record (Points subdomain to VPS IP)
    const createARes = await fetch(`https://api.hosting.ionos.com/dns/v1/zones/${zoneId}/records`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-API-Key": fullApiKey },
      body: JSON.stringify([{
        name: fqdn,
        type: "A",
        content: target, // VPS IP
        ttl: 3600,
        disabled: false
      }])
    });

    if (!createARes.ok) {
        const errText = await createARes.text();
        throw new Error("A-Record creation failed: " + errText);
    }

    // 4. Create SRV record (Points to the A-Record with the Minecraft Port)
    const createSrvRes = await fetch(`https://api.hosting.ionos.com/dns/v1/zones/${zoneId}/records`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-API-Key": fullApiKey },
      body: JSON.stringify([{
        name: srvFqdn,
        type: "SRV",
        content: `0 ${port} ${fqdn}.`,
        ttl: 3600,
        prio: 0,
        disabled: false
      }])
    });

    if (!createSrvRes.ok) {
        const errText = await createSrvRes.text();
        throw new Error("SRV-Record creation failed: " + errText);
    }

    return new Response(JSON.stringify({ ok: true, host, vps: target, port }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });

  } catch (e: any) {
    return new Response(JSON.stringify({ error: "internal_error", detail: e.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
