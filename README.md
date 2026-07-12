# PinMe — защищённое хранилище контактов

PinMe — Android-приложение для локального хранения контактов, дат, напоминаний и истории разговоров. Основная реализация находится в каталоге `android/` и написана на Kotlin с Jetpack Compose.

> Web-приложение в `src/` — исходный прототип. Оно собирается и проверяется в CI, но пока хранит поля контактов в `localStorage` и не предназначено для реальных конфиденциальных данных.

## Возможности Android-приложения

- SQLCipher-шифрование базы данных с отдельным случайным ключом для каждой установки
- хранение ключа базы под защитой Android Keystore
- пароль и биометрия для доступа к приложению и защищённым контактам
- теги, категории, даты, напоминания и записи разговоров
- активные и неактивные контакты
- зашифрованный экспорт и импорт резервной копии
- запрет системных скриншотов и содержимого в превью недавних приложений
- автоматическая блокировка после ухода приложения в фон

Пароль отдельного контакта является уровнем авторизации интерфейса. Данные на диске защищает общий ключ SQLCipher; отдельного криптографического ключа для каждого контакта пока нет.

## Требования

- Android 8.0 или новее, API 26+
- Java 17
- Android SDK 34
- Node.js 22.12–25 для проверки web-прототипа

## Локальная сборка

```bash
cd android
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew compileDebugKotlin
./gradlew lintDebug testDebugUnitTest assembleDebug
```

Debug APK создаётся в `android/app/build/outputs/apk/debug/app-debug.apk`.

Проверка web-прототипа:

```bash
npm ci
npm run lint
npm run build
npm audit --audit-level=high
```

## GitHub Actions

В репозитории настроены:

- `CI`: lint, typecheck, production build и dependency audit web-прототипа; Android lint, unit tests и debug APK
- `Android Release`: подписанный APK, проверка подписи, SHA-256 checksum и публикация GitHub Release
- Dependabot: еженедельные обновления npm, Gradle и GitHub Actions

CI сохраняет debug APK как временный artifact на 14 дней. Этот artifact не является каналом обновлений.

## Настройка подписи релизов

Один раз создайте production keystore:

```bash
keytool -genkeypair -v \
  -keystore pinme-release.jks \
  -alias pinme \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Создайте в GitHub Environment с именем `release` и добавьте secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Получить Base64 без переноса строк:

```bash
base64 -i pinme-release.jks | tr -d "\n"
```

Keystore и его пароли нельзя коммитить. Храните независимую офлайн-копию: потеря production keystore сделает обновление уже установленных APK невозможным.

## Автоматические релизы

- изменение Android-кода в ветке `main` создаёт подписанный prerelease `build-…`
- тег вида `v1.0.0` создаёт стабильный GitHub Release
- workflow можно запустить вручную через Actions

```bash
git tag v1.0.0
git push origin v1.0.0
```

Все автоматические prerelease и стабильные APK подписываются одним production keystore и получают возрастающий `versionCode`.

## Переход с debug APK

Production-signed APK не обновит ранее установленный debug APK из-за другой подписи. Перед первым переходом:

1. Создайте зашифрованную резервную копию в установленном приложении.
2. Убедитесь, что помните резервный пароль.
3. Удалите debug-версию.
4. Установите production APK из GitHub Releases.
5. Импортируйте зашифрованную копию.

Удаление приложения стирает локальную базу, потому что системный backup намеренно отключён.

## Структура

```text
android/    нативное Android-приложение
src/        legacy web-прототип
.github/    CI, подписанные релизы и Dependabot
```

## Лицензия

Открытая лицензия пока не назначена. Все права сохраняются за владельцем проекта.
