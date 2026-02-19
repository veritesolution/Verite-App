package com.example.myapplication.data.repository

import com.example.myapplication.data.local.RecoveryPlanDao
import com.example.myapplication.data.model.RecoveryPlan
import kotlinx.coroutines.flow.Flow

class RecoveryRepository(private val recoveryPlanDao: RecoveryPlanDao) {
    val activePlan: Flow<RecoveryPlan?> = recoveryPlanDao.getActivePlan()

    suspend fun saveActivePlan(plan: RecoveryPlan): Long {
        return recoveryPlanDao.setActivePlan(plan)
    }
    
    suspend fun deactivateActivePlan() {
        recoveryPlanDao.deactivateAllPlans()
    }
}
