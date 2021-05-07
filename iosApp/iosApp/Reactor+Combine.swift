import Foundation
import Combine
import shared

protocol CombineReactor: Reactor {
    var currentState: State { get }
    var state: AnyPublisher<State, Never> { get }
    var event: AnyPublisher<Event, Never> { get }
    var error: AnyPublisher<Error, Never> { get }
}

final class AnyReactor<Action: AnyObject, State: AnyObject, Event: AnyObject> : Reactor {

    private let reactor: ReaktorAbstractReactor<Action, State, Event>

    init(reactor: ReaktorAbstractReactor<Action, State, Event>) {
        self.reactor = reactor
    }

    deinit {
        reactor.destroy()
    }

    var currentState: State {
        reactor.currentState
    }

    var state: AnyPublisher<State, Never> {
        FlowPublisher(flow: reactor.state).eraseToAnyPublisher()
    }

    var event: AnyPublisher<Event, Never> {
        FlowPublisher(flow: reactor.event).eraseToAnyPublisher()
    }

    var error: AnyPublisher<KotlinThrowable, Never> {
        FlowPublisher(flow: reactor.error).eraseToAnyPublisher()
    }

    func send(_ action: Action) {
        reactor.send(action: action)
    }
}

struct FlowPublisher<Output, Failure>: Publisher where Output: AnyObject, Failure: Error {

    private let flow: ReaktorFlowWrapper<Output>

    init(flow: ReaktorFlowWrapper<Output>) {
        self.flow = flow
    }

    func receive<S>(subscriber: S) where S : Subscriber, Self.Failure == S.Failure, Self.Output == S.Input {
        NSLog("FlowPublisher receive")
        subscriber.receive(subscription: Subscription(flow: flow, target: subscriber))
    }
}

private extension FlowPublisher {

    final class Subscription<Target: Subscriber>: Combine.Subscription where Target.Input == Output {

        private let job: Kotlinx_coroutines_coreJob

        init(flow: ReaktorFlowWrapper<Output>, target: Target) {
            NSLog("Subscription init")
            job = flow.subscribeInMainScope {
                NSLog("\($0)")
                _ = target.receive($0)
            }
        }

        func request(_ demand: Subscribers.Demand) {
        }

        func cancel() {
            NSLog("Cancelled")
            job.cancel(cause: nil)
        }
    }
}
