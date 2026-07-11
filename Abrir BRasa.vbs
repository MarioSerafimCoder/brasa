Option Explicit

Dim shell, fso, root, statePath, nodeScript, command, windowStyle
Dim pid, port, started, debugMode, index

Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
root = fso.GetParentFolderName(WScript.ScriptFullName)
statePath = fso.BuildPath(root, ".brasa-server.json")
nodeScript = fso.BuildPath(root, "scripts\brasa-server.mjs")
debugMode = False

For index = 0 To WScript.Arguments.Count - 1
    If LCase(WScript.Arguments(index)) = "/debug" Then debugMode = True
Next

If shell.Run("cmd.exe /d /c where node >nul 2>&1", 0, True) <> 0 Then
    MsgBox "O Node.js não foi encontrado. Instale o Node.js e tente novamente.", 16, "BRasa"
    WScript.Quit 1
End If

pid = ReadStateNumber(statePath, "pid")
port = ReadStateNumber(statePath, "port")

If pid > 0 And IsProcessRunning(pid) And port > 0 Then
    shell.Run "http://127.0.0.1:" & port & "/loading.html?existing=1", 1, False
    WScript.Quit 0
End If

If fso.FileExists(statePath) Then
    On Error Resume Next
    fso.DeleteFile statePath, True
    On Error GoTo 0
End If

windowStyle = 0
If debugMode Then windowStyle = 1
command = "cmd.exe /d /c cd /d " & Quote(root) & " && node " & Quote(nodeScript)
shell.Run command, windowStyle, False

started = Timer
Do
    WScript.Sleep 200
    port = ReadStateNumber(statePath, "port")
    pid = ReadStateNumber(statePath, "pid")
    If port > 0 And pid > 0 And IsProcessRunning(pid) Then Exit Do
    If ElapsedSeconds(started) > 20 Then
        MsgBox "O BRasa não conseguiu iniciar. Execute Abrir BRasa.vbs /debug para ver o diagnóstico.", 16, "BRasa"
        WScript.Quit 1
    End If
Loop

shell.Run "http://127.0.0.1:" & port & "/loading.html", 1, False

Function ReadStateNumber(filePath, propertyName)
    Dim file, text, expression, matches
    ReadStateNumber = 0
    If Not fso.FileExists(filePath) Then Exit Function
    On Error Resume Next
    Set file = fso.OpenTextFile(filePath, 1, False)
    text = file.ReadAll
    file.Close
    If Err.Number <> 0 Then Err.Clear: Exit Function
    On Error GoTo 0
    Set expression = New RegExp
    expression.Pattern = Chr(34) & propertyName & Chr(34) & "\s*:\s*(\d+)"
    expression.IgnoreCase = True
    Set matches = expression.Execute(text)
    If matches.Count > 0 Then ReadStateNumber = CLng(matches(0).SubMatches(0))
End Function

Function IsProcessRunning(processId)
    Dim service, items
    IsProcessRunning = False
    On Error Resume Next
    Set service = GetObject("winmgmts:\\.\root\cimv2")
    Set items = service.ExecQuery("Select ProcessId From Win32_Process Where ProcessId=" & CLng(processId))
    If Err.Number = 0 Then IsProcessRunning = (items.Count > 0)
    Err.Clear
    On Error GoTo 0
End Function

Function ElapsedSeconds(startValue)
    Dim current
    current = Timer
    If current < startValue Then current = current + 86400
    ElapsedSeconds = current - startValue
End Function

Function Quote(value)
    Quote = Chr(34) & value & Chr(34)
End Function
