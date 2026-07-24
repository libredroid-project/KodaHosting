import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders } from "../_shared/cors.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405, headers: corsHeaders });
  }

  try {
    const authHeader = req.headers.get("Authorization") ?? "";
    const body = await req.json();
    const arch = String(body.arch ?? "aarch64");

    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const bucket = Deno.env.get("JDK_STORAGE_BUCKET") ?? "artifacts";
    const objectPath = `openjdk17/openjdk17-${arch}.tar.gz`;
    const checksum = Deno.env.get(`OPENJDK17_SHA256_${arch.toUpperCase()}`) ?? "";

    const admin = createClient(supabaseUrl, serviceRole, {
      global: { headers: { Authorization: authHeader } },
    });

    const { data, error } = await admin.storage.from(bucket).createSignedUrl(objectPath, 60 * 10);
    if (error || !data?.signedUrl) {
      return new Response(JSON.stringify({ error: "signed_url_failed", detail: error?.message }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    return new Response(
      JSON.stringify({
        bucket,
        objectPath,
        checksum,
        signedUrl: data.signedUrl,
      }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  } catch (e) {
    return new Response(JSON.stringify({ error: "internal_error", detail: String(e) }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
