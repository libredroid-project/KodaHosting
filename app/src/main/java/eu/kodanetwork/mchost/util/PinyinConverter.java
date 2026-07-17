package eu.kodanetwork.mchost.util;

public class PinyinConverter {
    
    // Maps a, e, i, o, u, v/ü to their respective tone marks
    // Using Unicode escape sequences to avoid encoding issues across different platforms/compilers.
    private static final String[][] TONES = {
        // a
        {"a", "\u0101", "\u00e1", "\u01ce", "\u00e0"},
        // e
        {"e", "\u0113", "\u00e9", "\u011b", "\u00e8"},
        // i
        {"i", "\u012b", "\u00ed", "\u01d0", "\u00ec"},
        // o
        {"o", "\u014d", "\u00f3", "\u01d2", "\u00f2"},
        // u
        {"u", "\u016b", "\u00fa", "\u01d4", "\u00f9"},
        // v / ü
        {"v", "\u01d6", "\u01d8", "\u01da", "\u01dc"},
        {"\u00fc", "\u01d6", "\u01d8", "\u01da", "\u01dc"}
    };

    public static String convert(String pinyin) {
        if (pinyin == null || pinyin.isEmpty()) return pinyin;
        
        StringBuilder result = new StringBuilder();
        String[] words = pinyin.split(" ");
        
        for (String word : words) {
            result.append(convertWord(word)).append(" ");
        }
        
        return result.toString().trim();
    }

    private static String convertWord(String word) {
        if (word == null || word.length() < 2) return word.replace("v", "\u00fc");
        
        char lastChar = word.charAt(word.length() - 1);
        if (lastChar < '1' || lastChar > '5') return word.replace("v", "\u00fc"); // No tone mark
        
        int tone = lastChar - '0';
        String base = word.substring(0, word.length() - 1).toLowerCase();
        
        if (tone == 5) return base.replace("v", "\u00fc"); // Neutral tone
        
        // Pinyin tone placement rules
        int targetIdx = -1;
        if (base.contains("a")) targetIdx = base.indexOf('a');
        else if (base.contains("e")) targetIdx = base.indexOf('e');
        else if (base.contains("ou")) targetIdx = base.indexOf('o');
        else {
            // Find the last vowel
            for (int i = base.length() - 1; i >= 0; i--) {
                char c = base.charAt(i);
                if (c == 'i' || c == 'o' || c == 'u' || c == 'v' || c == '\u00fc') {
                    targetIdx = i;
                    break;
                }
            }
        }
        
        if (targetIdx == -1) return base.replace("v", "\u00fc");
        
        char vowel = base.charAt(targetIdx);
        String replacement = getToneVowel(vowel, tone);
        
        String res = base.substring(0, targetIdx) + replacement + base.substring(targetIdx + 1);
        return res.replace("v", "\u00fc");
    }

    private static String getToneVowel(char vowel, int tone) {
        for (String[] t : TONES) {
            if (t[0].charAt(0) == vowel) {
                return t[tone];
            }
        }
        return String.valueOf(vowel);
    }
}
