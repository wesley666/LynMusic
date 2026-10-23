package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Only speculative work belongs here. Committed writes keep using the Store's scope. */
internal class LyricsShareTasks(val session: PlayerTaskSession, parent: CoroutineScope) {
    private val job = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + job)
    val initialization = LyricsShareTaskSlot(scope)
    val fonts = LyricsShareTaskSlot(scope)
    val preview = LyricsShareTaskSlot(scope)
    fun cancel() = job.cancel()
}

/** A replacement cancels its predecessor without waiting for a non-cooperative platform call. */
internal class LyricsShareTaskSlot(private val scope: CoroutineScope) {
    private data class Slot(val id: Long, val job: Job?)
    private val slot = MutableStateFlow(Slot(0, null))

    fun launch(id: Long, block: suspend () -> Unit) {
        val job = scope.launch(start = CoroutineStart.LAZY) { block() }
        val next = Slot(id, job)
        val previous = slot.getAndUpdate { if (it.id < id) next else it }
        if (previous.id < id) previous.job?.cancel()
        if (slot.value != next) {
            job.cancel()
            return
        }
        job.invokeOnCompletion { slot.compareAndSet(next, Slot(id, null)) }
        job.start()
    }

    fun cancel(id: Long) {
        val previous = slot.getAndUpdate { if (it.id < id) Slot(id, null) else it }
        if (previous.id < id) previous.job?.cancel()
    }
}
