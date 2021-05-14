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

    private(set) var currentState: State
    let state: Observable<State>
    let event: Observable<Event>
    let error: Observable<KotlinThrowable>
    private let disposeBag: DisposeBag = DisposeBag()
    private let action: (Action) -> Void

    init(currentState: State, state: Observable<State>, event: Observable<Event>, error: Observable<KotlinThrowable>, disposable: Disposable, action: @escaping (Action) -> Void) {
        self.currentState = currentState
        self.state = state
        self.event = event
        self.error = error
        self.action = action

        disposeBag.insert(disposable)
        state.subscribe(onNext: { [weak self] in self?.currentState = $0 }).disposed(by: disposeBag)
    }

    func send(_ action: Action) {
        self.action(action)
    }
}

extension AnyRxSwiftReactor {
    convenience init(reactor: ReaktorNativeReactor<Action, State, Event>) {
        let state = BehaviorSubject(value: reactor.currentState)
        let event = PublishSubject<Event>()
        let error = PublishSubject<KotlinThrowable>()

        let job = reactor.collectInReactorScope(
            onState: { state.onNext($0) },
            onEvent: { event.onNext($0) },
            onError: { error.onNext($0) }
        )
        let disposable = Disposables.create {
            job.cancel(cause: nil)
        }

        let action: (Action) -> Void = { reactor.send(action: $0) }

        self.init(currentState: reactor.currentState, state: state, event: event, error: error, disposable: disposable, action: action)
    }
}
