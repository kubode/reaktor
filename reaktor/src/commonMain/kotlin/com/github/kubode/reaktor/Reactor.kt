package com.github.kubode.reaktor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

interface Reactor<ActionT : Any, StateT : Any, EventT : Any> {
    val currentState: StateT
    val state: Flow<StateT>
    val event: Flow<EventT>
    val error: Flow<Throwable>
    fun send(action: ActionT)
    fun destroy()
}

// Exposed to Native
abstract class AbstractReactor<ActionT : Any, StateT : Any, EventT : Any> internal constructor() :
    Reactor<ActionT, StateT, EventT> {

    // Overrides generics for Native interoperability.
    abstract override val currentState: StateT
    abstract override fun send(action: ActionT)

    /**
     * Intended to use from iOS.
     */
    abstract fun collectInReactorScope(
        onState: ((StateT) -> Unit)? = null,
        onEvent: ((EventT) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
    ): Job
}

// Internal
abstract class BaseReactor<ActionT : Any, MutationT : Any, StateT : Any, EventT : Any>(
    initialState: StateT,
) : AbstractReactor<ActionT, StateT, EventT>() {

    private val _actions: MutableSharedFlow<ActionT> =
        MutableSharedFlow(replay = Int.MAX_VALUE, extraBufferCapacity = Int.MAX_VALUE)

    private val _state: MutableStateFlow<StateT> = MutableStateFlow(initialState)
    final override val state: Flow<StateT> = _state
    final override val currentState: StateT get() = _state.value

    private val _event: MutableSharedFlow<EventT> = MutableSharedFlow()
    final override val event: Flow<EventT> = _event

    private val _error: MutableSharedFlow<Throwable> = MutableSharedFlow()
    final override val error: Flow<Throwable> = _error

    private val job: Job = SupervisorJob()

    private val reactorScope: CoroutineScope =
        CoroutineScope(job + Dispatchers.Main)

    init {
        reactorScope.launch {
            @Suppress("EXPERIMENTAL_API_USAGE") // for flatMapMerge
            transformAction(_actions)
                .flatMapMerge { mutate(it) }
                .let { transformMutation(it) }
                .collect {
                    _state.value = reduce(currentState, it)
                }
        }
    }

    final override fun destroy() {
        job.cancel()
        onDestroy()
    }

    /**
     * Subclasses can override this to do something at [destroy] time.
     */
    protected open fun onDestroy() {}

    protected abstract fun mutate(action: ActionT): Flow<MutationT>
    protected abstract fun reduce(state: StateT, mutation: MutationT): StateT

    final override fun send(action: ActionT) {
        reactorScope.launch {
            _actions.emit(action)
        }
    }

    protected open fun transformAction(action: Flow<ActionT>): Flow<ActionT> {
        return action
    }

    protected open fun transformMutation(mutation: Flow<MutationT>): Flow<MutationT> {
        return mutation
    }

    protected suspend fun publish(event: EventT) {
        _event.emit(event)
    }

    protected suspend fun error(error: Throwable) {
        _error.emit(error)
    }

    final override fun collectInReactorScope(
        onState: ((StateT) -> Unit)?,
        onEvent: ((EventT) -> Unit)?,
        onError: ((Throwable) -> Unit)?
    ): Job {
        return reactorScope.launch {
            if (onState != null) {
                launch { state.collect { onState(it) } }
            }
            if (onEvent != null) {
                launch { event.collect { onEvent(it) } }
            }
            if (onError != null) {
                launch { error.collect { onError(it) } }
            }
        }
    }
}
