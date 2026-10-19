# NewsFeed

A free, ad-free news reader for Android with Ground News–style bias tools. Built with Kotlin and Jetpack Compose.

- **Today**: stories grouped across outlets, each with a Left / Center / Right coverage bar and source count
- **Story view**: every outlet's headline on the story, filterable by Left, Center or Right, with each outlet's bias rating
- **Blindspot**: stories one side of the spectrum is barely covering
- **Daily Brief**: weather for your city (Open-Meteo), plus Global Conflict and Space & Physics briefings with sources. Sections are editable.
- **DeArrow-style headlines**: strips clickbait framing and picks the plainest headline when several outlets cover a story (toggle in settings)
- **Your news bias**: which way the articles you open lean
- **Search**, **Saved**, and **Sources & settings** (add any site or RSS feed, change any outlet's rating)

Bias ratings follow AllSides media bias ratings at the outlet level. No accounts, no API keys, no tracking.

## Install

Every push to `main` builds a signed APK and publishes it under **Releases**. Download the newest `NewsFeed-x.y.z.apk` on your phone and open it. New releases install over the old one.
