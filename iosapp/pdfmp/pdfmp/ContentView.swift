//
//  ContentView.swift
//  pdfmp
//
//  Created by Daniels Satcs on 26/11/2025.
//

import SwiftUI
import pdfmpcompose

struct SampleViewController: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        return pdfmpcompose.SampleViewControllerKt.SampleViewController();
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

struct ContentView: View {
    var body: some View {
        VStack {
            Image(systemName: "globe")
                .imageScale(.large)
                .foregroundStyle(.tint)
            Text("Hello, world!")
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
