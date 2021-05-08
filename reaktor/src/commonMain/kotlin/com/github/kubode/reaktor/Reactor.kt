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
abstract class AbstractReactor<ActionT : Any, StateT : Any, EventT : Any> :
    Reactor<ActionT, StateT, EventT> {

    // Overrides generics for Native interoperability.
    abstract override val currentState: StateT
    abstract override fun send(action: ActionT)

    abstract fun collectInReactorScope(
        onState: (StateT) -> Unit,
        onEvent: (EventT) -> Unit,
        onError: (Throwable) -> Unit,
    ): Job
}

// Internal
abstract class BaseReactor<ActionT : Any, MutationT : Any, StateT : Any, EventT : Any>(
    initialState: StateT,
) : AbstractReactor<ActionT, StateT, EventT>() {

    private val _actions: MutableSharedFlow<ActionT> = MutableSharedFlow(replay = 1)

    private val _state: MutableStateFlow<StateT> = MutableStateFlow(initialState)
    final override val state: Flow<StateT> = _state
    final override val currentState: StateT get() = _state.value

    private val _event: MutableSharedFlow<EventT> = MutableSharedFlow()
    final override val event: Flow<EventT> = _event

    private val _error: MutableSharedFlow<Throwable> = MutableSharedFlow()
    final override val error: Flow<Throwable> = _error

    private val job: Job = SupervisorJob()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        error(throwable)
    }

    protected val reactorScope: CoroutineScope =
        CoroutineScope(job + Dispatchers.Main + exceptionHandler)

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

    protected fun publish(event: EventT) {
        reactorScope.launch {
            _event.emit(event)
        }
    }

    protected fun error(error: Throwable) {
        reactorScope.launch {
            _error.emit(error)
        }
    }

    final override fun collectInReactorScope(
        onState: (StateT) -> Unit,
        onEvent: (EventT) -> Unit,
        onError: (Throwable) -> Unit,
    ): Job {
        return reactorScope.launch {
            launch { state.collect { onState(it) } }
            launch { event.collect { onEvent(it) } }
            launch { error.collect { onError(it) } }
        }
    }
}
