Option Explicit

Dim shell, fso, root, launcherPath, statePath, command, exitCode
Dim port, address, message, silentMode, argument

Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

root = fso.GetParentFolderName(WScript.ScriptFullName)
launcherPath = fso.BuildPath(root, "scripts\start-brasa-network.ps1")
statePath = fso.BuildPath(root, ".brasa-server.json")
silentMode = False

For Each argument In WScript.Arguments
    If LCase(CStr(argument)) = "/silent" Then silentMode = True
Next

If Not fso.FileExists(launcherPath) Then
    Finish "O inicializador do servidor nao foi encontrado.", 16, 1
End If

' O processo e executado oculto. O script reutiliza um servidor existente,
' habilita o acesso pela rede e evita criar processos duplicados.
command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File " & Quote(launcherPath)
exitCode = shell.Run(command, 0, True)

If exitCode <> 0 Then
    Finish "Nao foi possivel iniciar o servidor ou a busca de titulos. Consulte data\brasa-launcher.log.", 16, exitCode
End If

port = ReadStateNumber(statePath, "port")
If port <= 0 Then
    Finish "O servidor iniciou, mas a porta de acesso nao foi identificada.", 16, 2
End If

If Not WaitForServer(port, 20) Then
    Finish "O servidor demorou demais para responder. Tente novamente em alguns segundos.", 48, 3
End If

address = GetLanAddress()
If address = "" Then address = "IP deste computador"

message = "Servidor do BRasa TV pronto." & vbCrLf & vbCrLf & _
          "Busca de novos titulos iniciada em segundo plano." & vbCrLf & _
          "Na TV, Configuracoes > Buscar novos titulos permite buscar e atualizar o catalogo." & vbCrLf & vbCrLf & _
          "Agora abra o aplicativo BRasa na TV." & vbCrLf & _
          "Endereco: http://" & address & ":" & port & vbCrLf & vbCrLf & _
          "Mantenha este computador ligado enquanto estiver assistindo."

Finish message, 64, 0

Function ReadStateNumber(filePath, propertyName)
    Dim file, text, expression, matches
    ReadStateNumber = 0
    If Not fso.FileExists(filePath) Then Exit Function

    On Error Resume Next
    Set file = fso.OpenTextFile(filePath, 1, False)
    text = file.ReadAll
    file.Close
    If Err.Number <> 0 Then
        Err.Clear
        Exit Function
    End If
    On Error GoTo 0

    Set expression = New RegExp
    expression.Pattern = Chr(34) & propertyName & Chr(34) & "\s*:\s*(\d+)"
    expression.IgnoreCase = True
    Set matches = expression.Execute(text)
    If matches.Count > 0 Then ReadStateNumber = CLng(matches(0).SubMatches(0))
End Function

Function WaitForServer(serverPort, timeoutSeconds)
    Dim started, request
    WaitForServer = False
    started = Timer

    Do
        On Error Resume Next
        Set request = CreateObject("MSXML2.ServerXMLHTTP.6.0")
        request.setTimeouts 1000, 1000, 1000, 1000
        request.Open "GET", "http://127.0.0.1:" & serverPort & "/", False
        request.Send
        If Err.Number = 0 And request.Status >= 200 And request.Status < 400 Then
            WaitForServer = True
            On Error GoTo 0
            Exit Function
        End If
        Err.Clear
        On Error GoTo 0

        WScript.Sleep 500
    Loop While ElapsedSeconds(started) < timeoutSeconds
End Function

Function GetLanAddress()
    Dim service, adapters, adapter, candidate
    GetLanAddress = ""

    On Error Resume Next
    Set service = GetObject("winmgmts:\\.\root\cimv2")
    Set adapters = service.ExecQuery("Select IPAddress From Win32_NetworkAdapterConfiguration Where IPEnabled=True")

    If Err.Number = 0 Then
        For Each adapter In adapters
            If Not IsNull(adapter.IPAddress) Then
                For Each candidate In adapter.IPAddress
                    If IsLanAddress(candidate) Then
                        GetLanAddress = candidate
                        Exit Function
                    End If
                Next
            End If
        Next
    End If

    Err.Clear
    On Error GoTo 0
End Function

Function IsLanAddress(value)
    Dim expression
    Set expression = New RegExp
    expression.Pattern = "^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)"
    IsLanAddress = expression.Test(CStr(value))
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

Sub Finish(text, icon, code)
    If silentMode Then
        WScript.Echo text
    Else
        MsgBox text, icon, "BRasa TV"
    End If
    WScript.Quit code
End Sub
