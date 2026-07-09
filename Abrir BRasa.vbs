Option Explicit

Dim shell, fso, root, statePath, command, stopCommand, loadingPage

Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

root = fso.GetParentFolderName(WScript.ScriptFullName)
statePath = fso.BuildPath(root, ".brasa-server.json")

stopCommand = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File " & Quote(fso.BuildPath(root, "scripts\stop-brasa.ps1"))
shell.Run stopCommand, 0, True

command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File " & Quote(fso.BuildPath(root, "scripts\start-brasa.ps1"))
shell.Run command, 0, False

loadingPage = fso.BuildPath(root, "loading.html")
shell.Run Quote(loadingPage), 1, False

Function Quote(value)
    Quote = Chr(34) & value & Chr(34)
End Function
