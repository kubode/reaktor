package com.github.kubode.reaktor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

expect fun log(message: String)

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

    private val _actions: MutableSharedFlow<ActionT> = MutableSharedFlow()

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

    private val reactorScope: CoroutineScope =
        CoroutineScope(job + Dispatchers.Main + exceptionHandler)

    private val tag: String = this::class.simpleName.orEmpty()

    init {
        log("$tag init")
        reactorScope.launch {
            _state.subscriptionCount.collect { log("$tag state subscribers: $it") }
        }
        reactorScope.launch {
            @Suppress("EXPERIMENTAL_API_USAGE") // for flatMapMerge
            transformAction(_actions)
                .onEach { log("$tag Action: $it") }
                .flatMapMerge { mutate(it) }
                .let { transformMutation(it) }
                .onEach { log("$tag Mutation: $it") }
                .collect {
                    _state.value = reduce(currentState, it).also { log("$tag Reduced: $it") }
                }
        }
    }

    final override fun destroy() {
        log("$tag destroy")
        job.cancel()
        onDestroy()
    }

    /**
     * Subclasses can override this to do something at [destroy] time.
     */
    open fun onDestroy() {}

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
        log("Publish: $event")
        reactorScope.launch {
            _event.emit(event)
        }
    }

    protected fun error(error: Throwable) {
        log("Error: $error")
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
