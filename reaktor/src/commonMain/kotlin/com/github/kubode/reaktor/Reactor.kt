package com.github.kubode.reaktor

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

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
     * A collector of this flow collects the events published.
     * Events are cached to this flow until this flow has a collector.
     * When a first collector starts collecting from this flow,
     * all of the cached events are emitted to the collector.
     *
     * Events are one-time, so a collector cannot collect an event that has been collected once.
     */
    val event: Flow<EventT>

    /**
     * The error flow.
     *
     * This flow never completes and never fails.
     *
     * A collector of this flow collects the errors published.
     * Errors are cached to this flow until this flow has a collector.
     * When a first collector starts collecting from this flow,
     * all of the cached errors are emitted to the collector.
     *
     * Errors are one-time, so a collector cannot collect an error that has been collected once.
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

/**
 * An abstract class to make it easier to use generics on iOS.
 * Do not use this class except on iOS.
 */
abstract class NativeReactor<ActionT : Any, StateT : Any, EventT : Any> internal constructor() :
    Reactor<ActionT, StateT, EventT> {

    // Overriding generics for the Native interoperability.
    abstract override val currentState: StateT
    abstract override fun send(action: ActionT)

    /**
     * Intended to use from iOS.
     * Do not use from Android.
     * Please use [Flow.collect] for each flows instead of using this on Android.
     */
    abstract fun collectInReactorScope(
        onState: ((StateT) -> Unit)? = null,
        onEvent: ((EventT) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
    ): Job
}

/**
 * An abstract class of the framework implementation of Reactor.
 *
 * Inherit this class if you want to create your own Reactor.
 */
@OptIn(FlowPreview::class)
abstract class BaseReactor<ActionT : Any, MutationT : Any, StateT : Any, EventT : Any>(
    initialState: StateT,
    context: CoroutineContext = DEFAULT_CONTEXT,
) : NativeReactor<ActionT, StateT, EventT>() {

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

    private val _event: Channel<EventT> = Channel(UNLIMITED)
    final override val event: Flow<EventT> =
        _event.receiveAsFlow().shareIn(reactorScope, SharingStarted.WhileSubscribed())

    private val _error: Channel<Throwable> = Channel(UNLIMITED)
    final override val error: Flow<Throwable> =
        _error.receiveAsFlow().shareIn(reactorScope, SharingStarted.WhileSubscribed())

    init {
        reactorScope.launch {
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

    /**
     * This converts an action to mutation flow.
     *
     * Within this function, you can do things that have side effects, such as calling the API.
     * If the returned flow throws an exception, it will be automatically emitted to [error].
     * If you want to handle your own exceptions, use try-catch to throw your own exceptions to error.
     */
    protected abstract fun mutate(action: ActionT): Flow<MutationT>

    /**
     * Returns a new state based on the current [state] and [mutation].
     */
    protected abstract fun reduce(state: StateT, mutation: MutationT): StateT

    final override fun send(action: ActionT) {
        reactorScope.launch {
            _actions.emit(action)
        }
    }

    /**
     * Transforms the action flow.
     *
     * Subclasses can override this and transform the action flow.
     *
     * Note: The transformed flow should not throw an exception. Otherwise, the application will crash.
     */
    protected open fun Flow<ActionT>.transformAction(): Flow<ActionT> = this

    /**
     * Transforms the mutation flow.
     *
     * Subclasses can override this and transform the mutation flow.
     * It is mainly used to convert the external flows into mutations.
     *
     * e.g.
     * ```
     * overridde fun Flow<Mutation>.transformMutation() = merge(
     *     this,
     *     externalFlow.map { Mutation.SetExternalData(it) }
     * )
     * ```
     *
     * Note: The transformed flow should not throw an exception. Otherwise, the application will crash.
     */
    protected open fun Flow<MutationT>.transformMutation(): Flow<MutationT> = this

    /**
     * Transforms the state flow.
     *
     * Subclasses can override this and transform the state flow.
     * This is useful for logging state changes.
     *
     * e.g.
     * ```
     * override fun Flow<State>.transformState() = this.onEach { log("$it") }
     * ```
     *
     * Note: The transformed flow should not throw an exception. Otherwise, the application will crash.
     */
    protected open fun Flow<StateT>.transformState(): Flow<StateT> = this

    /**
     * Emits an event to [event] flow.
     */
    protected suspend fun publish(event: EventT) {
        _event.send(event)
    }

    /**
     * Emits an error to [error] flow.
     */
    protected suspend fun error(error: Throwable) {
        _error.send(error)
    }

    final override fun collectInReactorScope(
        onState: ((StateT) -> Unit)?,
        onEvent: ((EventT) -> Unit)?,
        onError: ((Throwable) -> Unit)?,
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
