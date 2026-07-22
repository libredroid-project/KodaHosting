
with open("app/src/main/java/eu/kodanetwork/mchost/util/PlayerStatsParser.java", "r", encoding="utf-8") as f:
    code = f.read()

code = code.replace("for (String key : mined.keySet()) {", "java.util.Iterator<String> keys = mined.keys();\n                while (keys.hasNext()) {\n                    String key = keys.next();")

with open("app/src/main/java/eu/kodanetwork/mchost/util/PlayerStatsParser.java", "w", encoding="utf-8") as f:
    f.write(code)
print("Fix stats done")

