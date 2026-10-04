# Anin Voice Assistant

Anin Voice Assistant with authenticated Subham speaker verification and custom output voice studio, rewritten as a high-performance React TypeScript application.

## Key Architectures & Features

- **Profile A (Authentication Voice - Subham)**:
  - Strict speaker verification ensuring privileged commands are executed only when Subham is verified.
  - Fail-Closed Security Policy: If speaker verification fails or an unauthorized voice speaks, Anin remains completely silent.
  - Multi-sample guided enrollment across English, Bengali, and Hindi.
  - Anti-self-authentication guard preventing synthesizer loopback attacks.
- **Profile B (Output Voice System - Anin)**:
  - Fully separate from Profile A to guarantee zero self-authorization.
  - 5-step custom voice profile setup wizard with consent verification, audio quality checks, and 10-day retention scheduling.
  - Multi-lingual synthesis with honest fallback policy for Bengali, Hindi, and English.
  - Live audio waveform visualizer and emergency barge-in stop.
- **Personal Memory Vault & Scheduled Reminders**:
  - On-device local encrypted vault for preferences, instructions, and contacts.
  - Scheduled reminder queue.
- **Privacy & Diagnostics**:
  - 10-day automatic retention purge for raw voice samples.
  - Processing modes: Automatic, Offline Only, Online Only.
  - Security audit event stream logging.
