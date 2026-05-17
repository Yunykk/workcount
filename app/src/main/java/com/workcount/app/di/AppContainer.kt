package com.workcount.app.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.workcount.app.WorkCountViewModel
import com.workcount.app.location.AndroidLocationResolver
import com.workcount.app.location.LocationResolver
import com.workcount.core.data.LocalBackupRepository
import com.workcount.core.data.PreferencesSettingsRepository
import com.workcount.core.data.RoomPlaceRepository
import com.workcount.core.data.RoomShiftRepository
import com.workcount.core.data.WorkCountDatabase
import com.workcount.core.domain.BackupRepository
import com.workcount.core.domain.PlaceRepository
import com.workcount.core.domain.SettingsRepository
import com.workcount.core.domain.ShiftRepository

class AppContainer(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    val database: WorkCountDatabase = WorkCountDatabase.create(applicationContext)
    val shiftRepository: ShiftRepository = RoomShiftRepository(database)
    val placeRepository: PlaceRepository = RoomPlaceRepository(database)
    val settingsRepository: SettingsRepository = PreferencesSettingsRepository(applicationContext)
    val backupRepository: BackupRepository = LocalBackupRepository(
        shiftRepository = shiftRepository,
        placeRepository = placeRepository,
        settingsRepository = settingsRepository,
    )
    val locationResolver: LocationResolver = AndroidLocationResolver(applicationContext)

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WorkCountViewModel::class.java)) {
                return WorkCountViewModel(
                    shiftRepository = shiftRepository,
                    placeRepository = placeRepository,
                    settingsRepository = settingsRepository,
                    backupRepository = backupRepository,
                    locationResolver = locationResolver,
                ) as T
            }
            error("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
