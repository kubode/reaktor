package com.example.playgroundreactor

import com.example.playgroundreactor.MyReactor.*
import com.github.kubode.reaktor.AbstractReactor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

class MyReactor : AbstractReactor<Action, Mutation, State, Event>(State()) {
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
        object SubmitSucceeded : Event()
        data class Error(val cause: Throwable) : Event()
    }

    override fun mutate(action: Action): Flow<Mutation> = flow {
        when (action) {
            is Action.UpdateText -> {
                emit(Mutation.SetText(action.text))
            }
            Action.Submit -> {
                if (currentState.isSubmitting) {
                    return@flow
                }
                emit(Mutation.SetSubmitting(true))
                try {
                    doSubmit()
                    publish(Event.SubmitSucceeded)
                } catch (e: IllegalArgumentException) {
                    error(e)
                    // or
                    publish(Event.Error(e))
                } finally {
                    emit(Mutation.SetSubmitting(false))
                }
            }
        }
    }

    override fun reduce(currentState: State, mutation: Mutation): State {
        return when (mutation) {
            is Mutation.SetText -> currentState.copy(text = mutation.text)
            is Mutation.SetSubmitting -> currentState.copy(isSubmitting = mutation.isSubmitting)
        }
    }
}

private suspend fun doSubmit() {
    delay(1000)
    if (Random.Default.nextBoolean()) {
        throw RuntimeException("Error occurred during submit.")
    }
}
