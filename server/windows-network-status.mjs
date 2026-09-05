import { execFile as nodeExecFile } from "node:child_process";
import { promisify } from "node:util";

const execFileAsync = promisify(nodeExecFile);

export function createWindowsNetworkInspector({ platform = process.platform, exec = execFileAsync } = {}) {
    async function inspect(port = 4173) {
        if (platform !== "win32") return unavailable("Informações detalhadas estão disponíveis no Windows.", port);
        try {
            const command = "$c=Get-NetIPConfiguration|Where-Object{$_.IPv4DefaultGateway -and $_.IPv4Address}|Select-Object -First 1; if(-not $c){exit 2}; $a=Get-NetAdapter -InterfaceIndex $c.InterfaceIndex; $p=Get-NetConnectionProfile -InterfaceIndex $c.InterfaceIndex -ErrorAction SilentlyContinue; [pscustomobject]@{name=$c.InterfaceAlias;ip=$c.IPv4Address.IPAddress;prefix=$c.IPv4Address.PrefixLength;gateway=$c.IPv4DefaultGateway.NextHop;mac=$a.MacAddress;speed=[long]$a.LinkSpeed;description=$a.InterfaceDescription;category=[string]$p.NetworkCategory}|ConvertTo-Json -Compress";
            const { stdout } = await exec("powershell.exe", ["-NoProfile", "-NonInteractive", "-Command", command], { timeout: 5000, windowsHide: true });
            const value = JSON.parse(stdout.trim()), type = classifyConnection(value.name, value.description);
            return { available: true, ...value, type, connectionLabel: type === "ethernet" ? "Conectado por Ethernet" : type === "wifi" ? "Conectado por Wi-Fi" : "Conexão não identificada", subnetMask: prefixToMask(Number(value.prefix)), serverUrl: `http://${value.ip}:${port}`, port, dynamicIpWarning: "Confirme no roteador se este IP possui uma reserva DHCP." };
        } catch (error) { return unavailable(`Não foi possível consultar a interface: ${error.code || error.message}`, port); }
    }
    async function firewall(port = 4173) {
        if (platform !== "win32") return { configured: false, supported: false, publicNetwork: false, message: "Firewall gerenciado externamente neste sistema." };
        try {
            const command = `$r=Get-NetFirewallRule -DisplayName 'BRasa Local Server' -ErrorAction SilentlyContinue|Where-Object{$_.Enabled -eq 'True' -and $_.Direction -eq 'Inbound' -and $_.Action -eq 'Allow' -and $_.Profile -match 'Private'}|Select-Object -First 1; $ok=$false;if($r){$pf=$r|Get-NetFirewallPortFilter;$af=$r|Get-NetFirewallAddressFilter;$ok=($pf.Protocol -eq 'TCP' -and [string]$pf.LocalPort -eq '${port}' -and [string]$af.RemoteAddress -match 'LocalSubnet')};$pub=[bool](Get-NetConnectionProfile -ErrorAction SilentlyContinue|Where-Object{$_.IPv4Connectivity -ne 'Disconnected' -and $_.NetworkCategory -eq 'Public'});[pscustomobject]@{configured=$ok;supported=$true;publicNetwork=$pub}|ConvertTo-Json -Compress`;
            const { stdout } = await exec("powershell.exe", ["-NoProfile", "-NonInteractive", "-Command", command], { timeout: 5000, windowsHide: true });
            const value = JSON.parse(stdout.trim());
            return { ...value, message: value.configured ? "Porta limitada à LocalSubnet no perfil privado." : "Execute o script de firewall como administrador." };
        } catch (error) { return { configured: false, supported: true, publicNetwork: false, message: `Não foi possível verificar o firewall: ${error.code || error.message}` }; }
    }
    return { inspect, firewall };
}

export function classifyConnection(name = "", description = "") {
    const value = `${name} ${description}`.toLowerCase();
    if (/wi-?fi|wireless|wlan|802\.11/.test(value)) return "wifi";
    if (/ethernet|gigabit|lan|gbe/.test(value)) return "ethernet";
    return "unknown";
}

export function prefixToMask(prefix) {
    if (!Number.isInteger(prefix) || prefix < 0 || prefix > 32) return "";
    const bits = "1".repeat(prefix).padEnd(32, "0");
    return [0, 8, 16, 24].map((start) => parseInt(bits.slice(start, start + 8), 2)).join(".");
}

function unavailable(message, port) { return { available: false, type: "unknown", connectionLabel: "Conexão não identificada", name: "", ip: "", mac: "", gateway: "", subnetMask: "", speed: 0, category: "", serverUrl: "", port, dynamicIpWarning: "", message }; }
