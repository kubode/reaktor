import Foundation
import RxSwift
import shared

protocol RxSwiftReactor: Reactor {
    var currentState: State { get }
    var state: Observable<State> { get }
    var event: Observable<Event> { get }
    var error: Observable<KotlinThrowable> { get }
}

final class AnyRxSwiftReactor<Action: AnyObject, State: AnyObject, Event: AnyObject> : RxSwiftReactor {

    private let reactor: ReaktorNativeReactor<Action, State, Event>

    private var job: Kotlinx_coroutines_coreJob?

    init(reactor: ReaktorNativeReactor<Action, State, Event>) {
        self.reactor = reactor
        self._state = BehaviorSubject(value: reactor.currentState)
        self._event = PublishSubject()
        self._error = PublishSubject()

        job = reactor.collectInReactorScope(
            onState: { self._state.onNext($0) },
            onEvent: { self._event.onNext($0) },
            onError: { self._error.onNext($0) }
        )
    }

    deinit {
        job?.cancel(cause: nil)
        reactor.destroy()
    }

    var currentState: State {
        reactor.currentState
    }

    private let _state: BehaviorSubject<State>
    var state: Observable<State> { _state }

    private let _event: PublishSubject<Event>
    var event: Observable<Event> { _event }

    private let _error: PublishSubject<KotlinThrowable>
    var error: Observable<KotlinThrowable> { _error }

    func send(_ action: Action) {
        reactor.send(action: action)
    }
}
