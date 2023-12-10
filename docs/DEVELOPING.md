# Developing

Clone this repository using `git clone --recurse-submodules https://github.com/Seeneva/seeneva-reader-android.git` command to properly init git submodules (e.g. [seeneva-lib](https://github.com/Seeneva/seeneva-lib)).

Use Android Studio and Gradle to build *Seeneva* apk/bundle.

## Requirements

- Linux. It might be possible to use macOS, but it has not been tested. Your environment should be able to run shell scripts.
- Android [SDK](https://developer.android.com/studio).
- Android [NDK](https://developer.android.com/ndk). Check project's `ndkVersion` to find out which version to install.
- [CMake](https://developer.android.com/ndk/guides/cmake). Can be installed using Android Studio. In order to compile the native module, `CMake` must be available via `PATH`.
- [Autotools](https://www.gnu.org/software/automake/faq/autotools-faq.html).
- [Kotlin](https://developer.android.com/kotlin). Can be installed using Android Studio.
- [Rust](https://www.rust-lang.org/tools/install). Rustup will automatically install all required
  toolchain and targets using [rust-toolchain](../rust-toolchain) file.

    - Android targets manual setup:

      ```console
      # Android arm64-v8a
      rustup target add aarch64-linux-android
      # Android armeabi-v7a
      rustup target add armv7-linux-androideabi
      # Android x86
      rustup target add i686-linux-android
      # Android x86_64
      rustup target add x86_64-linux-android
      ```

## Gradle build variants

- `rustDubug`: build debug shared library.
- `rustRelease`: build release shared library.

Usually you should use `rustRelease` build flavor for better ML performance.

Output shared library will always include debug symbols (`-g` cflag). That's why shared library can
have size 200+MB. But do not worry about it, Android Gradle plugin will strip debug symbols before
pack the shared library into the output apk. These debug symbols will allow you
to [debug](#native-debug) native code.

## Gradle properties

You can set these Gradle properties:

- `seeneva.disableSplitApk`: disable apk splitting. Generate only one universal apk.
- `seeneva.noDebSymbols`: do not generate native debug symbols.
- `seeneva.unsigned`: build unsigned APK/AAB outputs.

If your system's default JDK is not compatible with the project, you can pass a correct version (
e.g. version shipped with Android Studio) using Gradle *system* property.

- `org.gradle.java.home`

## Native debug

Your apk should be debaggable.

:exclamation:**Note:** Native part of `Seeneva` was written using Rust language. You can't debug it
using Android Studio or Intellij IDEA Community edition GUI.

You can use [Visual Studio Code](https://code.visualstudio.com)
with [CodeLLDB](https://marketplace.visualstudio.com/items?itemName=vadimcn.vscode-lldb) extension
to debug Rust code.

You have two options to start debugger:

1. Open `./native` directory in VS Code (`code ./native`) and run `Attach Android debugger` VS Code task.
2. Run shell script `./native/scripts/attach_android_debugger.sh` which will start VS Code and LLDB. Do not forget to pass required arguments!

## App version

You can set app version using:

1. `seeneva.properties` file.

    ```text
    seeneva.versionName=x.y.z
    seeneva.versionCode=1
    ```

2. Pass same values as Gradle properties:

    ```console
    gradlew :app:assembleRelease -Pseeneva.versionName=x.y.z -Pseeneva.versionCode=1
    ```

3. Same as previous, but using env variables:

    ```console
    export SEENEVA_VERSION_NAME=x.y.z
    export SEENEVA_VERSION_CODE=1
    ```

## Signing

Any `Release` build type should be signed. [Read](https://developer.android.com/studio/publish/app-signing#sign-apk) how to create your key.

You have multiple options how to sign the app:

1. Using [Android Studio](https://developer.android.com/studio/publish/app-signing#sign_release) GUI
2. Using Gradle to automatize signing process:
    - Put `keystore.properties` file into `app` module root:

        ```text
        seeneva.storeFile=</path/to/my.jks>
        seeneva.storePassword=<mYpAsSWord>
        seeneva.keyAlias=<my_key_alias>
        seeneva.keyPassword=<mYpAsSWord>
        ```

    - Or pass same values as Gradle properties.
    - Set your values instead of `<...>`.
    - Run `gradlew :app:assembleRelease` Gradle task to build signed apk.

3. Same as previous, but using env variables:

    ```console
    export SEENEVA_STORE_FILE=</path/to/my.jks>
    export SEENEVA_STORE_PASS=<mYpAsSWord>
    export SEENEVA_KEY_ALIAS=<my_key_alias>
    export SEENEVA_KEY_PASS=<mYpAsSWord>
    ```

## Formatting

This projects ships with predefined code styles:

- `.editorconfig`
- `rustfmt.toml` to format Rust code using [rustfmt](https://github.com/rust-lang/rustfmt).

Please ensure that they are enabled in your code editor.

## Fastlane

The project uses [Fastlane](https://fastlane.tools) to automate build and deploy processes. Usually it will be used by CI.

### Preparation

Install [Bundler](https://bundler.io) and check
Fastlane's [setup](https://docs.fastlane.tools/getting-started/android/setup) instruction. You
should ensure that you use supported Ruby version. You can
use [asdf](https://asdf-vm.com) to use Ruby version described in the `.tool-versions`
file.

After that you can install all required Ruby gems by calling:

```console
bundle install
```

### Sensitive data

Use [dotenv](https://github.com/bkeepers/dotenv) files
to [pass](https://docs.fastlane.tools/advanced/other/) env variables to Fastlane actions. These
files should always be ignored by git.

Example:

- `.env.default`:

  ```text
  SUPPLY_JSON_KEY="fastlane_google_play_credentials.json"
  // Override JDK path during build using Fastlane
  FL_GRADLE_SYSTEM_PROPERTIES={"org.gradle.java.home":"/android_studio/jre"}
  ```

- `.env.dev` and `.env.gplay` describes debug and upload keystores credentials:

  ```text
  SEENEVA_STORE_FILE="seeneva.keystore"
  SEENEVA_KEY_ALIAS="key"
  SEENEVA_STORE_PASS="android"
  SEENEVA_KEY_PASS="android"
  ```

Now you can specify which configuration to use:

```console
bundle exec fastlane gplay_publish_internal --env gplay
```

### Metadata limitations

- `changelogs`: max 500 characters
- `title`: max 30 characters

## Git workflow

Based on well known [Github flow](https://guides.github.com/introduction/flow).

### Branches

- **master**: protected branch. All merges should be done through GitHub Pull Request. GitHub
  Release with tag name *vX.Y.Z* will start CI job. This job will build and attach APKs to the GH
  Release and upload AAB to the Google Play using Fastlane.
- **develop**: protected branch. All merges should be done through GitHub Pull Request. The source
  branch for all feature branches.
- **feature_branch**: can have any name. It should be created from **develop** branch and merged
  back.
- **hotfix/any_name**: the urgent bug fix. This branch always created from the **master** branch and
  merged to the **master** and **develop** branches.
- **release/x.y.z**: new app release is ready. x.y.z should describe new app version name e.g.
  0.1.0. This branch created from **master** branch and merged to the **master** and **develop**
  branches. The app version name and code will be calculated and committed by CI during Pull
  Request.
