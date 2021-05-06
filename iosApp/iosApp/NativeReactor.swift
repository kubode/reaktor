import Foundation
import shared
import Combine
import SwiftUI

protocol Reactor: ObservableObject {
    associatedtype Action
    associatedtype State
    associatedtype Event
    
    var currentState: State { get }
    var state: AnyPublisher<State, Never> { get }
    var event: AnyPublisher<Event, Never> { get }
    var error: AnyPublisher<KotlinThrowable, Never> { get }
    func send(action: Action)
}

@propertyWrapper
struct ReactorState<SelfType, ReactorType: Reactor>: DynamicProperty {

    init(_ reactorKeyPath: KeyPath<SelfType, ReactorType>) {
    }

    @EnvironmentObject
    private var reactor: ReactorType
    
    var wrappedValue: ReactorType.State {
        reactor.currentState
    }
    
    var projectedValue: Binding<Value> {
        get { target.wrappedValue.mutate(binding: keyPath, action) }
    }
}

@propertyWrapper
struct ActionBinding<SelfType, ReactorType: Reactor, Value>: DynamicProperty {
    
    private let valueKeyPath: KeyPath<ReactorType.State, Value>
    private let action: (Value) -> ReactorType.Action
    
    init(_ reactorKeyPath: KeyPath<SelfType, ReactorType>, valueKeyPath: KeyPath<ReactorType.State, Value>, action: @escaping (Value) -> ReactorType.Action) {
        self.valueKeyPath = valueKeyPath
        self.action = action
    }
    
    @EnvironmentObject
    private var reactor: ReactorType
    
    var wrappedValue: Value {
        get { projectedValue.wrappedValue }
        nonmutating set { projectedValue.wrappedValue = newValue }
    }
    
    var projectedValue: Binding<Value> {
        Binding(
            get: { reactor.currentState[keyPath: valueKeyPath] },
            set: { reactor.send(action: action($0)) }
        )
    }
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
    
    func send(action: Action) {
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
