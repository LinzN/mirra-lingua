; Parla setup for Windows: per-user install (no admin rights), Start menu and desktop shortcut, uninstaller.
; Built by scripts/build-windows-installer.sh:
;   makensis -DVERSION=1.3.0 -DAPPDIR=<staged app> -DICON=parla.ico -DOUTFILE=<setup.exe> installer.nsi
; APPDIR contains Parla.exe, lib\ (app jar + libraries) and runtime\ (Java runtime).

Unicode true
SetCompressor /SOLID lzma
ManifestDPIAware true

!include "MUI2.nsh"

!define APP_NAME "Parla"
!define PUBLISHER "MirraNET"
!define UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\Parla"

Name "${APP_NAME}"
OutFile "${OUTFILE}"
RequestExecutionLevel user
InstallDir "$LOCALAPPDATA\Programs\Parla"
; Remember the folder of an earlier install, so updates go to the same place.
InstallDirRegKey HKCU "${UNINSTALL_KEY}" "InstallLocation"

VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "${APP_NAME}"
VIAddVersionKey "FileDescription" "${APP_NAME} Setup"
VIAddVersionKey "CompanyName" "${PUBLISHER}"
VIAddVersionKey "LegalCopyright" "© ${PUBLISHER}"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "ProductVersion" "${VERSION}"

!define MUI_ICON "${ICON}"
!define MUI_UNICON "${ICON}"
!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\Parla.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Parla jetzt starten"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "German"

Function .onInit
  ; Parla must not run while its files are replaced.
  FindWindow $0 "" "Parla"
  StrCmp $0 0 done
    MessageBox MB_OKCANCEL|MB_ICONEXCLAMATION "Parla läuft gerade. Bitte schließe Parla und klicke dann auf OK." IDOK done
    Abort
  done:
FunctionEnd

Section "Parla" SecMain
  SectionIn RO

  ; Updates: remove the files of the previous version first, so no old libraries are left behind.
  ; User data lives in %APPDATA%\Parla and is not touched.
  RMDir /r "$INSTDIR\lib"
  RMDir /r "$INSTDIR\runtime"

  SetOutPath "$INSTDIR"
  File /r "${APPDIR}\*.*"
  File "/oname=parla.ico" "${ICON}"
  WriteUninstaller "$INSTDIR\Uninstall.exe"

  ; Shortcuts
  CreateDirectory "$SMPROGRAMS\Parla"
  CreateShortcut "$SMPROGRAMS\Parla\Parla.lnk" "$INSTDIR\Parla.exe" "" "$INSTDIR\parla.ico"
  CreateShortcut "$DESKTOP\Parla.lnk" "$INSTDIR\Parla.exe" "" "$INSTDIR\parla.ico"

  ; Entry in "Apps & Features"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayName" "${APP_NAME}"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "Publisher" "${PUBLISHER}"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayIcon" "$INSTDIR\parla.ico"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegStr HKCU "${UNINSTALL_KEY}" "QuietUninstallString" '"$INSTDIR\Uninstall.exe" /S'
  WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoRepair" 1
  WriteRegDWORD HKCU "${UNINSTALL_KEY}" "EstimatedSize" ${SIZE_KB}
SectionEnd

Section "Uninstall"
  Delete "$DESKTOP\Parla.lnk"
  RMDir /r "$SMPROGRAMS\Parla"
  RMDir /r "$INSTDIR\lib"
  RMDir /r "$INSTDIR\runtime"
  Delete "$INSTDIR\Parla.exe"
  Delete "$INSTDIR\parla.ico"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
  DeleteRegKey HKCU "${UNINSTALL_KEY}"
  ; Learning progress and login (%APPDATA%\Parla) stay, so a reinstall picks up where the user left off.
SectionEnd
