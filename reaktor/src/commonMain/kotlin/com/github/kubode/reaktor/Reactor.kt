package com.github.kubode.reaktor

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

interface Reactor<ActionT : Any, StateT : Any, EventT : Any> {
    /**
     * The current state.
     *
     * This value is changed after a new state emitted.
     *
     * To avoid race conditions, make sure to capture the value when using this value consecutively.
     */
    val currentState: StateT

    /**
     * The state flow.
     *
     * This flow never completes and never fails.
     *
     * A collector of this flow collects [currentState] when start collecting,
     * and also collects the new state just after state changed.
     */
    val state: Flow<StateT>

    /**
     * The event flow.
     *
     * This flow never completes and never fails.
     *
     * A collector of this flow collects the events published by `publish()`.
     * Events are cached to this flow until this flow has a collector.
     * When a first collector starts collecting from this flow,
     * all of the cached events are emitted to the collector.
     *
     * Events are one-time, so a collector cannot collect an event that has been collected again.
     */
    val event: Flow<EventT>

    /**
     * The error flow.
     *
     * This flow never completes and never fails.
     *
     * A collector of this flow collects the errors published by `error()`.
     * Errors are cached to this flow until this flow has a collector.
     * When a first collector starts collecting from this flow,
     * all of the cached errors are emitted to the collector.
     *
     * Errors are one-time, so a collector cannot collect an error that has been collected again.
     */
    val error: Flow<Throwable>

    /**
     * Sends an action.
     */
    fun send(action: ActionT)

    /**
     * Destroys this Reactor.
     *
     * Call this when you want to destroy this Reactor.
     * After this is called, all coroutines in this Reactor are canceled,
     * and the state will not be updated.
     */
    fun destroy()
}

// Exposed to Native
abstract class AbstractReactor<ActionT : Any, StateT : Any, EventT : Any> internal constructor() :
    Reactor<ActionT, StateT, EventT> {

    // Overriding generics for the Native interoperability.
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
    context: CoroutineContext = DEFAULT_CONTEXT,
) : AbstractReactor<ActionT, StateT, EventT>() {

    companion object {
        val DEFAULT_CONTEXT: CoroutineContext
            get() = Job() + Dispatchers.Main
    }

    private val reactorScope: CoroutineScope =
        CoroutineScope(context)

    private val _actions: MutableSharedFlow<ActionT> =
        MutableSharedFlow(replay = Int.MAX_VALUE, extraBufferCapacity = Int.MAX_VALUE)

    private val _state: MutableStateFlow<StateT> = MutableStateFlow(initialState)
    final override val state: Flow<StateT> = _state
    final override val currentState: StateT get() = _state.value

    private val _event: Channel<EventT> = Channel(Channel.UNLIMITED)
    final override val event: Flow<EventT> =
        _event.receiveAsFlow().shareIn(reactorScope, SharingStarted.WhileSubscribed())

    private val _error: Channel<Throwable> = Channel(Channel.UNLIMITED)
    final override val error: Flow<Throwable> =
        _error.receiveAsFlow().shareIn(reactorScope, SharingStarted.WhileSubscribed())

    init {
        reactorScope.launch {
            @Suppress("EXPERIMENTAL_API_USAGE") // for flatMapMerge
            _actions
                .transformAction()
                .flatMapMerge { action -> mutate(action).catch { error(it) } }
                .transformMutation()
                .map { reduce(currentState, it) }
                .transformState()
                .collect { _state.value = it }
        }
    }

    final override fun destroy() {
        reactorScope.cancel()
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

    protected open fun Flow<ActionT>.transformAction(): Flow<ActionT> {
        return this
    }

    protected open fun Flow<MutationT>.transformMutation(): Flow<MutationT> {
        return this
    }

    protected open fun Flow<StateT>.transformState(): Flow<StateT> {
        return this
    }

    protected suspend fun publish(event: EventT) {
        _event.send(event)
    }

    protected suspend fun error(error: Throwable) {
        _error.send(error)
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
