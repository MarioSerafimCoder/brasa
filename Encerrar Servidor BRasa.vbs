Option Explicit

Dim shell, fso, root, scriptPath, command, process, output, errors
Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
root = fso.GetParentFolderName(WScript.ScriptFullName)
scriptPath = fso.BuildPath(root, "scripts\stop-brasa.ps1")
command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File " & Quote(scriptPath)

Set process = shell.Exec(command)
Do While process.Status = 0
    WScript.Sleep 200
Loop
output = Trim(process.StdOut.ReadAll)
errors = Trim(process.StdErr.ReadAll)

If process.ExitCode = 0 Then
    If output = "" Then output = "Servidor BRasa encerrado."
    MsgBox output, 64, "BRasa"
Else
    If errors = "" Then errors = output
    If errors = "" Then errors = "Nao foi possivel encerrar o servidor BRasa."
    MsgBox errors, 16, "BRasa"
End If

Function Quote(value)
    Quote = Chr(34) & value & Chr(34)
End Function
