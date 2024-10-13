package com.krowdkinect_android_library

import android.content.Context
import android.content.Intent

class KrowdKinect {

    // Rename the data class to KKOptions (both K's capitalized)
    data class KKOptions(
        val apiKey: String, // required
        val deviceID: UInt = 1u,
        val displayName: String? = null,
        val displayTagline: String? = null,
        val homeAwayHide: Boolean = true,
        val seatNumberEditHide: Boolean = true,
        val homeAwaySelection: String = "All"
    )

    companion object {
        // Entry point for launching the KrowdKinect SDK with the required and optional parameters
        fun launch(context: Context, KKOptions: KKOptions) {
            if (KKOptions.apiKey.isEmpty()) {
                throw IllegalArgumentException("API Key is required to launch KrowdKinect SDK")
            }

            val intent = Intent(context, KrowdKinectActivity::class.java)

            intent.putExtra("apiKey", KKOptions.apiKey)
            intent.putExtra("deviceID", KKOptions.deviceID.toInt())  // UInt -> Int conversion
            intent.putExtra("displayName", KKOptions.displayName)
            intent.putExtra("displayTagline", KKOptions.displayTagline)
            intent.putExtra("homeAwayHide", KKOptions.homeAwayHide)
            intent.putExtra("seatNumberEditHide", KKOptions.seatNumberEditHide)
            intent.putExtra("homeAwaySelection", KKOptions.homeAwaySelection)

            // Launch KrowdKinectActivity in full-screen mode
            context.startActivity(intent)
        }
    }
}
