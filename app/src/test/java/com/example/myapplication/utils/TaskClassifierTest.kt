package com.example.myapplication.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskClassifierTest {

    @Test
    fun testHighPriorityClassification() {
        val emergencyTask = TaskClassifier.classify("I need to go to the hospital right now")
        assertEquals("High", emergencyTask.priority)
        assertEquals("Health", emergencyTask.category)
        
        val deadlineTask = TaskClassifier.classify("Complete the final project report before the deadline")
        assertEquals("High", deadlineTask.priority)
        assertEquals("Work", deadlineTask.category)
    }

    @Test
    fun testLowPriorityClassification() {
        val somedayTask = TaskClassifier.classify("Maybe someday I will read that book")
        assertEquals("Low", somedayTask.priority)
        assertEquals("Learning", somedayTask.category)
    }

    @Test
    fun testMediumPriorityDefault() {
        val normalTask = TaskClassifier.classify("Go for a quick walk outside")
        assertEquals("Medium", normalTask.priority)
        assertEquals("Health", normalTask.category)
    }

    @Test
    fun testCategoryDetection() {
        val financeTask = TaskClassifier.classify("Pay the monthly rent and electric bill")
        assertEquals("Finance", financeTask.category)

        val socialTask = TaskClassifier.classify("Attend Sarah's wedding anniversary party")
        assertEquals("Social", socialTask.category)

        val errandTask = TaskClassifier.classify("Pick up groceries and drop off the dry cleaning")
        assertEquals("Errand", errandTask.category)
    }
}
