import SwiftUI
import shared

struct ContentView: View {
    
    @EnvironmentObject
    var reactor: AnyReactor<MyReactor.Action, MyReactor.State, MyReactor.Event>

    @ReactorState(\Self.reactor)
    var state: MyReactor.State

    @ActionBinding(\Self.reactor, valueKeyPath: \.text, action: { MyReactor.ActionUpdateText(text: $0) })
    var text: String

    var body: some View {
        VStack {
            Text("Playground")
                .font(.title)
            TextField(
                "Input text",
                text: $text,
                onEditingChanged: { isEditing in
                    reactor.send(action: MyReactor.ActionUpdateText(text: text))
                }
            )
            Button(
                action: {
                    NSLog("Sending")
                    reactor.send(action: MyReactor.ActionSubmit())
                },
                label: {
                    Text("Submit")
                }
            )
            if state.isSubmitting {
                ProgressView()
            }
        }.onReceive(reactor.error) {
            NSLog("Error: \($0)")
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
