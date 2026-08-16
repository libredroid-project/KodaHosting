import paramiko
import time
import sys

VPS_IP = '85.215.180.87'
VPS_USER = 'root'
VPS_PASS = 'F1a60IJRA2r2l'

FRP_TOKEN = 'xY8!P_k29dLqM#z'

def run_cmd(ssh, cmd):
    print(f"[*] Führe aus: {cmd}")
    stdin, stdout, stderr = ssh.exec_command(cmd)
    out = stdout.read().decode().strip()
    err = stderr.read().decode().strip()
    if err and not out:
        print(f"[!] Fehler/Warnung: {err}")
    return out

def main():
    print(f"[*] Verbinde mit VPS {VPS_IP}...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    
    try:
        ssh.connect(VPS_IP, username=VPS_USER, password=VPS_PASS, timeout=10)
        print("[+] SSH Verbindung erfolgreich!\n")
        
        # 1. Suche nach frps.toml und frps
        print("[*] Suche nach alter FRP Konfiguration...")
        frps_path = run_cmd(ssh, 'find / -name "frps" -type f 2>/dev/null | grep -v "/var/lib/docker" | head -n 1')
        config_path = run_cmd(ssh, 'find / -name "frps.toml" -type f 2>/dev/null | grep -v "/var/lib/docker" | head -n 1')
        
        if not config_path:
            # Fallback for older frp versions that use .ini
            config_path = run_cmd(ssh, 'find / -name "frps.ini" -type f 2>/dev/null | grep -v "/var/lib/docker" | head -n 1')
            
        if not config_path:
            print("[-] Keine frps.toml oder frps.ini gefunden! Prüfe laufende Prozesse...")
            ps_out = run_cmd(ssh, 'ps aux | grep frps | grep -v grep')
            print(f"Laufende Prozesse:\n{ps_out}")
            print("\n[!] Abbruch: Konnte die alte Konfiguration nicht finden.")
            return

        print(f"[+] FRP Config gefunden unter: {config_path}")
        frp_dir = config_path.rsplit('/', 1)[0]
        print(f"[+] FRP Verzeichnis: {frp_dir}")

        # 2. Backup erstellen
        backup_dir = "/root/koda_backups"
        run_cmd(ssh, f'mkdir -p {backup_dir}')
        run_cmd(ssh, f'cp {config_path} {backup_dir}/frps_config.bak')
        print(f"[+] Backup erstellt unter {backup_dir}/frps_config.bak")

        # 3. Neue Konfiguration schreiben (als TOML, auch wenn es vorher INI war)
        new_config_path = f"{frp_dir}/frps.toml"
        
        new_config = f"""bindPort = 7000

# Auth
auth.method = "token"
auth.token = "{FRP_TOKEN}"

# TLS
transport.tls.force = true

# Ports
allowPorts = [
  {{ start = 30000, end = 59999 }}
]

# Limits
transport.maxPoolCount = 5
maxPortsPerClient = 5

# Dashboard
webServer.addr = "127.0.0.1"
webServer.port = 7500
webServer.user = "admin"
webServer.password = "koda_admin_secure_123!"

# Log
log.to = "/var/log/frps/frps.log"
log.level = "warn"
log.maxDays = 7
"""
        
        # Write file via echo
        run_cmd(ssh, f"cat << 'EOF' > {new_config_path}\n{new_config}\nEOF")
        print(f"[+] Neue Konfiguration geschrieben nach {new_config_path}")
        
        # 4. Service neustarten
        print("[*] Starte FRP neu...")
        # Try finding systemd service name
        service_name = run_cmd(ssh, 'systemctl list-units --type=service | grep frp | awk "{print $1}"')
        if service_name:
            run_cmd(ssh, f'systemctl restart {service_name}')
            print(f"[+] Service {service_name} neugestartet.")
        else:
            print("[-] Kein Systemd Service gefunden. Versuche Prozess zu killen und manuell zu starten...")
            run_cmd(ssh, 'pkill -f frps')
            run_cmd(ssh, f'nohup {frps_path} -c {new_config_path} > /dev/null 2>&1 &')
            print("[+] FRP manuell im Hintergrund gestartet.")
            
        # 5. UFW Firewall härten (ohne SSH zu sperren)
        print("[*] Konfiguriere Firewall...")
        run_cmd(ssh, 'apt-get install -y ufw')
        run_cmd(ssh, 'ufw allow 22/tcp')
        run_cmd(ssh, 'ufw allow 7000/tcp')
        run_cmd(ssh, 'ufw allow 30000:39999/tcp')
        run_cmd(ssh, 'ufw allow 40000:49999/udp')
        run_cmd(ssh, 'ufw allow 50000:59999/udp')
        # ufw --force enable -> to bypass y/n prompt
        run_cmd(ssh, 'ufw --force enable')
        print("[+] Firewall aktiv.")

        print("\n[+] ALLES ERFOLGREICH ABGESCHLOSSEN!")

    except Exception as e:
        print(f"[!] Fehler bei der Verbindung: {e}")
    finally:
        ssh.close()

if __name__ == '__main__':
    main()
