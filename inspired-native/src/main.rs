use std::collections::HashSet;
use std::io::{self, BufRead};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use anyhow::{anyhow, Context, Result};
use clap::{Parser, ValueEnum};
use nearby_glasses_native::{
    company_name, format_reasons, parse_company_ids_csv, Advertisement, Detector,
};
use serde::Deserialize;

#[derive(Debug, Copy, Clone, Eq, PartialEq, ValueEnum)]
enum ScannerMode {
    Auto,
    Linux,
    Windows,
    Stdin,
}

#[derive(Debug, Parser)]
#[command(name = "nearby-glasses-native")]
#[command(about = "Standalone BLE smart-glasses detector (inspired project)")]
struct Cli {
    #[arg(long, value_enum, default_value_t = ScannerMode::Auto)]
    scanner: ScannerMode,

    #[arg(long, default_value_t = -75, allow_hyphen_values = true)]
    rssi_threshold: i16,

    #[arg(long, default_value_t = 10_000)]
    cooldown_ms: u64,

    #[arg(long, default_value = "")]
    override_company_ids: String,

    #[arg(long, default_value_t = false)]
    debug: bool,
}

#[derive(Debug, Deserialize)]
struct InputAdvertisement {
    device_address: String,
    #[serde(default)]
    device_name: Option<String>,
    rssi: i16,
    #[serde(default)]
    company_id: Option<u16>,
    #[serde(default)]
    manufacturer_data_hex: Option<String>,
    #[serde(default)]
    timestamp_ms: Option<u64>,
}

fn main() -> Result<()> {
    let cli = Cli::parse();
    let overrides = parse_company_ids_csv(&cli.override_company_ids)
        .map_err(|e| anyhow!(e))
        .context("Failed to parse --override-company-ids")?;

    let detector = Detector::new(cli.rssi_threshold, overrides.clone());

    eprintln!(
        "starting scanner mode={:?} rssi_threshold={} cooldown_ms={} override_ids={}",
        cli.scanner,
        cli.rssi_threshold,
        cli.cooldown_ms,
        format_override_ids(&overrides)
    );

    match cli.scanner {
        ScannerMode::Stdin => run_stdin(detector, cli.cooldown_ms, cli.debug),
        ScannerMode::Auto | ScannerMode::Linux | ScannerMode::Windows => {
            #[cfg(feature = "ble")]
            {
                return run_ble(detector, cli.scanner, cli.cooldown_ms, cli.debug);
            }

            #[cfg(not(feature = "ble"))]
            {
                Err(anyhow!(
                    "BLE scanner not enabled in this build. Rebuild with: cargo run --features ble -- --scanner {:?}",
                    cli.scanner
                ))
            }
        }
    }
}

fn run_stdin(detector: Detector, cooldown_ms: u64, debug: bool) -> Result<()> {
    let stdin = io::stdin();
    let mut last_notification_ms = 0_u64;

    for line in stdin.lock().lines() {
        let line = line?;
        if line.trim().is_empty() {
            continue;
        }

        let row: InputAdvertisement =
            serde_json::from_str(&line).with_context(|| format!("Invalid JSON line: {line}"))?;

        let adv = Advertisement {
            timestamp_ms: row.timestamp_ms.unwrap_or_else(now_ms),
            device_address: row.device_address,
            device_name: row.device_name,
            rssi: row.rssi,
            company_id: row.company_id,
            manufacturer_data_hex: row.manufacturer_data_hex,
        };

        evaluate_and_print(
            &detector,
            cooldown_ms,
            &mut last_notification_ms,
            adv,
            debug,
        );
    }

    Ok(())
}

fn evaluate_and_print(
    detector: &Detector,
    cooldown_ms: u64,
    last_notification_ms: &mut u64,
    adv: Advertisement,
    debug: bool,
) {
    let decision = detector.evaluate(&adv);

    if debug {
        eprintln!(
            "debug addr={} name={} rssi={} company_id={:?} matched={}",
            adv.device_address,
            adv.device_name.as_deref().unwrap_or("?"),
            adv.rssi,
            adv.company_id,
            decision.matched
        );
    }

    if !decision.matched {
        return;
    }

    let reason_text = format_reasons(&decision.reasons);
    let device_name = adv.device_name.as_deref().unwrap_or("Unknown device");
    println!(
        "[{}] {} ({} dBm) - {} [{}]",
        format_time(adv.timestamp_ms),
        device_name,
        adv.rssi,
        reason_text,
        company_name(adv.company_id)
    );

    let now = now_ms();
    if now.saturating_sub(*last_notification_ms) < cooldown_ms {
        if debug {
            eprintln!("debug notification suppressed by cooldown");
        }
        return;
    }

    *last_notification_ms = now;
}

