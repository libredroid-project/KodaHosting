with open('app/src/main/java/eu/kodanetwork/mchost/util/TerminalThemeHelper.java', 'r') as f:
    c = f.read()

bg_injection = """
        // Force dark background on the root if it's likely a container
        if (root instanceof ViewGroup) {
            root.setBackgroundColor(Color.parseColor("#111111"));
        }
"""
c = c.replace('applyToChildren(ctx, root);', bg_injection + '\n        applyToChildren(ctx, root);')

with open('app/src/main/java/eu/kodanetwork/mchost/util/TerminalThemeHelper.java', 'w') as f:
    f.write(c)

