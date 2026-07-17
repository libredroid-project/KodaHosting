package eu.kodanetwork.mchost.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import eu.kodanetwork.mchost.R;

public class ChineseDictionaryHelper {
    private static final String TAG = "ChineseDictionaryHelper";
    private static HashMap<String, DictEntry> dictionary;
    private static HashMap<String, String> sentencesMap;
    private static boolean isLoading = false;

    private static class DictEntry {
        String pinyin;
        String english;
        DictEntry(String p, String e) { 
            this.pinyin = PinyinConverter.convert(p); 
            this.english = e; 
        }
    }

    public static void init(Context context) {
        if (dictionary != null || isLoading) return;
        isLoading = true;
        new Thread(() -> {
            try {
                InputStream is = context.getAssets().open("chinese_dict.json");
                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                is.close();
                String jsonStr = new String(buffer, "UTF-8");
                JSONObject jsonObject = new JSONObject(jsonStr);
                
                HashMap<String, DictEntry> tempMap = new HashMap<>();
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONObject entry = jsonObject.getJSONObject(key);
                    tempMap.put(key, new DictEntry(entry.getString("p"), entry.getString("e")));
                }
                dictionary = tempMap;

                InputStream is2 = context.getAssets().open("zh_en_sentences.json");
                byte[] buffer2 = new byte[is2.available()];
                is2.read(buffer2);
                is2.close();
                String jsonStr2 = new String(buffer2, "UTF-8");
                JSONObject jsonObject2 = new JSONObject(jsonStr2);
                
                HashMap<String, String> tempSentences = new HashMap<>();
                Iterator<String> sKeys = jsonObject2.keys();
                while (sKeys.hasNext()) {
                    String key = sKeys.next();
                    tempSentences.put(key, jsonObject2.getString(key));
                }
                sentencesMap = tempSentences;

            } catch (Exception e) {
                Log.e(TAG, "Failed to load chinese dictionary", e);
            } finally {
                isLoading = false;
            }
        }).start();
    }

    public static void applyToActivity(Activity activity) {
        if (dictionary == null && !isLoading) {
            init(activity.getApplicationContext());
        }
        View rootView = activity.findViewById(android.R.id.content);
        if (rootView != null) {
            applyRecursive(rootView, activity);
        }
    }

    private static void applyRecursive(View view, Activity activity) {
        if (view instanceof TextView) {
            attachListener((TextView) view, activity);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyRecursive(group.getChildAt(i), activity);
            }
        }
    }

    private static void attachListener(TextView textView, Activity activity) {
        textView.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private float rawX, rawY;
            private boolean isLongPressFired = false;
            private final Handler handler = new Handler(Looper.getMainLooper());
            
            private final Runnable longPressRunnable = new Runnable() {
                @Override
                public void run() {
                    isLongPressFired = true;
                    handleLongPress(textView, startX, startY, rawX, rawY, activity);
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startY = event.getY();
                        rawX = event.getRawX();
                        rawY = event.getRawY();
                        isLongPressFired = false;
                        handler.postDelayed(longPressRunnable, 500);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getX() - startX) > 15 || Math.abs(event.getY() - startY) > 15) {
                            handler.removeCallbacks(longPressRunnable);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPressRunnable);
                        if (isLongPressFired) {
                            return true;
                        }
                        break;
                }
                return false;
            }
        });
    }

    private static void handleLongPress(TextView textView, float x, float y, float rawX, float rawY, Activity activity) {
        Layout layout = textView.getLayout();
        if (layout == null || dictionary == null) return;

        int line = layout.getLineForVertical((int) y - textView.getTotalPaddingTop());
        int offset = layout.getOffsetForHorizontal(line, x - textView.getTotalPaddingLeft());

        CharSequence textSeq = textView.getText();
        if (textSeq != null && offset >= 0 && offset < textSeq.length()) {
            char c = textSeq.charAt(offset);
            if (isChinese(c)) {
                String fullText = textSeq.toString();
                String fullEnglish = sentencesMap != null ? sentencesMap.get(fullText) : null;
                showPopup(activity, fullText, fullEnglish, offset, rawX, rawY);
            }
        }
    }

    private static boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
               ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
               ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A;
    }

    private static void showPopup(Activity activity, String fullText, String fullEnglish, int initialOffset, float initialRawX, float initialRawY) {
        View popupView = LayoutInflater.from(activity).inflate(R.layout.popup_chinese_dict, null);
        
        if (ThemeHelper.isLiquidGlass(activity)) {
            popupView.setBackgroundResource(R.drawable.bg_liquid_glass);
        }

        final PopupWindow popupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true); // Focusable allows outside clicks to dismiss
        
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(20f);
        popupWindow.setAnimationStyle(0);
        
        int popupX = (int) initialRawX - 250;
        int popupY = (int) initialRawY - 500;
        if (popupX < 50) popupX = 50;
        if (popupY < 100) popupY = 100;

        // Tokenize sentence into words via Maximum Matching
        int[] charToWordIdx = new int[fullText.length()];
        List<String> tokens = new ArrayList<>();
        int index = 0;
        while (index < fullText.length()) {
            boolean matched = false;
            for (int len = Math.min(4, fullText.length() - index); len > 0; len--) {
                String chunk = fullText.substring(index, index + len);
                if (dictionary.containsKey(chunk)) {
                    for (int k = 0; k < len; k++) {
                        charToWordIdx[index + k] = tokens.size();
                    }
                    tokens.add(chunk);
                    index += len;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                charToWordIdx[index] = tokens.size();
                tokens.add(String.valueOf(fullText.charAt(index)));
                index++;
            }
        }

        // Setup views
        View layoutSentence = popupView.findViewById(R.id.layout_sentence_context);
        TextView tvSentChinese = popupView.findViewById(R.id.tv_sentence_chinese);
        TextView tvSentPinyin = popupView.findViewById(R.id.tv_sentence_pinyin);
        TextView tvSentEnglish = popupView.findViewById(R.id.tv_sentence_english);
        TextView tvChar = popupView.findViewById(R.id.tv_dict_character);
        TextView tvPinyin = popupView.findViewById(R.id.tv_dict_pinyin);
        TextView tvEnglish = popupView.findViewById(R.id.tv_dict_english);

        class ViewUpdater implements Runnable {
            int currentOffset = initialOffset;

            @Override
            public void run() {
                if (currentOffset < 0 || currentOffset >= fullText.length()) return;
                
                int wordIdx = charToWordIdx[currentOffset];
                String selectedWord = tokens.get(wordIdx);
                DictEntry entry = dictionary.get(selectedWord);

                if (fullText.length() > 1 && fullEnglish != null) {
                    layoutSentence.setVisibility(View.VISIBLE);
                    
                    int start = 0;
                    for(int j=0; j<wordIdx; j++) start += tokens.get(j).length();
                    int end = start + selectedWord.length();

                    SpannableString spanText = new SpannableString(fullText);
                    spanText.setSpan(new ForegroundColorSpan(0xFFFF6B00), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    tvSentChinese.setText(spanText);
                    
                    StringBuilder fullPinyin = new StringBuilder();
                    for (String t : tokens) {
                        if (dictionary.containsKey(t)) {
                            fullPinyin.append(dictionary.get(t).pinyin).append(" ");
                        } else {
                            fullPinyin.append(t);
                        }
                    }
                    tvSentPinyin.setText(fullPinyin.toString().trim());
                    tvSentEnglish.setText(fullEnglish);
                } else {
                    layoutSentence.setVisibility(View.GONE);
                }

                if (entry != null) {
                    tvChar.setText(selectedWord);
                    tvPinyin.setText(entry.pinyin);
                    tvEnglish.setText(entry.english);
                } else {
                    tvChar.setText(selectedWord);
                    tvPinyin.setText("");
                    tvEnglish.setText("No translation found.");
                }
            }

            public void setOffset(int offset) {
                this.currentOffset = offset;
            }
        }
        
        final ViewUpdater updateViews = new ViewUpdater();
        updateViews.run();

        // Custom Drag & Tap Listener
        popupView.setOnTouchListener(new View.OnTouchListener() {
            private float startRawX, startRawY;
            private int initialPopupX, initialPopupY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startRawX = event.getRawX();
                        startRawY = event.getRawY();
                        int[] loc = new int[2];
                        popupView.getLocationOnScreen(loc);
                        initialPopupX = loc[0];
                        initialPopupY = loc[1];
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - startRawX) > 10 || Math.abs(event.getRawY() - startRawY) > 10) {
                            isDragging = true;
                            int newX = (int) (initialPopupX + (event.getRawX() - startRawX));
                            int newY = (int) (initialPopupY + (event.getRawY() - startRawY));
                            popupWindow.update(newX, newY, -1, -1, true);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            // Check if tap was inside tvSentChinese to switch words
                            int[] tvLoc = new int[2];
                            tvSentChinese.getLocationOnScreen(tvLoc);
                            float tvX = event.getRawX() - tvLoc[0];
                            float tvY = event.getRawY() - tvLoc[1];
                            if (tvX >= 0 && tvX <= tvSentChinese.getWidth() && tvY >= 0 && tvY <= tvSentChinese.getHeight()) {
                                Layout l = tvSentChinese.getLayout();
                                if (l != null) {
                                    int line = l.getLineForVertical((int) tvY - tvSentChinese.getTotalPaddingTop());
                                    int off = l.getOffsetForHorizontal(line, tvX - tvSentChinese.getTotalPaddingLeft());
                                    if (off >= 0 && off < fullText.length()) {
                                        updateViews.setOffset(off);
                                        updateViews.run();
                                    }
                                }
                            }
                        }
                        return true;
                }
                return false;
            }
        });

        // Show window first so it can be laid out
        popupWindow.showAtLocation(activity.findViewById(android.R.id.content), Gravity.NO_GRAVITY, popupX, popupY);

        // Animate Enter
        popupView.post(() -> {
            int[] loc = new int[2];
            popupView.getLocationOnScreen(loc);
            popupView.setPivotX(initialRawX - loc[0]);
            popupView.setPivotY(initialRawY - loc[1]);
            popupView.setScaleX(0f);
            popupView.setScaleY(0f);
            popupView.setAlpha(0f);
            popupView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(350)
                    .setInterpolator(new OvershootInterpolator())
                    .start();
        });
    }

    private static void animateExit(View popupView, PopupWindow popupWindow) {
        popupView.animate()
                .scaleX(0.5f)
                .scaleY(0.5f)
                .alpha(0f)
                .setDuration(200)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        popupWindow.dismiss();
                    }
                })
                .start();
    }
}
