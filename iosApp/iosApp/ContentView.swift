import SwiftUI
import shared

struct ContentView: View {
    
    @EnvironmentObject var reactor: AnyReactor<MyReactor.Action, MyReactor.State, MyReactor.Event>

    @State var state: MyReactor.State = MyReactor.State(text: "", isSubmitting: false)
    @State var text: String = "" {
        didSet {
            NSLog(text)
        }
    }

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
        }.onReceive(reactor.state) {
            NSLog("State: \($0)")
            self.state = $0
            self.text = $0.text
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
