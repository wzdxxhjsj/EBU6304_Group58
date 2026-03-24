|No|Github Name|QMID|
|-|-|-|
|1|Sodacrane|231225395|
|2|Renee959|231225384|
|3|hathaway3499-max|231226897|
|4|wzdxxhjsj|231225421|
|5|bluemore330|231226336|
|6|wuxuan884632-bot|231226303|

## Java app (Swing + JSON)

- **JDK**: 11+ recommended. **Dependency**: `lib/gson-2.10.1.jar` (see `lib/README.txt` if missing).
- **IDE**: Add `lib/gson-2.10.1.jar` to the project classpath; set source root to `src`; run `com.group58.recruit.Main`. **Working directory** must be the project root so `data/` resolves correctly.
- **Command line (Windows)**: from repo root, `scripts\compile.bat` then `scripts\run.bat`.

Root package: `com.group58.recruit` (`config`, `model`, `repository`, `service`, `ui`). Data schema: `database.md` and `data/README.txt` (`users.json`, `profiles.json`, `modules.json`, `applications.json`, `reassign_logs.json`, `data/cvs/`).
