import Foundation
import shared
import Combine
import SwiftUI

protocol SwiftUIReactor: Reactor, ObservableObject {
    var state: State { get }
    var event: Event? { get }
    var error: KotlinThrowable? { get }
}

protocol TestableSwiftUIReactor: SwiftUIReactor {
    var state: State { get set }
    var event: Event? { get set }
    var error: KotlinThrowable? { get set }
}

extension KotlinBase: Identifiable {}

final class AnySwiftUIReactor<Action: AnyObject, State: AnyObject, Event: AnyObject>: SwiftUIReactor {

    private var cancellables: Set<AnyCancellable> = []
    private let action: (Action) -> Void

    init(currentState: State, state: AnyPublisher<State, Never>, event: AnyPublisher<Event, Never>, error: AnyPublisher<KotlinThrowable, Never>, cancellable: Cancellable, action: @escaping (Action) -> Void) {
        self.state = currentState
        self.action = action
        state.sink { self.state = $0 }.store(in: &cancellables)
        event.sink { self.event = $0 }.store(in: &cancellables)
        error.sink { self.error = $0 }.store(in: &cancellables)
        cancellable.store(in: &cancellables)
    }

    @Published
    private(set) var state: State

    @Published
    var event: Event?

    @Published
    var error: KotlinThrowable?

    func send(_ action: Action) {
        self.action(action)
    }
}

extension AnySwiftUIReactor {

    convenience init(combineReactor: AnyCombineReactor<Action, State, Event>) {
        self.init(currentState: combineReactor.currentState, state: combineReactor.state, event: combineReactor.event, error: combineReactor.error, cancellable: AnyCancellable {}, action: { combineReactor.send($0) })
    }

    convenience init(reactor: ReaktorNativeReactor<Action, State, Event>) {
        self.init(combineReactor: AnyCombineReactor(reactor: reactor))
    }
}
