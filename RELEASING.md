# Публикация Tomsmatic

Tomsmatic — мод для Fabric. Fabric загружает мод, а публичный каталог и скачивание предоставляет Modrinth. GitHub хранит исходники, запускает проверки и публикует релизные файлы.

## 1. Перенести коммиты в рабочую папку

Сохрани `Tomsmatic-0.3.11.bundle` в папку загрузок. Если `C:\Code\Mods\Tomsmatic` уже содержит подготовленный ранее Git-репозиторий, открой PowerShell и выполни:

```powershell
Set-Location 'C:\Code\Mods\Tomsmatic'
git status --short --branch
git fetch "$env:USERPROFILE\Downloads\Tomsmatic-0.3.11.bundle" main
git merge --ff-only FETCH_HEAD
```

Если ветки разошлись, `--ff-only` остановит слияние. Сохрани свои правки и проверь историю перед объединением. Если папка пуста или ещё не существует, вместо команд выше выполни:

```powershell
git clone --branch main --origin bundle "$env:USERPROFILE\Downloads\Tomsmatic-0.3.11.bundle" 'C:\Code\Mods\Tomsmatic'
Set-Location 'C:\Code\Mods\Tomsmatic'
```

## 2. Создать GitHub-репозиторий и отправить код

1. Войди в GitHub и открой [New repository](https://github.com/new). Выбери владельца и название `Tomsmatic`. Для первой проверки можно выбрать **Private**.
2. Оставь опции README, .gitignore и лицензии выключенными при создании пустого репозитория. README и история уже есть в исходниках.
3. Скопируй HTTPS-адрес нового репозитория. Подставь свой логин вместо `OWNER` и выполни из рабочей папки:

```powershell
git remote -v
git remote add origin https://github.com/OWNER/Tomsmatic.git
git push -u origin main
```

Если `origin` уже существует, проверь его адрес. Для смены адреса выбранного репозитория используй `git remote set-url origin URL`. Для входа через GitHub CLI можно выполнить `gh auth login` и `gh auth setup-git`.

4. Открой **Actions → Build and release**. Задача `build` запускается на каждом push в `main` и на pull request. Она скачивает закреплённые зависимости, компилирует код на Java 25, проверяет локализации и классы, объявленные в метаданных, и сохраняет JAR с SHA-256 в **Artifacts**. Если нужен обязательный статус для слияния, после первого успешного запуска добавь `build` в правила ветки `main`.
5. Перед открытием исходников выбери лицензию, добавь её текст в `LICENSE` и идентификатор в поле `license` файла `src/main/resources/fabric.mod.json`. Используй ту же лицензию на странице Modrinth. Закоммить изменения и выставь нужную видимость репозитория.

[Официальная инструкция GitHub по загрузке готового репозитория](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github?platform=windows)

## 3. Собрать GitHub Release

В примере выпускается текущая версия `0.3.11`. У следующих релизов обновляй версию в `src/main/resources/fabric.mod.json` перед созданием тега. Используй новый тег для каждого выпуска.

```powershell
git switch main
git pull --ff-only
git tag -a v0.3.11 -m "Tomsmatic 0.3.11"
git push origin v0.3.11
```

После успешной сборки в **Releases** появится черновик с `tomsmatic-0.3.11.jar` и файлом SHA-256. Примечания берутся из раздела `## 0.3.11` в [CHANGELOG.md](CHANGELOG.md). Для нового тега добавь одноимённый раздел в журнал до отправки тега. Проверь JAR в отдельном профиле Fabric, затем нажми **Publish release**.

Чтобы запустить ту же сборку локально, поставь Python 3.11 или новее и JDK 25, затем запусти `python scripts/build.py`. Файлы появятся в `build/release`. Сборка из установленного профиля Modrinth App через `build.ps1` тоже доступна.

[Официальная документация по релизам GitHub](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)

## 4. Сделать первую публикацию на Modrinth

1. Войди в Modrinth и создай проект типа **Mod** с названием **Tomsmatic**. Для короткого описания подойдёт `Compare Litematica materials with Tom’s Simple Storage and optionally refill Easy Place.` Для полного описания используй [MODRINTH.md](MODRINTH.md).
2. Добавь ссылку на исходники GitHub и лицензию. Для страницы Modrinth используй свою иконку, сделанную вручную. Иконка в JAR и README сгенерирована, а текст страницы подготовлен с помощью ИИ. Укажи это в раскрытии использования ИИ на Modrinth и проверь применимость [правил публикации](https://modrinth.com/legal/rules/) к проекту.
3. Создай первую версию вручную. Прикрепи JAR из GitHub Release, выбери **Minecraft 26.3**, загрузчик **Fabric** и канал **Release**. Добавь **Fabric API** и **Tom’s Simple Storage** как обязательные зависимости. Укажи **Litematica**, **MaLiLib** и **Mod Menu** в связанных зависимостях. В универсальном JAR они опциональны для сервера. На клиенте для самой сверки требуются Litematica и MaLiLib.
4. Открой страницу проекта, проверь карточку версии и отправь проект на модерацию, когда интерфейс предложит **Submit for review**.

[Правила публикации Modrinth](https://modrinth.com/legal/rules/)

## 5. Настроить отправку следующих релизов из GitHub Actions

1. Возьми восьмисимвольный ID проекта Modrinth. Если у тебя только slug из публичного URL, поле `id` видно в JSON по адресу `https://api.modrinth.com/v2/project/ТВОЙ-SLUG`.
2. В [настройках токенов Modrinth](https://modrinth.com/settings/pats) создай PAT с правом `VERSION_CREATE`. В GitHub открой **Settings → Secrets and variables → Actions**. Сохрани токен как **secret** `MODRINTH_TOKEN`, а ID проекта как **variable** `MODRINTH_PROJECT_ID`. Токен вводится только в поле секрета.
3. После публикации следующего GitHub Release открой **Actions → Publish to Modrinth → Run workflow**, оставь ветку `main` и введи тег релиза. Сценарий проверит контрольную сумму и версию JAR, затем загрузит файл с примечаниями из GitHub Release. Вкладка **Versions** на Modrinth покажет опубликованную версию.
4. Если версия уже добавлена вручную, пропусти ручной запуск для этого тега. При сбое во время загрузки проверь **Versions** перед повтором.

[GitHub Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
[Modrinth API для публикации версии](https://docs.modrinth.com/api/operations/createversion/)
