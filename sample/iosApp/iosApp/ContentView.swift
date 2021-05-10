import SwiftUI
import shared

struct ContentView: View {
    
    @EnvironmentObject
    var reactor: AnySwiftUIReactor<MyReactor.Action, MyReactor.State, MyReactor.Event>

    var text: Binding<String> {
        Binding(
            get: { reactor.state.text },
            set: { reactor.send(MyReactor.ActionUpdateText(text: $0)) }
        )
    }

    var body: some View {
        VStack {
            Text("Playground")
                .font(.title)
            TextField(
                "Input text",
                text: text
            )
            Button(
                action: {
                    reactor.send(MyReactor.ActionSubmit())
                },
                label: {
                    Text("Submit")
                }
            )

            if reactor.state.isSubmitting {
                ProgressView()
            }

            Spacer().alert(item: $reactor.event) {
                Alert(title: Text("Succeeded!"), message: Text("\($0)"))
            }
            Spacer().alert(item: $reactor.error) {
                Alert(title: Text("Error!"), message: Text($0.message ?? ""))
            }
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
