import os

target_dir = r"C:\Users\Home\KodaHosting\KodaNetwork\app\src\main\java\eu\kodanetwork\mchost\ui"
search_str = '"https://scsezpfrrmpyuapblbxk.supabase.co'
replacement_str = 'eu.kodanetwork.mchost.security.PraetorSecurity.getSupabaseUrl() + "'

for root, _, files in os.walk(target_dir):
    for f in files:
        if f.endswith(".java"):
            path = os.path.join(root, f)
            with open(path, "r", encoding="utf-8") as file:
                content = file.read()
            
            if search_str in content:
                new_content = content.replace(search_str, replacement_str)
                with open(path, "w", encoding="utf-8") as file:
                    file.write(new_content)
                print(f"Updated {f}")
