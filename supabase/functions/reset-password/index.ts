import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.38.4'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const { email } = await req.json()
    if (!email) {
      throw new Error('Email is required')
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL')!
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
    
    // Create Supabase Admin client to generate a link
    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceKey)
    
    // Generate recovery link
    const { data: linkData, error: linkError } = await supabaseAdmin.auth.admin.generateLink({
      type: 'recovery',
      email: email,
    })

    if (linkError) {
      throw linkError;
    }

    // Replace the default URL (localhost:3000) with the custom Koda URL
    let actionLink = linkData.properties.action_link;
    actionLink = actionLink.replace('http://localhost:3000', 'https://host.kodanetwork.eu/reset');
    
    // Fallback if the user has already changed it in the dashboard or it doesn't contain localhost
    if (!actionLink.includes('host.kodanetwork.eu')) {
        const urlObj = new URL(actionLink);
        actionLink = `https://host.kodanetwork.eu/reset${urlObj.hash}`;
    }

    // Now send via Resend
    const resendApiKey = Deno.env.get('RESEND_API_KEY')!
    const resendRes = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${resendApiKey}`
      },
      body: JSON.stringify({
        from: 'KodaNetwork <noreply@kodanetwork.eu>',
        to: email,
        subject: 'Password Reset - KodaNetwork',
        html: `
          <div style="background-color: #0d0d12; color: white; font-family: sans-serif; padding: 40px; text-align: center;">
            <h1 style="color: #FF6B00; letter-spacing: 2px;">P.R.A.E.T.O.R.</h1>
            <p style="color: #8A8A9A; margin-bottom: 30px;">SECURITY OVERRIDE PROTOCOL INITIATED</p>
            <p>You have requested a password reset for your KodaHosting account.</p>
            <a href="${actionLink}" style="display: inline-block; background-color: #FF6B00; color: white; padding: 15px 30px; text-decoration: none; font-weight: bold; border-radius: 5px; margin: 20px 0;">RESET PASSWORD</a>
            <p style="color: #555; font-size: 12px; margin-top: 40px;">If you did not request this, you can safely ignore this email.</p>
          </div>
        `
      })
    })

    const resData = await resendRes.json()

    return new Response(
      JSON.stringify(resData),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 200 },
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 400 },
    )
  }
})
