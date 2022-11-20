# NoNonsense Notes

[![Android build](https://github.com/spacecowboy/NotePad/actions/workflows/android_build.yml/badge.svg)](https://github.com/spacecowboy/NotePad/actions/workflows/android_build.yml)          [![Android tests](https://github.com/spacecowboy/NotePad/actions/workflows/android_tests.yml/badge.svg)](https://github.com/spacecowboy/NotePad/actions/workflows/android_tests.yml)      [![Crowdin](https://d322cqt584bo4o.cloudfront.net/nononsensenotes/localized.png)](https://crowdin.com/project/nononsensenotes)

<img src="https://img.shields.io/f-droid/v/com.nononsenseapps.notepad.svg?logo=F-Droid"/>
<img src="https://img.shields.io/github/release/spacecowboy/NotePad.svg?logo=github"/>

<img src="https://img.shields.io/github/release-date/spacecowboy/NotePad"/>
<img src="https://img.shields.io/github/downloads/spacecowboy/NotePad/latest/total"/>

<img src="https://img.shields.io/github/last-commit/spacecowboy/NotePad"/>
<img src="https://img.shields.io/github/search/spacecowboy/NotePad/TODO"/>
<img src="https://img.shields.io/librariesio/github/spacecowboy/NotePad"/>

A note taking app for android with reminders, since 2012.

The app is currently being updated, and old versions are still available on f-droid:

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">
](https://f-droid.org/repository/browse/?fdid=com.nononsenseapps.notepad)

How it looks like:

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="Phone UI" height="480" /><img src="fastlane/metadata/android/en-US/images/tenInchScreenshots/1.png" alt="Tablet UI" height="360" />

## Reporting bugs

Please report bugs by creating an [issue here](https://github.com/spacecowboy/NotePad/issues).
I'd very much appreciate it if you could write clear steps to reproduce the bug: use the provided template.

## Build the project

```sh
git clone https://github.com/spacecowboy/NotePad
cd NotePad
./gradlew check
./gradlew installDebug
```

if it does not work, open an issue [here](https://github.com/spacecowboy/NotePad/issues)

## Getting Google Tasks sync to work

The app was once able to synchronize with Google Tasks, then Google made some changes and
now **that functionality is not available anymore**. Therefore, the related settings are disabled.
Developers interested in maintaining this feature should open a new issue
[here](https://github.com/spacecowboy/NotePad/issues) to get help on how to proceed.
See also issue #403

Once the code is fixed,
You need to put your API keys in a file, like the sample here:
https://github.com/spacecowboy/NotePad/blob/master/core/assets/secretkeys.properties.sample

But first you will of course need to get yourself a key.
Follow the instructions on this page:
https://developers.google.com/tasks/firstapp#register

## Where files are saved

The app can save 2 kinds of files:
* org files, which are used for SD card synchronization. You can choose to save them in a subdirectory of your `Documents` folder, where they will be visible to many apps, including your file manager
* json files, for the backup-restore functionality

Unless otherwise specified, files are saved in a subfolder of `Android/data/` accessible only
to this app. It is still possible to view them by connecting the device to a computer.

## License

```text
Copyright (C) 2014 Jonas Kalderstam

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```

See also [the full license](LICENSE)

## Useful links

* the [FAQ page](app/FAQ.md)
* our [contributions guide](CONTRIBUTING.md)
* the list of [releases](https://github.com/spacecowboy/NotePad/releases)
* our [privacy policy](PRIVACY_POLICY.txt)
