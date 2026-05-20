package com.example.bajeti

import android.app.Application
import com.clerk.api.Clerk

class BajetiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Clerk.initialize(
            this,
            publishableKey = "pk_test_aW1tdW5lLWNoaXBtdW5rLTY3LmNsZXJrLmFjY291bnRzLmRldiQ",
        )
    }
}
