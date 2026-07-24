import urllib.request, json

token = 'sbp_0f187d6f02f1110f3399d4a0a5ed939b2ded4997'
url = 'https://api.supabase.com/v1/projects/scsezpfrrmpyuapblbxk/database/query'
headers = {
    'Authorization': f'Bearer {token}',
    'Content-Type': 'application/json',
    'User-Agent': 'curl/8.4.0'
}

sql = """
CREATE OR REPLACE FUNCTION public.check_app_user_exists(p_app_uuid text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  RETURN EXISTS (SELECT 1 FROM public.koda_users WHERE app_uuid = p_app_uuid);
END;
$$;

DROP POLICY IF EXISTS "Allow insert for app users" ON public.koda_servers;
CREATE POLICY "Allow insert for app users" 
ON public.koda_servers 
FOR INSERT 
TO public 
WITH CHECK (
    public.check_app_user_exists(owner_app_uuid)
);
"""

req = urllib.request.Request(url, data=json.dumps({'query': sql}).encode('utf-8'), headers=headers, method='POST')
try:
    with urllib.request.urlopen(req) as res:
        print("Success:", res.read().decode('utf-8'))
except Exception as e:
    if hasattr(e, 'read'):
        print("Error:", e.read().decode('utf-8'))
    else:
        print("Error:", e)
