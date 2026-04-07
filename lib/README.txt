Gson (JSON library)
===================
This project expects gson-2.10.1.jar in this folder.

If the JAR is missing, download:
https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar

IntelliJ: File -> Project Structure -> Libraries -> + -> Java -> select gson-2.10.1.jar
Eclipse: Right-click project -> Properties -> Java Build Path -> Libraries -> Add JARs -> lib/gson-2.10.1.jar

Or use scripts/compile.bat and scripts/run.bat from the project root.

JUnit 5 (unit tests)
====================
Place this JAR next to Gson (used by scripts/test.bat):

  junit-platform-console-standalone-1.10.2.jar

Download:
https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar

From project root:
  scripts\compile.bat
  scripts\test.bat

Test sources live under test/java (sibling of src/, same package names as main code).
