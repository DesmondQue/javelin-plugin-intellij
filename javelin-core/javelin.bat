@echo off
@rem ensure Java 21 is used
setlocal

@rem common locations
if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot"
) else if exist "C:\Program Files\Java\jdk-21" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-21"
) else if exist "C:\Program Files\Eclipse Adoptium\jdk-21" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21"
) else (
    set "JAVA_HOME="
)

call "%~dp0build\install\javelin\bin\javelin.bat" %*

endlocal
