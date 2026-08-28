with open('app/src/main/res/layout/item_server_terminal.xml', 'r') as f:
    c = f.read()
c = c.replace('android:background="#111111"', 'android:background="@drawable/bg_mc_button_dark"')
with open('app/src/main/res/layout/item_server_terminal.xml', 'w') as f:
    f.write(c)
print("done")
