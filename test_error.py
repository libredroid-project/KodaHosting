import urllib.request
import json
import urllib.error

url = 'https://scsezpfrrmpyuapblbxk.supabase.co/rest/v1/rpc/report_tamper'
headers = {
    'apikey': 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNjc2V6cGZycm1weXVhcGJsYnhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY5NDE4MjYsImV4cCI6MjA5MjUxNzgyNn0.rHro6kQpXHAnxEaFxozYzsKY8IHIUlot-7-Q4LNbZT8',
    'Authorization': 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNjc2V6cGZycm1weXVhcGJsYnhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY5NDE4MjYsImV4cCI6MjA5MjUxNzgyNn0.rHro6kQpXHAnxEaFxozYzsKY8IHIUlot-7-Q4LNbZT8',
    'Content-Type': 'application/json'
}
data = json.dumps({'p_hwid': 'test_hwid', 'p_reason': 'test_reason'}).encode('utf-8')
req = urllib.request.Request(url, data=data, headers=headers, method='POST')
try:
    res = urllib.request.urlopen(req)
    print(res.read().decode('utf-8'))
except urllib.error.HTTPError as e:
    print(e.code)
    print(e.read().decode('utf-8'))
