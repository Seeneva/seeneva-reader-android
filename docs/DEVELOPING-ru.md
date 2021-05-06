# Разработка

Склонируйте репозиторий, используя команду `git clone --recurse-submodules https://github.com/Seeneva/seeneva-reader-android.git` для инициализации всех git подмодулей, например [seeneva-lib](https://github.com/Seeneva/seeneva-lib).

Используйте Android Studio и Gradle чтобы собрать *Seeneva* apk/bundle.

## Требования

- Linux. Возможно получится собрать и с помощью macOS, но это не проверялось. Ваша система должна иметь возможность запуска shell скриптов.
- Android [SDK](https://developer.android.com/studio).
- Android [NDK](https://developer.android.com/ndk). Проверьте используемый проектом `ndkVersion`, чтобы установить правильную версию.
- [CMake](https://developer.android.com/ndk/guides/cmake). Может быть установлен с помощью Android Studio.
- [Autotools](https://www.gnu.org/software/automake/faq/autotools-faq.html).
- [Kotlin](https://developer.android.com/kotlin). Может быть установлен с помощью Android Studio.
- [Rust](https://www.rust-lang.org/tools/install). Rustup сам установит необходимые toolchain и цели для кросс компиляции, используя файл [rust-toolchain](rust-toolchain).
  
  - Установка Android целей вручную:

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

## Варианты сборки Gradle

- `rustDubug`: собрать debug динамическую библиотеку.
- `rustRelease`: собрать release динамическую библиотеку.

 Обычно вы должны использовать `rustRelease` build flavor для лучшей производительности ML.

Полученная динамическая библиотека всегда будет содержать дебаг символы (`-g` cflag). Поэтому динамическая библиотека может достигать 200+ MБ. Но не волнуйтесь об этом, Android Gradle плагин уберёт дебаг символы прежде чем упаковать динамическую библиотеку в apk приложения. Эти дебаг символы помогут при [дебаге](#дебаг-нативного-кода) нативного кода.

## Gradle свойства

Вы можете использовать следующие свойства:

- `seeneva.disableSplitApk`: отключает деление apk. Будет сгенерирован только один универсальный apk.
- `seeneva.noDebSymbols`: отключает генерацию дебаг символов для нативного кода.

## Дебаг нативного кода

Ваша apk должна поддерживать дебаг (debaggable).

:exclamation:**Примечание:** Нативная часть приложения `Seeneva` была написана, используя язык Rust. Дебаг Rust кода в Android Studio или Intellij IDEA Community edition GUI невозможен.

Для дебага вы можете использовать [Visual Studio Code](https://code.visualstudio.com) с установленным расширением [CodeLLDB](https://marketplace.visualstudio.com/items?itemName=vadimcn.vscode-lldb).

У вас есть две возможности начать дебаг:

1. Открыть директорию `./native` в VS Code (`code ./native`) и запустить VS Code таску `Attach Android debugger`.
2. Запустить shell скрипт `./native/scripts/attach_android_debugger.sh` который запустит VS Code и LLDB. Не забудьте передать необходимые аргументы!

## Версия приложения

Вы можете установить версию приложения, используя:

1. Файл `seeneva.properties`.

    ```text
    seeneva.versionName=x.y.z
    seeneva.versionCode=1
    ```

2. Передать те же значения как Gradle свойства:

    ```console
    gradlew :app:assembleRelease -Pseeneva.versionName=x.y.z -Pseeneva.versionCode=1
    ```

3. Как и в предыдущем варианте, но используя переменные окружения (env variables):

    ```console
    export SEENEVA_VERSION_NAME=x.y.z
    export SEENEVA_VERSION_CODE=1
    ```

## Подпись приложения

Любой `Release` билд должен быть подписан. [Почитайте](https://developer.android.com/studio/publish/app-signing#sign-apk) как создать собственный ключ.

Есть несколько путей подписи приложения:

1. Используя [Android Studio](https://developer.android.com/studio/publish/app-signing#sign_release) GUI.
2. Используя Gradle для автоматизации процесса подписи:
    - Поместите файл `keystore.properties` в корень `app` модуля:

        ```text
        seeneva.storeFile=</path/to/my.jks>
        seeneva.storePassword=<mYpAsSWord>
        seeneva.keyAlias=<my_key_alias>
        seeneva.keyPassword=<mYpAsSWord>
        ```

    - Или передайте те же значения, но как Gradle свойства.
    - Поместите свои значения заместо `<...>`.
    - Запустите `gradlew :app:assembleRelease` Gradle таску, чтобы собрать подписанный apk.

3. Как и в предыдущем варианте, но используя переменные окружения (env variables):

    ```console
    export SEENEVA_STORE_FILE=</path/to/my.jks>
    export SEENEVA_STORE_PASS=<mYpAsSWord>
    export SEENEVA_KEY_ALIAS=<my_key_alias>
    export SEENEVA_KEY_PASS=<mYpAsSWord>
    ```

## Форматирование

Проект содержит следующие файлы, описывающие предпочтительный стиль кода (code style):

- `.editorconfig`
- `rustfmt.toml` для форматирования кода на Rust, используя [rustfmt](https://github.com/rust-lang/rustfmt).

Пожалуйста, убедитесь, что поддержка этих файлов включена в вашем редакторе.

## Fastlane

Проект использует [Fastlane](https://fastlane.tools) для автоматизации процесса сборки и загрузки проекта. Обычно Fastlane используется проектом в CI.

### Подготовка

Установите [Bundler](https://bundler.io) и следуйте [инструкции](https://docs.fastlane.tools/getting-started/android/setup) по установке Fastlane. Убедитесь, что вы используете поддерживаемую версию Ruby. Помните, что в данный момент Fastlane [не поддерживает Ruby 3.0](https://github.com/fastlane/fastlane/issues/17931). Вы можете использовать [rbenv](https://github.com/rbenv/rbenv) или схожие инструменты, чтобы обойти это ограничение. Используйте версию Ruby, указанную в файле `.ruby-version`.

Теперь вы можете установить все требуемые Ruby gems:

```console
bundle install
```

### Конфиденциальные данные

Используйте файлы формата [dotenv](https://github.com/bkeepers/dotenv), чтобы передать переменные окружения в Fastlane. Эти файлы всегда должны быть исключены из системы контроля версий git.

Пример:

- `.env.default`:

  ```text
  SUPPLY_JSON_KEY="fastlane_google_play_credentials.json"
  ```

- файлы `.env.dev` и `.env.gplay` описывают реквизиты для debug и upload связок ключей:

  ```text
  SEENEVA_STORE_FILE="seeneva.keystore"
  SEENEVA_KEY_ALIAS="key"
  SEENEVA_STORE_PASS="android"
  ```

Теперь вы можете указать какую конфигурацию использовать:

```console
bundle exec fastlane gplay_publish_internal --env gplay
```

## Рабочий процесс в git

Основан на известном [git-flow](https://danielkummer.github.io/git-flow-cheatsheet/index.ru_RU.html).

### Ветки

- **master**: защищённая ветка. Все git merge должны быть сделаны через GitHub Pull Request. Создание GitHub Release с тегом *vX.Y.Z* запустит CI. СI соберёт необходимые APK и прикрепит их к созданному GH Release, затем соберёт AAB и отправит в Google Play, используя Fastlane.
- **dev**: защищённая ветка. Все git merge должны быть сделаны через GitHub Pull Request. Создание GitHub Release с тегом *vX.Y.Z-nightly* запустит CI. СI соберёт необходимые APK и прикрепит их к созданному GH Release.
- **feature_branch**: может иметь любое имя. Всегда создаётся из **dev** и вливается обратно.
- **release/x.y.z**: новая версия приложения готова. x.y.z описывает новое имя версии приложения, например 0.1.0. Эта ветка всегда создаётся из **dev** и вливается в **master** и **dev**. Имя и код версии будут высчитаны и закомичены в процессе Pull Request в **master** ветку с помощью CI.
- **hotfix/x.y.z**: экстренное исправление ошибки. x.y.z описывает новое имя версии приложения, например 0.1.0. Эта ветка всегда создаётся из **master** и вливается в **master** и **dev**. Имя и код версии будут высчитаны и закомичены в процессе Pull Request в **master** ветку с помощью CI.
- **master_feature_branch**: иногда необходимо изменить некоторые файлы на **master** ветке, например GitHub issue templates. Эта ветка может иметь любое имя. Всегда создаётся из **master** и вливается в **master** и **dev**.
