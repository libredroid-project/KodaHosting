
with open("app/src/main/java/eu/kodanetwork/mchost/util/PlayerStatsParser.java", "r", encoding="utf-8") as f:
    code = f.read()

fallback_code = """
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Fallback to offline UUID
        return java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8)).toString();
    }
"""

code = code.replace("""        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }""", fallback_code)

with open("app/src/main/java/eu/kodanetwork/mchost/util/PlayerStatsParser.java", "w", encoding="utf-8") as f:
    f.write(code)
print("Fix UUID done")

