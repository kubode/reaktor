package com.github.kubode.reaktor

import app.cash.turbine.test
import io.kotest.assertions.timing.eventually
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@ExperimentalTime
class ReactorTest : BaseTest() {

    private class TestReactor(
        initialState: State = State(),
        context: CoroutineContext = DEFAULT_CONTEXT,
        private val transformAction: (Flow<Action>) -> Flow<Action> = { it },
        private val transformMutation: (Flow<Mutation>) -> Flow<Mutation> = { it },
        private val transformState: (Flow<State>) -> Flow<State> = { it },
        private val onDestroy: () -> Unit = {},
    ) : BaseReactor<Action, Mutation, State, Event>(initialState, context) {

        private val timeMark: TimeMark = TimeSource.Monotonic.markNow()
        private fun log(message: String) {
            println("[${timeMark.elapsedNow().toIsoString()}] $message")
        }

        override fun mutate(action: Action): Flow<Mutation> {
            log("mutate($action)")
            return flow {
                when (action) {
                    is Action.UpdateText -> {
                        emit(Mutation.SetText(action.text))
                    }
                    is Action.Submit -> {
                        emit(Mutation.SetLoading(true))
                        try {
                            action.run()
                            publish(Event.Succeeded)
                        } catch (e: ExpectedException) {
                            error(Error.Expected(e))
                        } finally {
                            emit(Mutation.SetLoading(false))
                        }
                    }
                }
            }
        }

        override fun reduce(state: State, mutation: Mutation): State {
            log("reduce($state, $mutation)")
            return when (mutation) {
                is Mutation.SetText -> state.copy(
                    text = mutation.text
                )
                is Mutation.SetLoading -> state.copy(
                    isLoading = mutation.isLoading
                )
            }
        }

        override fun Flow<Action>.transformAction(): Flow<Action> = transformAction(this)
        override fun Flow<Mutation>.transformMutation(): Flow<Mutation> = transformMutation(this)
        override fun Flow<State>.transformState(): Flow<State> = transformState(this)
        override fun onDestroy() = onDestroy.invoke()
    }

    private sealed class Action {
        data class UpdateText(val text: String) : Action()
        data class Submit(val run: suspend () -> Unit) : Action()
    }

    private sealed class Mutation {
        data class SetText(val text: String) : Mutation()
        data class SetLoading(val isLoading: Boolean) : Mutation()
    }

    private data class State(
        val text: String = "",
        val isLoading: Boolean = false,
    )

    private sealed class Event {
        object Succeeded : Event()
    }

    private sealed class Error(cause: Throwable) : Exception(cause) {
        class Expected(cause: Throwable) : Error(cause)
    }

    private class ExpectedException : Exception()
    private class UnexpectedException : Exception()

    @Test
    fun `test currentState when initialized then it returns initialState`() = runTest {
        val reactor = TestReactor(initialState = State(text = "init"))

        reactor.currentState shouldBe State(text = "init")
    }

    @Test
    fun `test currentState when state changed then it returns new state`() = runTest {
        val reactor = TestReactor()
        reactor.send(Action.UpdateText("test"))

        eventually { reactor.currentState.text shouldBe "test" }
    }

    @Test
    fun `test state when initialized then it emits only initialState`() = runTest {
        val reactor = TestReactor(initialState = State(text = "init"))

        reactor.state.test {
            expectItem() shouldBe State(text = "init")
            expectNoEvents()
            cancel()
        }
    }

    @Test
    fun `test send when sends many actions then all actions are consumed`() = runTest {
        val mutex = Mutex()
        val receivedActions = mutableListOf<Action>()
        val reactor = TestReactor(
            transformAction = { action ->
                action.onEach { mutex.withLock { receivedActions += it } }
            }
        )

        val repeats = 100
        repeat(repeats) {
            reactor.send(Action.UpdateText(it.toString()))
        }
        eventually { reactor.currentState.text shouldBe "99" }
        receivedActions shouldBe (0 until repeats).map { Action.UpdateText(it.toString()) }
    }

    @Test
    fun `test error when expected exception thrown then it emits wrapped`() = runTest {
        val reactor = TestReactor()

        reactor.error.test {
            reactor.send(Action.Submit { throw ExpectedException() })
            expectItem().shouldBeInstanceOf<Error.Expected>()
        }
    }

    @Test
    fun `test error when unexpected exception thrown from mutate then it emits as is`() = runTest {
        val reactor = TestReactor()

        reactor.error.test {
            reactor.send(Action.Submit { throw UnexpectedException() })
            expectItem().shouldBeInstanceOf<UnexpectedException>()
        }
    }

    @Test
    fun `test error when exception thrown from transformMutation then crash`() = runTest {
        var thrown: Throwable? = null
        val handler = CoroutineExceptionHandler { _, throwable -> thrown = throwable }
        TestReactor(
            context = handler,
            transformMutation = { flow { throw UnexpectedException() } }
        )

        eventually { thrown.shouldBeInstanceOf<UnexpectedException>() }
    }

    @Test
    fun `test error given many error when collect then all exceptions are emitted`() = runTest {
        val reactor = TestReactor()
        val repeats = 10
        repeat(repeats) { reactor.send(Action.Submit { throw UnexpectedException() }) }
        // await state change
        reactor.send(Action.UpdateText("new"))
        eventually { reactor.currentState.text shouldBe "new" }

        reactor.error.test {
            repeat(repeats) { expectItem().shouldBeInstanceOf<UnexpectedException>() }
            expectNoEvents()
            cancel()
        }
    }

    @Test
    fun `test error when collect again then all exceptions are emitted`() = runTest {
        val reactor = TestReactor()
        reactor.send(Action.Submit { throw UnexpectedException() })
        reactor.error.test {
            expectItem().shouldBeInstanceOf<UnexpectedException>()
            expectNoEvents()
            cancel()
        }

        reactor.send(Action.Submit { throw UnexpectedException() })
        reactor.error.test {
            expectItem().shouldBeInstanceOf<UnexpectedException>()
            expectNoEvents()
            cancel()
        }
    }

    @Test
    fun `test error when collect double then broadcast it to each collector`() = runTest {
        val reactor = TestReactor()
        val e1 = async { reactor.error.first() }
        val e2 = async { reactor.error.first() }

        reactor.send(Action.Submit { throw UnexpectedException() })

        e1.await() shouldBeSameInstanceAs e2.await()
    }
}
