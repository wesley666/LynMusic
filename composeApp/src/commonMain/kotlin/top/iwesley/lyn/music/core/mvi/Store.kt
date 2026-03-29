package top.iwesley.lyn.music.core.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface Store<S, I, E> {
    val state: StateFlow<S>
    val effects: SharedFlow<E>
    fun dispatch(intent: I)
}

abstract class BaseStore<S, I, E>(
    initialState: S,
    private val scope: CoroutineScope,
) : Store<S, I, E> {
    private val mutableState = MutableStateFlow(initialState)
    private val mutableEffects = MutableSharedFlow<E>(extraBufferCapacity = 16)

    final override val state: StateFlow<S> = mutableState.asStateFlow()
    final override val effects: SharedFlow<E> = mutableEffects.asSharedFlow()

    final override fun dispatch(intent: I) {
        scope.launch {
            handleIntent(intent)
        }
    }

    protected fun updateState(transform: (S) -> S) {
        mutableState.update(transform)
    }

    protected suspend fun emitEffect(effect: E) {
        mutableEffects.emit(effect)
    }

    protected abstract suspend fun handleIntent(intent: I)
}
