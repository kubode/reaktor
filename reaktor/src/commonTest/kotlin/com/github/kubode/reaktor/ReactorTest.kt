package com.github.kubode.reaktor

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test

class ReactorTest : BaseTest() {

    private class TestReactor(
        initialState: State = State(),
        private val transformAction: (Flow<Action>) -> Flow<Action> = { it },
        private val transformMutation: (Flow<Mutation>) -> Flow<Mutation> = { it },
        private val onDestroy: () -> Unit = {},
    ) : BaseReactor<Action, Mutation, State, Event>(initialState) {

        override fun mutate(action: Action): Flow<Mutation> {
            println("mutate($action)")
            return flow {
                when (action) {
                    is Action.UpdateText -> {
                        emit(Mutation.SetText(action.text))
                    }
                    is Action.Submit -> {
                        emit(Mutation.SetLoading(true))
                        try {
                            publish(Event.Succeeded)
                        } finally {
                            emit(Mutation.SetLoading(false))
                        }
                    }
                }
            }
        }

        override fun reduce(state: State, mutation: Mutation): State {
            println("reduce($state, $mutation)")
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
        object Submit : Action()
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

    private class ExpectedException(message: String) : Exception(message)

    @Test
    fun `test currentState when initialized then it returns initialState`() = runBlocking {
        val reactor = TestReactor(initialState = State(text = "init"))

        reactor.currentState shouldBe State(text = "init")
    }

    @Test
    fun `test currentState when state changed then it returns reduced state`() = runBlocking {
        val reactor = TestReactor()

        reactor.send(Action.UpdateText("test"))

        reactor.currentState.text shouldBe "test"
    }
}
