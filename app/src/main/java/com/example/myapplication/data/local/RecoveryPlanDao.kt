package com.example.myapplication.data.local

import androidx.room.*
import com.example.myapplication.data.model.RecoveryPlan
import kotlinx.coroutines.flow.Flow

@Dao
interface RecoveryPlanDao {
    @Query("SELECT * FROM recovery_plans WHERE isActive = 1 LIMIT 1")
    fun getActivePlan(): Flow<RecoveryPlan?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: RecoveryPlan): Long

    @Query("UPDATE recovery_plans SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAllPlans()
    
    @Transaction
    suspend fun setActivePlan(plan: RecoveryPlan): Long {
        deactivateAllPlans()
        return insertPlan(plan)
    }

    @Query("SELECT * FROM recovery_plans WHERE id = :planId LIMIT 1")
    suspend fun getPlanById(planId: Long): RecoveryPlan?
}
