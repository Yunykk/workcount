@ECHO OFF
SET DIRNAME=%~dp0
SET APP_HOME=%DIRNAME%
SET CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

IF NOT EXIST "%CLASSPATH%" (
  ECHO Missing gradle\wrapper\gradle-wrapper.jar
  EXIT /B 1
)

IF DEFINED JAVA_HOME (
  "%JAVA_HOME%\bin\java.exe" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
) ELSE (
  java -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
)
