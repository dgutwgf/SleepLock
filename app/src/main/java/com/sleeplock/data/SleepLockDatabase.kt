package com.sleeplock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sleeplock.data.dao.*
import com.sleeplock.data.entity.*

/**
 * SleepLock 主数据库
 */
@Database(
    entities = [
        SleepRecord::class,
        AppWhitelist::class,
        Holiday::class,
        UserSettings::class,
        UnlockCredit::class,
        ViolationLog::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SleepLockDatabase : RoomDatabase() {
    
    abstract fun sleepRecordDao(): SleepRecordDao
    abstract fun appWhitelistDao(): AppWhitelistDao
    abstract fun holidayDao(): HolidayDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun unlockCreditDao(): UnlockCreditDao
    abstract fun violationLogDao(): ViolationLogDao
    
    companion object {
        @Volatile
        private var INSTANCE: SleepLockDatabase? = null
        
        fun getDatabase(context: Context): SleepLockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SleepLockDatabase::class.java,
                    "sleeplock_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
