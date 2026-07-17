package eu.kodanetwork.mchost.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import eu.kodanetwork.mchost.R

class TiltEffectHelper(
    context: Context, 
    private val viewToTilt: View,
    private val enableGlare: Boolean = false
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var glareView: ImageView? = null

    init {
        if (enableGlare && viewToTilt is ViewGroup) {
            glareView = ImageView(context)
            glareView?.setBackgroundResource(R.drawable.bg_glass_glare_layer)
            glareView?.alpha = 0f
            
            // To ensure the layer can move without showing harsh edges, we make it larger
            val params = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            glareView?.scaleX = 2f
            glareView?.scaleY = 2f

            viewToTilt.addView(glareView, params)
        }
    }

    fun register() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun unregister() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        val orientationVals = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientationVals)

        // orientationVals[1] is pitch (tilt forward/backward)
        // orientationVals[2] is roll (tilt left/right)
        val pitch = Math.toDegrees(orientationVals[1].toDouble()).toFloat()
        val roll = Math.toDegrees(orientationVals[2].toDouble()).toFloat()

        // Compute a parallax Y-offset based on the View's position on screen
        val location = IntArray(2)
        viewToTilt.getLocationOnScreen(location)
        val screenHeight = android.content.res.Resources.getSystem().displayMetrics.heightPixels
        val viewY = location[1]
        // yOffset goes from ~ -0.5 (top of screen) to +0.5 (bottom of screen)
        val yOffset = (viewY - screenHeight / 2f) / screenHeight.toFloat()
        
        // Offset the pitch by the Y-position so cards tilt organically
        val parallaxPitch = pitch + (yOffset * 15f)

        // Clamp the values to avoid extreme flips. Cards only turn slightly to prevent clipping.
        val maxRotation = 6f
        val clampedPitch = parallaxPitch.coerceIn(-maxRotation, maxRotation)
        val clampedRoll = roll.coerceIn(-maxRotation, maxRotation)

        // Apply rotation directly for smooth 60fps tracking (subtle)
        viewToTilt.rotationX = -clampedPitch * 0.3f
        viewToTilt.rotationY = clampedRoll * 0.3f

        glareView?.let {
            if (it.alpha == 0f) {
                it.animate().alpha(1f).setDuration(500).start()
            }
            // Move the glare layer opposite to tilt, AND offset it based on screen position
            it.translationX = roll * 25f
            it.translationY = (parallaxPitch * 25f) + (yOffset * 150f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
