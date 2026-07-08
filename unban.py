import sqlite3

try:
    conn = sqlite3.connect('C:\\Users\\Home\\KodaHosting\\KodaNetwork\\supabase\\koda.db')
    cursor = conn.cursor()
    cursor.execute("DELETE FROM banned_hwids;")
    cursor.execute("DELETE FROM high_risk_hwids;")
    conn.commit()
    print("Successfully unbanned all HWIDs.")
except Exception as e:
    print(f"Error: {e}")
finally:
    if conn:
        conn.close()
