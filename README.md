# PoGo+LE

[![CircleCI](https://circleci.com/gh/Mygod/pogoplusle.svg?style=shield)](https://circleci.com/gh/Mygod/pogoplusle)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![Releases](https://img.shields.io/github/downloads/Mygod/pogoplusle/total.svg)](https://github.com/Mygod/pogoplusle/releases)
[![Language: Kotlin](https://img.shields.io/github/languages/top/Mygod/pogoplusle.svg)](https://github.com/Mygod/pogoplusle/search?l=kotlin)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/e70e52b1a58045819b505c09edcae816)](https://www.codacy.com/gh/Mygod/pogoplusle/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=Mygod/pogoplusle&amp;utm_campaign=Badge_Grade)
[![License](https://img.shields.io/github/license/Mygod/pogoplusle.svg)](LICENSE)

<a href="https://play.google.com/store/apps/details?id=be.mygod.pogoplusplus"><img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" height="48"></a>
([beta](https://play.google.com/apps/testing/be.mygod.pogoplusplus))

Automagically skips pairing dialog when connecting Pokémon GO Plus (Android 8+), and alerts you when things go wrong.

Context: Pairing dialog were introduced in [November 2020 security patch](https://source.android.com/security/bulletin/2020-11-01), for [fixing](https://android.googlesource.com/platform/system/bt/+/b3f12befdc4def7d695b6f1049cd02238eb1e4a8) [CVE-2020-12856](https://github.com/alwentiu/COVIDSafe-CVE-2020-12856) as discovered by [/u/BlueMysticNA](https://www.reddit.com/r/TheSilphRoad/comments/jujfm4/comment/gcdk4eb/).

## FAQ

Q: Is this app safe to use?  
A: Yes. This app uses `AccessibilityService` API to help you skip the pairing dialog. Since this app only interacts with Android system interface and system Settings but not the game, PoGo+LE is fully compliant with Niantic's terms of service and is safe to use.
