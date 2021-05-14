import Foundation
import Combine
import shared

protocol CombineReactor: Reactor {
    var currentState: State { get }
    var state: AnyPublisher<State, Never> { get }
    var event: AnyPublisher<Event, Never> { get }
    var error: AnyPublisher<KotlinThrowable, Never> { get }
}

final class AnyCombineReactor<Action: AnyObject, State: AnyObject, Event: AnyObject> : CombineReactor {

    private let reactor: ReaktorNativeReactor<Action, State, Event>

    private var job: Kotlinx_coroutines_coreJob?

    init(reactor: ReaktorNativeReactor<Action, State, Event>) {
        self.reactor = reactor
        self._state = CurrentValueSubject(reactor.currentState)
        self._event = PassthroughSubject()
        self._error = PassthroughSubject()

        job = reactor.collectInReactorScope(
            onState: { [weak self] in self?._state.value = $0 },
            onEvent: { [weak self] in self?._event.send($0) },
            onError: { [weak self] in self?._error.send($0) }
        )
    }

    deinit {
        job?.cancel(cause: nil)
        reactor.destroy()
    }

    var currentState: State {
        reactor.currentState
    }

    private let _state: CurrentValueSubject<State, Never>
    var state: AnyPublisher<State, Never> {
        _state.eraseToAnyPublisher()
    }

    private let _event: PassthroughSubject<Event, Never>
    var event: AnyPublisher<Event, Never> {
        _event.eraseToAnyPublisher()
    }

    private let _error: PassthroughSubject<KotlinThrowable, Never>
    var error: AnyPublisher<KotlinThrowable, Never> {
        _error.eraseToAnyPublisher()
    }

    func send(_ action: Action) {
        reactor.send(action: action)
    }
}
