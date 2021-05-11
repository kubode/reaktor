package com.github.kubode.reaktor

import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@ExperimentalTime
class ReactorTest : BaseTest() {

    private class TestReactor(
        initialState: State = State(),
        private val transformAction: (Flow<Action>) -> Flow<Action> = { it },
        private val transformMutation: (Flow<Mutation>) -> Flow<Mutation> = { it },
        private val onDestroy: () -> Unit = {},
    ) : BaseReactor<Action, Mutation, State, Event>(initialState) {

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
                            error(e)
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

        override fun transformAction(action: Flow<Action>): Flow<Action> =
            transformAction.invoke(action)

        override fun transformMutation(mutation: Flow<Mutation>): Flow<Mutation> =
            transformMutation.invoke(mutation)

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

        reactor.state.test {
            expectItem() // initialState
            expectItem().text shouldBe "test"
            expectNoEvents()
            cancel()
        }
        reactor.currentState.text shouldBe "test"
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
        val reactor = TestReactor()

        reactor.state.test {
            expectItem().text shouldBe "" // initialState

            val repeats = 100
            repeat(repeats) {
                reactor.send(Action.UpdateText(it.toString()))
            }

            repeat(repeats) {
                expectItem().text shouldBe it.toString()
            }

            expectNoEvents()
            cancel()
        }
    }

    @Test
    fun `test error when expected exception thrown then it emits`() = runTest {
        val reactor = TestReactor()

        reactor.error.test {
            reactor.send(Action.Submit { throw ExpectedException() })
            expectItem().shouldBeInstanceOf<ExpectedException>()
        }
    }

    @Test
    fun `test error when unexpected exception thrown then crashes`() = runTest {
        val reactor = TestReactor()

        reactor.send(Action.Submit { throw UnexpectedException() })
    }
}
