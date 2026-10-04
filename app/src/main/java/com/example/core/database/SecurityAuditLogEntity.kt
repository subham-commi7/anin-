package com.example.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "security_audit_logs")
data class SecurityAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String,
    val details: String,
    val diagnosticCode: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface SecurityAuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun logEvent(event: SecurityAuditLogEntity)

    @Query("SELECT * FROM security_audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<SecurityAuditLogEntity>>

    @Query("DELETE FROM security_audit_logs")
    suspend fun clearLogs()
}
