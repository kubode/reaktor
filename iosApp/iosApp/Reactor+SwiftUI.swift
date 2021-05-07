import Foundation
import shared
import Combine
import SwiftUI

protocol SwiftUIReactor: Reactor, ObservableObject {
    var state: State { get }
    var event: Event? { get }
    var error: KotlinThrowable? { get }
}

extension KotlinBase: Identifiable {}

final class AnySwiftUIReactor<Action: AnyObject, State: AnyObject, Event: AnyObject>: SwiftUIReactor {

    private let reactor: ReaktorAbstractReactor<Action, State, Event>

    private var jobs: [Kotlinx_coroutines_coreJob] = []

    init(reactor: ReaktorAbstractReactor<Action, State, Event>) {
        self.reactor = reactor
        self.state = reactor.currentState
        jobs = [
            reactor.state.subscribeInMainScope { self.state = $0 },
            reactor.event.subscribeInMainScope { self.event = $0 },
            reactor.error.subscribeInMainScope { self.error = $0 },
        ]
    }

    deinit {
        jobs.forEach { $0.cancel(cause: nil) }
        reactor.destroy()
    }

    @Published
    private(set) var state: State

    @Published
    var event: Event?

    @Published
    var error: KotlinThrowable?

    func send(_ action: Action) {
        reactor.send(action: action)
    }
}
