# The New AI

A planned Android-first personal AI assistant focused on natural conversation, voice interaction, useful research, and responsible device automation.

> **Project status:** Planning / early development. This repository was empty when this README was prepared. The capabilities below describe the intended product; they are not claims that these features are already implemented or tested.

## Product goals

- Build a helpful assistant that feels natural and conversational rather than returning repetitive, scripted replies.
- Prioritize a working AI core before expanding the interface.
- Combine everyday chat and research in one continuous conversation.
- Keep the app modular so capabilities and AI providers can be changed without rebuilding the whole project.
- Be transparent about permissions, actions, limitations, and errors.

## Planned features

### 1. Unified AI conversation
- One main conversation for everyday questions, follow-up context, and research.
- Natural greetings and small talk without unnecessary web searches.
- Context-aware answers that use the current conversation appropriately.
- Clear, useful error messages instead of fake or placeholder responses.
- A future conversation history feature, with user control over saved data.

### 2. Web research
- Search the web when a question needs current, external, or source-based information.
- Summarize findings in plain language and provide source links.
- Distinguish general knowledge from information retrieved from the web.
- Avoid searching for simple greetings or ordinary conversation unless requested.

### 3. Voice interaction
- Speech input for hands-free questions and commands, subject to Android permissions.
- Spoken responses using text-to-speech, with a preferred male voice where supported.
- Adjustable voice settings such as voice choice and speech behavior where available.
- Visible listening, processing, speaking, and error states.
- Respect microphone permission and provide a clear way to stop listening.

### 4. Wake-word activation
- Planned “Hey Nexus” wake-word support (the assistant name may be configured for this project).
- Evaluate an on-device wake-word engine such as Porcupine or openWakeWord for Android compatibility.
- Show when wake-word listening is enabled and provide controls to disable it.
- Do not claim wake-word or background listening works until it is implemented and tested on supported devices.

### 5. Android assistant actions
- Open installed apps when the user explicitly asks, where Android permits it.
- Support a carefully scoped set of useful device actions over time.
- Ask for confirmation before consequential actions.
- Use Android's official permission and intent mechanisms rather than bypassing platform protections.
- Explain when an action is unsupported or requires user interaction.

### 6. Background operation
- Explore Android foreground-service support for user-enabled voice features.
- Display the required persistent system notification while a foreground service is active.
- Respect battery, microphone, and background-execution restrictions imposed by Android.
- Allow users to stop the service and revoke permissions at any time.

### 7. Permission setup and privacy
- Explain why each permission is needed before requesting it.
- Request permissions only when a feature needs them.
- Handle denied or revoked permissions gracefully.
- Keep API keys and other secrets out of source code and public commits.
- Use secure configuration for any optional remote AI or speech provider.
- Minimize collection and retention of audio and conversation data.

### 8. Interface and accessibility
- Android-first interface, with a landscape-friendly layout.
- Dark-blue visual theme with clear contrast and readable text.
- Chat and Research presented as one unified experience rather than disconnected assistants.
- Voice controls and status indicators that are easy to understand.
- Responsive layouts for different screen sizes and orientation changes.
- Preserve user interface settings between app launches when implemented.

### 9. Modular architecture
- Separate conversation, research, voice, Android actions, settings, and UI components.
- Keep AI and speech providers replaceable where practical.
- Avoid hard-coded provider credentials and unnecessary provider lock-in.
- Keep platform-specific Android functionality isolated from shared logic.

### 10. Reliability and user control
- No fabricated claims that an action, search, or task succeeded.
- Clear handling of network, provider, microphone, and permission errors.
- Logging that avoids storing secrets or unnecessary personal content.
- Test core features on real Android devices before describing them as stable.
- Provide controls to pause or disable optional capabilities.

### 11. Future development ideas
These are longer-term possibilities, not committed functionality:
- Optional personalization based on user-approved preferences.
- Learning from explicit feedback while keeping the user in control of stored information.
- Helping review code and identify potential issues in projects the user provides.
- Additional local or hybrid AI capabilities to reduce dependence on remote APIs, subject to device performance and feasibility.

## Development principles

1. Build and test the core conversation first.
2. Add web research as a capability of the same assistant.
3. Add voice input and spoken output, then evaluate wake-word activation.
4. Add Android actions with permission checks and user confirmation.
5. Improve the interface and persistence after core flows are reliable.
6. Document what is implemented, partially implemented, planned, and unsupported.

## Status

| Area | Status |
|---|---|
| Android-first AI assistant concept | Planned |
| Unified chat and research | Planned |
| Web research with sources | Planned |
| Speech input and text-to-speech | Planned |
| “Hey Nexus” wake word | Under evaluation |
| Android app launching | Planned |
| Background voice operation | Under evaluation |
| Permission and privacy controls | Planned |
| Modular architecture | Planned |
| Self-improvement / code review | Future idea |

## Important note

This README is a product feature blueprint. Update the status table as code is added and tested so that the repository accurately reflects the working application.
