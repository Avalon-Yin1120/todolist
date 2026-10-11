@rem ---------------------------------------------------------------------------
@rem Gradle 启动脚本。
@rem
@rem 本机 gradle.org 的 TLS 被网络策略拦截，标准 gradle-wrapper 机制（下载
@rem distribution、校验、解压）会失败，因此这里直接驱动已解压的 Gradle 发行包。
@rem
@rem AGP 8.9.2 要求 Gradle >= 8.11.1，故使用 D:\Android\gradle-8.11.1
@rem ---------------------------------------------------------------------------
@echo off
setlocal

set "APP_HOME=%~dp0"
set "GRADLE_HOME=D:\Android\gradle-8.11.1"
set "GRADLE_HOME_FALLBACK=D:\Android\gradle-8.9"

if not defined JAVA_HOME set "JAVA_HOME=D:\Download\java"

if exist "%GRADLE_HOME%\bin\gradle.bat" (
  call "%GRADLE_HOME%\bin\gradle.bat" %*
  exit /b %ERRORLEVEL%
)

if exist "%GRADLE_HOME_FALLBACK%\bin\gradle.bat" (
  call "%GRADLE_HOME_FALLBACK%\bin\gradle.bat" %*
  exit /b %ERRORLEVEL%
)

set "WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
if not exist "%WRAPPER_JAR%" (
  echo [ERROR] Gradle distribution not found at %GRADLE_HOME%
  exit /b 1
)
"%JAVA_HOME%\bin\java.exe" -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%