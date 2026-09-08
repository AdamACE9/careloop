@rem
@rem CareLoop Gradle wrapper (Windows).
@rem
@rem Compact by design - see the comment block in ./gradlew for why.
@rem
@rem NOTE (see CLAUDE.md section 9): if you hit
@rem   "Unable to establish loopback connection"
@rem set JAVA_HOME to a JDK 21 (Android Studio ships one at <studio>\jbr),
@rem or simply build from inside Android Studio.
@rem
@echo off
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set WRAPPER_JAR=%DIRNAME%gradle\wrapper\gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (
    echo ERROR: "%WRAPPER_JAR%" not found. 1>&2
    exit /b 1
)

if defined JAVA_HOME (
    set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
    set JAVACMD=java.exe
)

"%JAVACMD%" %JAVA_OPTS% %GRADLE_OPTS% -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
set EXIT_CODE=%ERRORLEVEL%

endlocal & exit /b %EXIT_CODE%
