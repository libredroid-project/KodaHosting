package eu.kodanetwork.mchost.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Html
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import eu.kodanetwork.mchost.R
import eu.kodanetwork.mchost.util.HapticUtil

class PraetorMemoryLimitActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_praetor_warning)

        HapticUtil.forceVibrate(this, 200)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            HapticUtil.forceVibrate(this, 300)
        }, 300)

        // Set P.R.A.E.T.O.R. title formatting
        val tvTitle = findViewById<TextView>(R.id.tv_praetor_title)
        val praetorHtml = "<font color=\"#555555\">P.R.</font><font color=\"#AAAAAA\">A</font><font color=\"#555555\">.</font><font color=\"#AAAAAA\">E</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">T</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">O</font><font color=\"#555555\">.</font><font color=\"#FFFFFF\">R.</font>"
        tvTitle.text = Html.fromHtml(praetorHtml, Html.FROM_HTML_MODE_LEGACY)

        // Blinking warning icon animation
        val warningIcon = findViewById<View>(R.id.tv_warning_icon)
        warningIcon?.startAnimation(AlphaAnimation(1.0f, 0.0f).apply {
            duration = 300
            repeatMode = Animation.REVERSE
            repeatCount = 5
        })

        // Cheeky / insulting explanation text in DE / EN
        val tvReason = findViewById<TextView>(R.id.tv_praetor_reason)
        
        val systemLocale = resources.configuration.locales[0]
        val isGerman = systemLocale.language.equals("de", ignoreCase = true)
        
        val msg = if (isGerman) {
            "SYSTEM CRASH DETECTED!\n\n" +
            "Dein wunderbares neues Android 17 Betriebssystem meinte wohl, es sei klüger als du, und hat unsere Server-Prozesse willkürlich terminiert.\n\n" +
            "Android 17 ist zu 100% schuld daran, dass dein Server abgestürzt ist. Google hasst es offenbar einfach, wenn Nutzer die Rechenleistung ihres eigenen Handys tatsächlich beanspruchen wollen.\n\n" +
            "Um dieses nutzlose RAM-Limit dauerhaft aufzuheben, führe den untenstehenden ADB-Befehl aus."
        } else {
            "SYSTEM CRASH DETECTED!\n\n" +
            "Your wonderful new Android 17 operating system thought it was smarter than you and terminated our server processes.\n\n" +
            "Android 17 is 100% to blame for this crash. Google apparently hates it when users actually try to use the computing power of their own device.\n\n" +
            "To permanently bypass this useless RAM limit, run the ADB command below."
        }
        tvReason.text = msg

        // Action button copies the bypass commands to clipboard
        val btnAction = findViewById<Button>(R.id.btn_praetor_action)
        btnAction.text = if (isGerman) "ADB-BEFEHL KOPIEREN" else "COPY ADB COMMAND"
        btnAction.setOnClickListener {
            HapticUtil.forceVibrate(this, 80)
            val adbCmd = "adb shell cmd device_config set_sync_disabled_for_tests persistent && adb shell am memory-limiter ignore none && adb shell device_config put activity_manager max_phantom_processes 2147483647"
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("KodaHosting ADB Bypass", adbCmd)
            clipboard.setPrimaryClip(clip)
            
            val toastMsg = if (isGerman) "Befehl kopiert! Führe ihn per ADB aus." else "Command copied! Run it via ADB."
            Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show()
        }
    }
}
