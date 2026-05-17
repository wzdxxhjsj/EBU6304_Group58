|No|Github Name|QMID|
|-|-|-|
|1|Sodacrane|231225395|
|2|Renee959|231225384|
|3|hathaway3499-max|231226897|
|4|wzdxxhjsj|231225421|
|5|bluemore330|231226336|
|6|wuxuan884632-bot|231226303|

## Java app (JavaFX + JSON)

- **JDK**: 11+ recommended. **Dependency**: `lib/gson-2.10.1.jar` (see `lib/README.txt` if missing).
- **IDE**: Add `lib/*.jar` (Gson, JavaFX, JUnit as needed) to the classpath; set source root to `src`; run `com.group58.recruit.FxMain` or `com.group58.recruit.Main` (delegates to `FxMain`). **Working directory** must be the project root so `data/` resolves correctly.
- **Command line (Windows)**: from repo root, `scripts\run.bat` (compiles, then starts JavaFX `com.group58.recruit.FxMain` with `--module-path lib` — run `scripts\setup-fx-deps.bat` once if JavaFX JARs are missing). Use `scripts\compile.bat` alone if you only need to build.
- **Prepare JavaFX + Ikonli libs (Windows)**: run `scripts\setup-fx-deps.bat` once to download required JARs into `lib\`.

Root package: `com.group58.recruit` (`config`, `model`, `repository`, `service`, `ui`). Data schema: `database.md` and `data/README.txt` (`users.json`, `profiles.json`, `modules.json`, `applications.json`, `reassign_logs.json`, `data/cvs/`).
