@echo off
rem  Removes leftover files of the old web version (Capacitor) from your project folder.
rem  OPTIONAL: the app builds fine without running this. Double-click it from the project root folder
rem  (the folder that contains the "android" folder).
cd /d "%~dp0"
echo This will delete leftover files of the OLD web version in:
echo   %cd%
echo Your notes, birthdays, alarms etc. are NOT stored here, they are on the phone.
echo Close this window to cancel.
pause
rmdir /s /q node_modules 2>nul
rmdir /s /q www 2>nul
del /q package.json package-lock.json capacitor.config.json README-widget-update.md 2>nul
rmdir /s /q android\capacitor-cordova-android-plugins 2>nul
del /q android\capacitor.settings.gradle android\app\capacitor.build.gradle 2>nul
rmdir /s /q android\app\src\main\assets 2>nul
del /q android\app\src\main\java\app\birthdayreminder\MainActivity.java 2>nul
del /q android\app\src\main\java\app\birthdayreminder\AlarmPlugin.java 2>nul
del /q android\app\src\main\java\app\birthdayreminder\WidgetPlugin.java 2>nul
del /q android\app\src\main\java\app\birthdayreminder\NativeSync.kt 2>nul
del /q android\app\src\main\java\app\birthdayreminder\domain\LegacySecrets.kt 2>nul
del /q android\app\src\main\res\layout\activity_main.xml android\app\src\main\res\xml\config.xml android\app\src\main\res\xml\file_paths.xml 2>nul
rmdir /s /q android\app\src\test\java\com\getcapacitor 2>nul
rmdir /s /q android\app\src\androidTest\java\com\getcapacitor 2>nul
rem  old build outputs (regenerated automatically on the next build)
rmdir /s /q android\app\build 2>nul
rmdir /s /q android\build 2>nul
echo Done.
pause
