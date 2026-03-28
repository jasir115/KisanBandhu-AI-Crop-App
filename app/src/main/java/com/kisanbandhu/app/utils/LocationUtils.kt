package com.kisanbandhu.app.utils

import android.app.Activity
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kisanbandhu.app.R

/**
 * Unified Utility for Prominent Disclosures required by Google Play Policies.
 * Ensuring consistent UI and strict enforcement across all modules.
 */
object LocationUtils {

    /**
     * Disclosure for Location Access (Weather & Mandi prices)
     */
    fun showLocationDisclosure(
        activity: Activity,
        onAgree: () -> Unit,
        onCancel: () -> Unit
    ) {
        if (activity.isFinishing) return
        
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.location_disclosure_title)
            .setMessage(R.string.location_disclosure_msg)
            .setCancelable(false)
            .setPositiveButton(R.string.agree) { dialog, _ ->
                dialog.dismiss()
                onAgree()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                onCancel()
            }
            .show()
    }

    /**
     * Disclosure for Voice Search (Microphone access)
     */
    fun showVoiceDisclosure(
        activity: Activity,
        onAgree: () -> Unit,
        onCancel: () -> Unit
    ) {
        if (activity.isFinishing) return

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.voice_disclosure_title)
            .setMessage(R.string.voice_disclosure_msg)
            .setCancelable(false)
            .setPositiveButton(R.string.agree) { dialog, _ ->
                dialog.dismiss()
                onAgree()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                onCancel()
            }
            .show()
    }

    /**
     * Disclosure for Camera Access (Crop Disease Scanning)
     */
    fun showCameraDisclosure(
        activity: Activity,
        onAgree: () -> Unit,
        onCancel: () -> Unit
    ) {
        if (activity.isFinishing) return

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.camera_disclosure_title)
            .setMessage(R.string.camera_disclosure_msg)
            .setCancelable(false)
            .setPositiveButton(R.string.proceed) { dialog, _ ->
                dialog.dismiss()
                onAgree()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                onCancel()
            }
            .show()
    }
}
