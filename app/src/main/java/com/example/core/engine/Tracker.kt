package com.example.core.engine

import com.example.core.RunState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** حالة التقدّم المشتركة بين العامل (Worker) والواجهة. */
class Tracker {
    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    private var startedAt = 0L
    private var lastPush = 0L
    private var bytes = 0L
    private var items = 0
    private var skipped = 0
    private val errors = ArrayList<String>()
    private var errorCount = 0

    @Synchronized fun begin(restore: Boolean, phase: String) {
        startedAt = System.currentTimeMillis(); bytes = 0; items = 0; skipped = 0; errors.clear(); errorCount = 0
        _state.value = RunState(running = true, isRestore = restore, phase = phase)
    }

    @Synchronized fun totals(items: Int, bytes: Long) { _state.value = _state.value.copy(totalItems = items, totalBytes = bytes) }
    @Synchronized fun addTotals(items: Int, bytes: Long) {
        _state.value = _state.value.copy(totalItems = _state.value.totalItems + items, totalBytes = _state.value.totalBytes + bytes)
    }

    @Synchronized fun phase(p: String, current: String = "") {
        _state.value = _state.value.copy(phase = p, current = current); lastPush = 0
    }

    @Synchronized fun current(name: String) { _state.value = _state.value.copy(current = name) }

    @Synchronized fun bytes(n: Long) {
        bytes += n
        val now = System.currentTimeMillis()
        if (now - lastPush > 250) push(now)
    }

    @Synchronized fun itemDone() { items++; val now = System.currentTimeMillis(); if (now - lastPush > 250) push(now) }
    @Synchronized fun itemSkipped(size: Long = 0) {
        skipped++; items++
        // الملف المتخطّى يُحسب كأنه أُنجز كي يبقى الشريط دقيقاً
        bytes += size
        val now = System.currentTimeMillis(); if (now - lastPush > 250) push(now)
    }

    @Synchronized fun error(msg: String) {
        errorCount++
        if (errors.size < 50) errors.add(msg)
        push(System.currentTimeMillis())
    }

    val errorTotal: Int @Synchronized get() = errorCount
    val doneItems: Int @Synchronized get() = items
    val doneBytes: Long @Synchronized get() = bytes
    val skippedCount: Int @Synchronized get() = skipped

    private fun push(now: Long) {
        lastPush = now
        val secs = ((now - startedAt) / 1000.0).coerceAtLeast(1.0)
        _state.value = _state.value.copy(
            doneItems = items, doneBytes = bytes, skipped = skipped,
            speedBps = (bytes / secs).toLong(), errors = errors.toList()
        )
    }

    @Synchronized fun finish(summary: String) {
        push(System.currentTimeMillis())
        _state.value = _state.value.copy(running = false, summary = summary, finishedAt = System.currentTimeMillis(), current = "")
    }
}