fn format_time(timestamp_ms: u64) -> String {
    let secs = timestamp_ms / 1000;
    let h = (secs / 3600) % 24;
    let m = (secs / 60) % 60;
    let s = secs % 60;
    format!("{h:02}:{m:02}:{s:02}")
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or(Duration::from_secs(0))
        .as_millis() as u64
}

fn format_override_ids(ids: &HashSet<u16>) -> String {
    if ids.is_empty() {
        return "<none>".to_string();
    }

    let mut v: Vec<_> = ids.iter().copied().collect();
    v.sort_unstable();
    v.into_iter()
        .map(|id| format!("0x{id:04X}"))
        .collect::<Vec<_>>()
        .join(",")
}

#[cfg(feature = "ble")]
fn run_ble(detector: Detector, scanner: ScannerMode, cooldown_ms: u64, debug: bool) -> Result<()> {
    ble::run(detector, scanner, cooldown_ms, debug)
}

#[cfg(feature = "ble")]
mod ble {
    use std::collections::HashSet;
    use std::thread;
    use std::time::Duration;

    use anyhow::{anyhow, Context, Result};
    use btleplug::api::{Central, Manager as _, Peripheral as _, ScanFilter};
    use btleplug::platform::Manager;

    use crate::{evaluate_and_print, now_ms, Advertisement, Detector, ScannerMode};

    pub fn run(
        detector: Detector,
        scanner: ScannerMode,
        cooldown_ms: u64,
        debug: bool,
    ) -> Result<()> {
        let rt = tokio::runtime::Runtime::new().context("Failed to create tokio runtime")?;
        rt.block_on(run_async(detector, scanner, cooldown_ms, debug))
    }

    async fn run_async(
        detector: Detector,
        scanner: ScannerMode,
        cooldown_ms: u64,
        debug: bool,
    ) -> Result<()> {
        let manager = Manager::new()
            .await
            .context("Failed to create BLE manager")?;
        let adapters = manager
            .adapters()
            .await
            .context("Failed to list adapters")?;
        let adapter = adapters
            .into_iter()
            .next()
            .ok_or_else(|| anyhow!("No BLE adapter found"))?;

        if !scanner_supported(scanner) {
            return Err(anyhow!(
                "Requested scanner mode is incompatible with current OS"
            ));
        }

        adapter
            .start_scan(ScanFilter::default())
            .await
            .context("Failed to start BLE scan")?;

        let mut seen = HashSet::<String>::new();
        let mut last_notification_ms = 0_u64;

        loop {
            let peripherals = adapter
                .peripherals()
                .await
                .context("Failed to list peripherals")?;

            for peripheral in peripherals {
                let id = peripheral.id().to_string();
                let properties = match peripheral.properties().await {
                    Ok(p) => p,
                    Err(_) => None,
                };

                let Some(props) = properties else { continue };
                let Some(rssi) = props.rssi else { continue };

                let company_id = props.manufacturer_data.keys().next().copied();
                let manufacturer_data_hex =
                    props.manufacturer_data.iter().next().map(|(_, bytes)| {
                        bytes.iter().map(|b| format!("{b:02X}")).collect::<String>()
                    });

                let adv = Advertisement {
                    timestamp_ms: now_ms(),
                    device_address: id.clone(),
                    device_name: props.local_name,
                    rssi,
                    company_id,
                    manufacturer_data_hex,
                };

                if debug {
                    eprintln!(
                        "debug adv addr={} rssi={} company_id={:?}",
                        adv.device_address, adv.rssi, adv.company_id
                    );
                }

                if !seen.insert(format!(
                    "{}:{}:{:?}:{:?}",
                    adv.device_address, adv.rssi, adv.company_id, adv.device_name
                )) {
                    continue;
                }

                evaluate_and_print(
                    &detector,
                    cooldown_ms,
                    &mut last_notification_ms,
                    adv,
                    debug,
                );
            }

            thread::sleep(Duration::from_millis(700));
            if seen.len() > 20_000 {
                seen.clear();
            }
        }
    }

    fn scanner_supported(scanner: ScannerMode) -> bool {
        let os = std::env::consts::OS;
        match scanner {
            ScannerMode::Auto => os == "linux" || os == "windows",
            ScannerMode::Linux => os == "linux",
            ScannerMode::Windows => os == "windows",
            ScannerMode::Stdin => true,
        }
    }
}
