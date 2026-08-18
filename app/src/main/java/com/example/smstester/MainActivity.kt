package com.antteam.smstester

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {

    private lateinit var permissionCoordinator: SystemPermissionCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        permissionCoordinator = SystemPermissionCoordinator(this)

        setContent {
            SmsTesterApp(
                onRequestCriticalPermissions = {
                    permissionCoordinator.requestCriticalPermissions()
                }
            )
        }

        // Не запускать системные окна повторно при каждом возврате в Activity.
        permissionCoordinator.requestRequiredPermissions()
    }
}
