package com.example.playgroundreactor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

interface Reactor<ActionT, StateT, EventT> {
    val currentState: StateT
    val state: Flow<StateT>
    val event: Flow<EventT>
    val error: Flow<Throwable>
    fun send(action: ActionT): Boolean
    fun destroy()
}

class A
class S
class E

fun main() {
    val reactor = object : Reactor<A, S, E> {
        override val currentState: S
            get() = TODO("Not yet implemented")
        override val state: Flow<S>
            get() = TODO("Not yet implemented")
        override val event: Flow<E>
            get() = TODO("Not yet implemented")
        override val error: Flow<Throwable>
            get() = TODO("Not yet implemented")

        override fun send(action: A): Boolean {
            TODO("Not yet implemented")
        }

        override fun destroy() {
            TODO("Not yet implemented")
        }
    }

    GlobalScope.launch {
        reactor.error.collect { error ->
        }
    }
}

class FlowWrapper<T>(private val flow: Flow<T>) : Flow<T> by flow

// Exposed to Native
abstract class BaseReactor<ActionT : Any, StateT : Any, EventT : Any> :
    Reactor<ActionT, StateT, EventT> {
    abstract override val state: FlowWrapper<StateT>
    abstract override val event: FlowWrapper<EventT>
    abstract override val error: FlowWrapper<Throwable>
}

// Internal
abstract class AbstractReactor<ActionT : Any, MutationT : Any, StateT : Any, EventT : Any>(
    initialState: StateT,
) : BaseReactor<ActionT, StateT, EventT>() {

    private val _actions: MutableSharedFlow<ActionT> = MutableSharedFlow()

    private val _state: MutableStateFlow<StateT> = MutableStateFlow(initialState)
    final override val state: FlowWrapper<StateT> = FlowWrapper(_state)
    final override val currentState: StateT get() = _state.value

    private val _event: MutableSharedFlow<EventT> = MutableSharedFlow()
    final override val event: FlowWrapper<EventT> = FlowWrapper(_event)

    private val _error: MutableSharedFlow<Throwable> = MutableSharedFlow()
    final override val error: FlowWrapper<Throwable> = FlowWrapper(_error)

    private val job: Job = SupervisorJob()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        error(throwable)
    }

    internal val reactorScope: CoroutineScope =
        CoroutineScope(job + Dispatchers.Main + exceptionHandler)

    init {
        reactorScope.launch {
            @Suppress("EXPERIMENTAL_API_USAGE") // for flatMapMerge
            transformAction(_actions)
                .flatMapMerge { mutate(it) }
                .let { transformMutation(it) }
                .collect { _state.value = reduce(currentState, it) }
        }
    }

    final override fun destroy() {
        job.cancel()
        onDestroy()
    }

    /**
     * Subclasses can override this to do something at [destroy] time.
     */
    open fun onDestroy() {}

    protected abstract fun mutate(action: ActionT): Flow<MutationT>
    protected abstract fun reduce(currentState: StateT, mutation: MutationT): StateT

    final override fun send(action: ActionT): Boolean {
        return _actions.tryEmit(action)
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
}
