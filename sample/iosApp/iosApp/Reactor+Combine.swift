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

    private(set) var currentState: State
    let state: AnyPublisher<State, Never>
    let event: AnyPublisher<Event, Never>
    let error: AnyPublisher<KotlinThrowable, Never>
    private var cancellables: Set<AnyCancellable> = []
    private let action: (Action) -> Void

    init(currentState: State, state: AnyPublisher<State, Never>, event: AnyPublisher<Event, Never>, error: AnyPublisher<KotlinThrowable, Never>, cancellable: Cancellable, action: @escaping (Action) -> Void) {
        self.currentState = currentState
        self.state = state
        self.event = event
        self.error = error
        self.action = action

        cancellable.store(in: &cancellables)
    }

    func send(_ action: Action) {
        self.action(action)
    }
}

extension AnyCombineReactor {

    convenience init(reactor: ReaktorNativeReactor<Action, State, Event>) {
        let state = CurrentValueSubject<State, Never>(reactor.currentState)
        let event = PassthroughSubject<Event, Never>()
        let error = PassthroughSubject<KotlinThrowable, Never>()

        let job = reactor.collectInReactorScope(
            onState: { state.value = $0 },
            onEvent: { event.send($0) },
            onError: { error.send($0) }
        )
        let cancellable = AnyCancellable {
            job.cancel(cause: nil)
        }

        let action: (Action) -> Void = { reactor.send(action: $0) }

        self.init(currentState: reactor.currentState, state: state.eraseToAnyPublisher(), event: event.eraseToAnyPublisher(), error: error.eraseToAnyPublisher(), cancellable: cancellable, action: action)
    }
}
