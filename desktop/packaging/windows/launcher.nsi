; Parla.exe - a tiny launcher that starts the app with the bundled Java runtime, without a console window.
; Built by scripts/build-windows-installer.sh:
;   makensis -DVERSION=1.3.0 -DICON=parla.ico -DOUTFILE=Parla.exe launcher.nsi

Unicode true
SetCompressor /SOLID lzma

Name "Parla"
OutFile "${OUTFILE}"
Icon "${ICON}"
RequestExecutionLevel user
SilentInstall silent
AutoCloseWindow true
ShowInstDetails nevershow

VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "Parla"
VIAddVersionKey "FileDescription" "Parla"
VIAddVersionKey "CompanyName" "MirraNET"
VIAddVersionKey "LegalCopyright" "© MirraNET"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "ProductVersion" "${VERSION}"

Section
  ; The launcher lives next to runtime\ and lib\ in the install folder.
  SetOutPath "$EXEDIR"
  IfFileExists "$EXEDIR\runtime\bin\javaw.exe" +3
    MessageBox MB_ICONSTOP "Parla ist unvollständig installiert (Java-Laufzeit fehlt). Bitte installiere Parla neu."
    Abort
  Exec '"$EXEDIR\runtime\bin\javaw.exe" -Dfile.encoding=UTF-8 -cp "$EXEDIR\lib\*" de.mirranet.parla.desktop.MainKt'
SectionEnd
