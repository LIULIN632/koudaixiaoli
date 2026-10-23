package com.pocket.xiaoli.data.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseBackupRestorerTest {

    @Test
    fun newBusinessTablesAreIncludedInBackup() {
        assertTrue(DatabaseBackupRestorer.isUserTable("courses"))
        assertTrue(DatabaseBackupRestorer.isUserTable("campus_map_corrections"))
        assertTrue(DatabaseBackupRestorer.isUserTable("special_work_day"))
        assertTrue(DatabaseBackupRestorer.isUserTable("saved_grades"))
    }

    @Test
    fun roomAndSystemTablesAreExcludedFromBackup() {
        assertFalse(DatabaseBackupRestorer.isUserTable("room_master_table"))
        assertFalse(DatabaseBackupRestorer.isUserTable("android_metadata"))
        assertFalse(DatabaseBackupRestorer.isUserTable("sqlite_sequence"))
    }
}