
with open("app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java", "r", encoding="utf-8") as f:
    code = f.read()

import re

new_create_row = """
    private android.view.View createPlayerRow(String p, boolean isOnline, int pad, com.google.android.material.bottomsheet.BottomSheetDialog parentSheet) {
        android.view.View view = getLayoutInflater().inflate(R.layout.item_player_row, null);
        android.widget.TextView tvName = view.findViewById(R.id.tv_row_player_name);
        tvName.setText(p);
        
        view.setOnClickListener(v -> {
            showPlayerActionSheet(p, isOnline);
            parentSheet.dismiss();
        });
        return view;
    }
"""

code = re.sub(r"private android\.view\.View createPlayerRow\(.*?(?=\n    private void|\n    public)", new_create_row, code, flags=re.DOTALL)

with open("app/src/main/java/eu/kodanetwork/mchost/ui/ServerDetailActivity.java", "w", encoding="utf-8") as f:
    f.write(code)

print("Row fixed")

