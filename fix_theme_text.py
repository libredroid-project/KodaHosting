with open('app/src/main/java/eu/kodanetwork/mchost/util/TerminalThemeHelper.java', 'r') as f:
    c = f.read()

text_injection = """
            TextView tv = (TextView) view;
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            // Don't override completely if they already have specific colors, but we can't easily check current color.
            // Let's just set it to #DDDDDD (light gray) so it's visible on dark bg.
            tv.setTextColor(Color.parseColor("#DDDDDD"));
"""
c = c.replace('TextView tv = (TextView) view;\n            tv.setTypeface(android.graphics.Typeface.MONOSPACE);', text_injection)

with open('app/src/main/java/eu/kodanetwork/mchost/util/TerminalThemeHelper.java', 'w') as f:
    f.write(c)

