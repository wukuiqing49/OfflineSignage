package com.wkq.localsignage.feature.app.storage

import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class CoalescingWriterTest {
    @Test fun queuesOneTaskAndPersistsOnlyLatestSnapshot() {
        val tasks = mutableListOf<Runnable>()
        val writes = mutableListOf<Int>()
        val writer = CoalescingWriter<Int>(Executor { tasks += it }, { writes += it }, { throw it })
        repeat(10_000) { writer.submit(it) }
        assertEquals(1, tasks.size)
        tasks.removeAt(0).run()
        assertEquals(listOf(9_999), writes)
        writer.submit(10_000)
        tasks.removeAt(0).run()
        assertEquals(listOf(9_999, 10_000), writes)
    }

    @Test fun updateDuringWriteIsPersistedWithoutSchedulingAnotherTask() {
        val tasks = mutableListOf<Runnable>()
        val writes = mutableListOf<Int>()
        lateinit var writer: CoalescingWriter<Int>
        writer = CoalescingWriter(Executor { tasks += it }, {
            writes += it
            if (it == 1) { writer.submit(2); writer.submit(3) }
        }, { throw it })
        writer.submit(1)
        tasks.removeAt(0).run()
        assertEquals(listOf(1, 3), writes)
        assertTrue(tasks.isEmpty())
    }

    @Test fun writeFailureDoesNotDisableFutureCheckpoints() {
        val errors = mutableListOf<Exception>()
        val writes = mutableListOf<Int>()
        val writer = CoalescingWriter<Int>(Executor { it.run() }, {
            if (it == 1) throw IllegalStateException("disk full")
            writes += it
        }, { errors += it })
        writer.submit(1)
        writer.submit(2)
        assertEquals(1, errors.size)
        assertEquals(listOf(2), writes)
    }
}
