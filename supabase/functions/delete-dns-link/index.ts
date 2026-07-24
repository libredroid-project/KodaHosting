import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.6";
import { corsHeaders } from "../_shared/cors.ts";
import { validateHost } from "../_shared/validation.ts";

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

    const validationError = validateHost(host);
    if (validationError) {
      return new Response(JSON.stringify({ error: validationError }), {
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

    // 3. SURGICAL matching: Fetch and delete exactly
    const fqdn = `${host}.${domain}`;
    const srvFqdnTcp = `_minecraft._tcp.${fqdn}`;
    const srvFqdnUdp = `_minecraft._udp.${fqdn}`;
    const srvVoiceChatUdp = `_voicechat._udp.${fqdn}`;
    let deletedCount = 0;
    
    const recordsToCheck = [
      { type: "A", name: fqdn },
      { type: "CNAME", name: fqdn },
      { type: "SRV", name: srvFqdnTcp },
      { type: "SRV", name: srvFqdnUdp },
      { type: "SRV", name: srvVoiceChatUdp }
    ];
    
    // Fetch all records concurrently
    const fetchPromises = recordsToCheck.map(item => 
      fetch(`https://api.hosting.ionos.com/dns/v1/zones/${zoneId}?recordName=${item.name}&recordType=${item.type}`, {
        headers: { "X-API-Key": fullApiKey }
      }).then(res => res.ok ? res.json() : null).then(data => ({ item, data }))
    );
    
    const results = await Promise.all(fetchPromises);
    
    // Collect all record IDs to delete
    const recordsToDelete: string[] = [];
    for (const res of results) {
      if (res.data && Array.isArray(res.data.records)) {
        for (const rec of res.data.records) {
          recordsToDelete.push(rec.id);
        }
      }
    }
    
    // Delete all records concurrently
    const deletePromises = recordsToDelete.map(recId => 
      fetch(`https://api.hosting.ionos.com/dns/v1/zones/${zoneId}/records/${recId}`, {
        method: "DELETE",
        headers: { "X-API-Key": fullApiKey }
      })
    );
    
    const delResults = await Promise.all(deletePromises);
    deletedCount = delResults.filter(r => r.ok).length;

    // Delete allocated ports from koda_ports table
    const supabaseAdmin = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );
    await supabaseAdmin.from('koda_ports').delete().eq('host', host);

    return new Response(JSON.stringify({ 
        ok: true, 
        host, 
        deletedCount
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
