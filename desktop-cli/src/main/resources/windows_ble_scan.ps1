# Emits one compact JSON line per BLE advertisement.

Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementWatcher, Windows.Devices.Bluetooth, ContentType=WindowsRuntime]
$null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType=WindowsRuntime]

$watcher = [Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementWatcher]::new()
$watcher.ScanningMode = [Windows.Devices.Bluetooth.Advertisement.BluetoothLEScanningMode]::Active

$watcher.add_Received({
    param($sender, $eventArgs)

    try {
        $addressRaw = "{0:X12}" -f $eventArgs.BluetoothAddress
        $address = ($addressRaw -replace '..(?!$)', '$0:').TrimEnd(':')

        $name = $eventArgs.Advertisement.LocalName
        if ([string]::IsNullOrWhiteSpace($name)) {
            $name = $null
        }

        $rssi = [int]$eventArgs.RawSignalStrengthInDBm
        $companyId = $null
        $manufacturerDataHex = $null

        if ($eventArgs.Advertisement.ManufacturerData.Count -gt 0) {
            $entry = $eventArgs.Advertisement.ManufacturerData[0]
            $companyId = [int]$entry.CompanyId

            $reader = [Windows.Storage.Streams.DataReader]::FromBuffer($entry.Data)
            $bytes = New-Object byte[] ($entry.Data.Length)
            $reader.ReadBytes($bytes)
            if ($bytes.Length -gt 0) {
                $manufacturerDataHex = ($bytes | ForEach-Object { $_.ToString('X2') }) -join ''
            }
        }

        $payload = [ordered]@{
            deviceAddress = $address
            deviceName = $name
            rssi = $rssi
            companyId = $companyId
            manufacturerDataHex = $manufacturerDataHex
        }

        [Console]::WriteLine(($payload | ConvertTo-Json -Compress))
    }
    catch {
        # skip malformed events
    }
})

$watcher.Start()

while ($true) {
    Start-Sleep -Seconds 1
}
