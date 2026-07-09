Option Explicit

Dim shell, fso, root, command

Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

root = fso.GetParentFolderName(WScript.ScriptFullName)
command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File " & Quote(fso.BuildPath(root, "scripts\stop-brasa.ps1"))

shell.Run command, 1, True

Function Quote(value)
    Quote = Chr(34) & value & Chr(34)
End Function
