# Parla text-to-speech helper for Windows (Windows PowerShell 5.1).
# Started once by the desktop app; talks over stdin/stdout, one line per message:
#   app -> helper:  <language tag>|<slow 0/1>|<text as Base64 UTF-8>     or  EXIT
#   helper -> app:  VOICES <tag,tag,...>   (once at start)
#                   WAV <Base64 of a .wav file>  |  NOVOICE  |  ERR <message>
# The app plays the audio itself, so it can stop it at any time.
# Uses the modern Windows voices (Windows.Media.SpeechSynthesis, the ones installed under
# Settings > Time & Language > Speech) and falls back to the older SAPI voices (System.Speech).

$ErrorActionPreference = 'Stop'
$out = [Console]::Out

$winrt = $null
$asTask = $null
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $null = [Windows.Media.SpeechSynthesis.SpeechSynthesizer, Windows.Media.SpeechSynthesis, ContentType = WindowsRuntime]
    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() |
        Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
                       $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } |
        Select-Object -First 1
    $winrt = New-Object Windows.Media.SpeechSynthesis.SpeechSynthesizer
} catch { $winrt = $null }

$sapi = $null
try {
    Add-Type -AssemblyName System.Speech
    $sapi = New-Object System.Speech.Synthesis.SpeechSynthesizer
} catch { $sapi = $null }

function WinRtVoices { if ($winrt) { @([Windows.Media.SpeechSynthesis.SpeechSynthesizer]::AllVoices) } else { @() } }
function SapiVoices { if ($sapi) { @($sapi.GetInstalledVoices() | Where-Object { $_.Enabled }) } else { @() } }

# Best voice for a tag such as en-GB: exact match first, then any voice of the same language (en-US).
function Pick($voices, $tag, [scriptblock]$lang) {
    $base = $tag.Split('-')[0]
    $exact = $voices | Where-Object { (& $lang $_) -eq $tag } | Select-Object -First 1
    if ($exact) { return $exact }
    return $voices | Where-Object { (& $lang $_) -like "$base-*" -or (& $lang $_) -eq $base } | Select-Object -First 1
}

$langs = @()
$langs += WinRtVoices | ForEach-Object { $_.Language }
$langs += SapiVoices | ForEach-Object { $_.VoiceInfo.Culture.Name }
$out.WriteLine('VOICES ' + (($langs | Select-Object -Unique) -join ','))
$out.Flush()

while ($null -ne ($line = [Console]::In.ReadLine())) {
    if ($line -eq 'EXIT') { break }
    try {
        $p = $line.Split('|')
        $tag = $p[0]
        $slow = $p[1] -eq '1'
        $text = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p[2]))
        $bytes = $null

        $v = Pick (WinRtVoices) $tag { param($x) $x.Language }
        if ($v) {
            $winrt.Voice = $v
            try { $winrt.Options.SpeakingRate = $(if ($slow) { 0.6 } else { 0.95 }) } catch { }
            $task = $asTask.MakeGenericMethod([Windows.Media.SpeechSynthesis.SpeechSynthesisStream]).Invoke(
                $null, @($winrt.SynthesizeTextToStreamAsync($text)))
            $null = $task.Wait(-1)
            $stream = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($task.Result)
            $ms = New-Object System.IO.MemoryStream
            $stream.CopyTo($ms)
            $bytes = $ms.ToArray()
        } else {
            $sv = Pick (SapiVoices) $tag { param($x) $x.VoiceInfo.Culture.Name }
            if ($sv) {
                $sapi.SelectVoice($sv.VoiceInfo.Name)
                $sapi.Rate = $(if ($slow) { -4 } else { 0 })
                $ms = New-Object System.IO.MemoryStream
                $sapi.SetOutputToWaveStream($ms)
                $sapi.Speak($text)
                $sapi.SetOutputToNull()
                $bytes = $ms.ToArray()
            }
        }

        if ($bytes) { $out.WriteLine('WAV ' + [Convert]::ToBase64String($bytes)) } else { $out.WriteLine('NOVOICE') }
    } catch {
        $out.WriteLine('ERR ' + ($_.Exception.Message -replace "[`r`n]+", ' '))
    }
    $out.Flush()
}
