import SwiftUI
import shared

extension KotlinThrowable: Identifiable {}

struct ContentView: View {
    
    @EnvironmentObject
    var reactor: AnyReactor<MyReactor.Action, MyReactor.State, MyReactor.Event>

    @ReactorState(\Self.reactor)
    var state: MyReactor.State

    @ActionBinding(\Self.reactor, valueKeyPath: \.text, action: { MyReactor.ActionUpdateText(text: $0) })
    var text: String

    @State
    private var error: KotlinThrowable? = nil

    var body: some View {
        VStack {
            Text("Playground")
                .font(.title)
            TextField(
                "Input text",
                text: $text
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
        }
        .alert(item: $error) {
            Alert(title: Text("Error!"), message: Text($0.message ?? ""))
        }
        .onReceive(reactor.error) {
            error = $0
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
