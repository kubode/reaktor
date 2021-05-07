package com.example.playgroundreactor

import com.example.playgroundreactor.MyReactor.*
import com.github.kubode.reaktor.BaseReactor
import com.github.kubode.reaktor.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

class MyReactor : BaseReactor<Action, Mutation, State, Event>(State()) {
    sealed class Action {
        data class UpdateText(val text: String) : Action()
        object Submit : Action()
    }

    sealed class Mutation {
        data class SetText(val text: String) : Mutation()
        data class SetSubmitting(val isSubmitting: Boolean) : Mutation()
    }

    data class State(
        val text: String = "",
        val isSubmitting: Boolean = false,
    )

    sealed class Event {
        data class SubmitSucceeded(val text: String) : Event()
    }

    override fun mutate(action: Action): Flow<Mutation> = flow {
        when (action) {
            is Action.UpdateText -> {
                emit(Mutation.SetText(action.text))
            }
            Action.Submit -> {
                val currentState = currentState
                if (currentState.isSubmitting) {
                    return@flow
                }
                emit(Mutation.SetSubmitting(true))
                try {
                    doSubmit()
                    publish(Event.SubmitSucceeded(currentState.text))
                    emit(Mutation.SetText(""))
                } catch (e: SubmitException) {
                    error(e)
                } finally {
                    emit(Mutation.SetSubmitting(false))
                }
            }
        }
    }

    override fun reduce(state: State, mutation: Mutation): State {
        return when (mutation) {
            is Mutation.SetText -> state.copy(
                text = mutation.text
            )
            is Mutation.SetSubmitting -> state.copy(
                isSubmitting = mutation.isSubmitting
            )
        }
    }
}

private class SubmitException : Exception("Error occurred during submit.")

private suspend fun doSubmit() {
    delay(1000)
    if (Random.Default.nextBoolean()) {
        log("doSubmit throws")
        throw SubmitException()
    }
}
