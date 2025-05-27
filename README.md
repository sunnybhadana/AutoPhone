# AutoPhone

<img alt="Logo" src="app/src/main/res/drawable/ic_launcher.webp" width="120" />

**AutoPhone is a completely free Android application that manages your call history and contacts, with powerful call automation capabilities.** 
It provides quick access to making calls and sending text messages through the application interface,
including popular messengers (Whatsapp, Telegram, Signal, Viber, and Threema). 

## Key Automation Features

A standout feature of AutoPhone is its advanced call automation, designed to help you manage automated calls (like PagerDuty alerts) and reduce unnecessary interruptions, especially during off-hours.

*   **Intelligent Alert Handling:** Configure rules to automatically answer incoming calls and send DTMF tones after a specified delay. This is particularly useful for services like PagerDuty or other automated systems that require a quick response with a key press.
*   **Customizable Call Thresholds:** Set up time-interval based rules. For example, you can configure AutoPhone to:
    *   Automatically acknowledge the first 2 PagerDuty calls if they occur within a 60-minute window.
    *   If a 3rd call comes in within that same window, the app can then redirect it to you, ensuring you're alerted to potentially more critical situations.
*   **Versatile DTMF Automation:** Beyond PagerDuty, you can set up rules for any number to automatically send predefined DTMF key sequences. This can be used for navigating automated phone menus, accessing services, or any scenario where you need to send tones during a call.
*   **Batch Number Management**: Easily group multiple numbers under a single contact or rule, simplifying the management of automation settings for teams or multiple services.
*   **Flexible Auto-Answer Limits**: Control how many times a rule can auto-answer, with options for unlimited responses or resetting the count after a specified interval.

These features aim to give you more control over automated calls, allowing you to sleep more peacefully at night, knowing that routine alerts can be handled automatically while still being notified for persistent or potentially critical issues.

<br>

> [!DANGER]
> **Important Note on Usage:** The automation features, especially the ability to set unlimited auto-acknowledgments for services like PagerDuty, are powerful. If you configure the app to acknowledge all events automatically, you are solely responsible for any real threats or critical alerts that might be missed as a result. Please use these features responsibly and ensure your configurations align with your on-call duties and the criticality of the services you monitor. This application is a tool to assist you, not a replacement for critical judgment.

<br>

[Google Play](https://play.google.com/store/apps/details?id=com.revaltronics.autophone) (Note: The app is now completely free!)<br><br>

Based on [Simple Dialer](https://github.com/SimpleMobileTools/Simple-Dialer) and [Goodwy Dialer](https://github.com/Goodwy/Dialer).
