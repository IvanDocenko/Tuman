package com.tuman.browser

import android.app.Application
import com.tuman.browser.data.CustomizationDataStore

class TumanApplication : Application() {
    val customization by lazy { CustomizationDataStore(this) }
}
