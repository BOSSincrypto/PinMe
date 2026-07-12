package com.securecontacts.app

import android.app.Application
import com.securecontacts.app.data.database.AppDatabase

class SecureContactsApp : Application() {
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }
}
