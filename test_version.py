def resolve_java(version_str):
    if not version_str or not version_str.startswith("1."):
        return 21 # Default for unknown
    parts = version_str.split('.')
    try:
        minor = int(parts[1])
        patch = int(parts[2]) if len(parts) > 2 else 0
        
        if minor >= 20 and patch >= 5: return 21
        if minor >= 21: return 21
        if minor >= 18: return 17
        if minor == 17: return 17 # Java 16/17 for 1.17
        if minor <= 16: return 8
    except:
        pass
    return 21

for v in ["1.21.1", "1.20.5", "1.20.4", "1.18.2", "1.17.1", "1.16.5", "1.8.8", "1.19.4"]:
    print(v, resolve_java(v))
