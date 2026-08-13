# Architecture

The checkpoint UI is intentionally delivered as one Android application module with feature-oriented packages. After visual approval it will be split into `core`, `data`, `domain`, and independent feature modules while preserving the UI contracts. State is immutable and hoisted at the app shell; production phases add ViewModels, repositories, use cases, Hilt, persistence and API implementations.
