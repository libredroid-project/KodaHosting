import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.6";
import { corsHeaders } from "../_shared/cors.ts";
import { validateHost } from "../_shared/validation.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const authHeader = req.headers.get("Authorization")?.replace("Bearer ", "");
  const apikey = req.headers.get("apikey");
  if (!authHeader || !apikey) {
    return new Response(JSON.stringify({ error: "unauthorized" }), {
      status: 401,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  try {
    const { host, type } = await req.json();

    if (!host || !type) {
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

    const supabaseAdmin = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    let minPort = 30000;
    let maxPort = 39999;
    if (type === "bedrock") {
      minPort = 40000;
      maxPort = 49999;
    } else if (type === "voicechat") {
      minPort = 50000;
      maxPort = 59999;
    }

    // Fetch all used ports in this range
    const { data: usedPortsData, error: fetchError } = await supabaseAdmin
      .from('koda_ports')
      .select('port')
      .gte('port', minPort)
      .lte('port', maxPort);

    if (fetchError) {
      throw new Error("Failed to fetch ports: " + fetchError.message);
    }

    const usedPorts = new Set(usedPortsData?.map(row => row.port) || []);
    
    // Find available ports
    const availablePorts = [];
    for (let p = minPort; p <= maxPort; p++) {
      if (!usedPorts.has(p)) {
        availablePorts.push(p);
      }
    }

    if (availablePorts.length === 0) {
      return new Response(JSON.stringify({ error: "ports_exhausted" }), {
        status: 409,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Try picking a random available port up to 5 times (in case of race conditions)
    for (let i = 0; i < 5; i++) {
      const randomIndex = Math.floor(Math.random() * availablePorts.length);
      const port = availablePorts[randomIndex];
      
      const { error } = await supabaseAdmin
        .from('koda_ports')
        .insert({ port, host });

      if (!error) {
        return new Response(JSON.stringify({ port }), {
          status: 200,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }
      
      if (error.code !== "23505") { // not a unique violation
         throw new Error(error.message);
      }
      // If unique violation, try again
    }

    return new Response(JSON.stringify({ error: "ports_exhausted" }), {
      status: 409,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });

  } catch (err: any) {
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
