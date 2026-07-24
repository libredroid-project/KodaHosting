import re

file_path = "app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java"

with open(file_path, "r", encoding="utf-8") as f:
    code = f.read()

# Add head fetching to showPlayerActions()
head_fetch = """            android.widget.ImageView ivHead = dummyPlayer.findViewById(R.id.iv_row_player_head);
            new Thread(() -> {
                try {
                    java.net.URL url = new java.net.URL("https://mc-heads.net/avatar/ExamplePlayer/64");
                    java.io.InputStream in = url.openStream();
                    final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in);
                    runOnUiThread(() -> {
                        if (ivHead != null && bmp != null) ivHead.setImageBitmap(bmp);
                    });
                } catch (Exception e) {}
            }).start();
            
            dummyPlayer.setOnClickListener(v -> {"""

code = code.replace("dummyPlayer.setOnClickListener(v -> {", head_fetch)

# Fix starvation (0 hunger) in populateInventoryUI if it exists
starvation_fix = """        if (ivFood != null && tvFood != null) {
            ivFood.setImageResource(R.drawable.ic_pm_hunger_empty);
            tvFood.setText("0 / 20");
        }"""
        
old_starvation = """        if (ivFood != null && tvFood != null) {
            ivFood.setImageResource(R.drawable.ic_pm_hunger_empty);
            tvFood.setText("20 / 20");
        }"""

code = code.replace(old_starvation, starvation_fix)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(code)

print("Added head fetch and starvation fixes.")
