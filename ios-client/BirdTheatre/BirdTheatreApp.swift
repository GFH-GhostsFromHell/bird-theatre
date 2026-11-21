import SwiftUI
import WebKit

@main
struct BirdTheatreApp: App {
    var body: some Scene {
        WindowGroup { CommunityRoot() }
    }
}

private enum Theatre {
    static let origin = URL(string: "https://bird-theatre-h7xq.onrender.com")!
}

@MainActor
final class TheatreBrowser: ObservableObject {
    weak var webView: WKWebView?
    @Published var error: String?
    @Published var loading = true

    func navigate(to route: String) {
        guard let url = URL(string: "#\(route)", relativeTo: Theatre.origin)?.absoluteURL else { return }
        webView?.load(URLRequest(url: url))
    }
}

struct CommunityRoot: View {
    @StateObject private var browser = TheatreBrowser()
    @State private var selected = "explore"

    private let routes: [(String, String, String)] = [
        ("explore","Explore","leaf"),
        ("cameras","Stations","web.camera"),
        ("sightings","Visits","bird"),
        ("alerts","Alerts","bell"),
        ("rfid","RFID","waveform.path")
    ]
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("✦  bird theatre.")
                    .font(.title3.bold()).foregroundStyle(.white)
                Spacer()
                if browser.loading { ProgressView().tint(.white) }
            }
            .padding(.horizontal, 18).padding(.vertical, 12)
            .background(Color(red: 0.07, green: 0.19, blue: 0.14))
            if let error = browser.error {
                HStack {
                    Text(error).font(.caption)
                    Spacer()
                    Button("Reload") { browser.navigate(to: selected) }
                }.padding(10).background(Color.yellow.opacity(0.2))
            }
            TheatreWebView(browser: browser)
            HStack(spacing: 0) {
                ForEach(routes.indices, id: \.self) { index in
                    let route = routes[index]
                    Button {
                        selected = route.0
                        browser.navigate(to: route.0)
                    } label: {
                        VStack(spacing: 4) {
                            Image(systemName: route.2).font(.system(size: 19))
                            Text(route.1).font(.system(size: 10, weight: .semibold))
                        }
                        .frame(maxWidth: .infinity)
                        .foregroundStyle(selected == route.0 ? Color.green : Color.secondary)
                        .frame(height: 54)
                    }
                }
            }
            .background(Color(uiColor: .systemBackground))
        }
    }
}

struct TheatreWebView: UIViewRepresentable {
    @ObservedObject var browser: TheatreBrowser
    func makeCoordinator() -> Coordinator { Coordinator(browser: browser) }
    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.allowsInlineMediaPlayback = true
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.navigationDelegate = context.coordinator
        view.allowsBackForwardNavigationGestures = true
        browser.webView = view
        view.load(URLRequest(url: Theatre.origin))
        return view
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
    final class Coordinator: NSObject, WKNavigationDelegate {
        let browser: TheatreBrowser
        init(browser: TheatreBrowser) { self.browser = browser }
        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = action.request.url else { decisionHandler(.cancel); return }
            if url.scheme == "https" && url.host == Theatre.origin.host {
                decisionHandler(.allow)
            } else {
                if url.scheme == "https" { UIApplication.shared.open(url) }
                decisionHandler(.cancel)
            }
        }
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            Task { @MainActor in browser.loading = false; browser.error = nil }
        }
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            Task { @MainActor in browser.loading = false; browser.error = "Couldn't reach the Bird Theatre portal." }
        }
    }
}
