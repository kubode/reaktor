import Foundation

protocol Reactor {
    associatedtype Action
    associatedtype State
    associatedtype Event

    func send(_ action: Action)
}
