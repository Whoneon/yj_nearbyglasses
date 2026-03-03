# nearby-glasses-native
Standalone native BLE detector inspired by `yj_nearbyglasses`, without Android dependencies.

## What it is
- Native Rust detector logic (company IDs + name heuristics + RSSI threshold + override company IDs)
- CLI execution
- `stdin` mode for deterministic testing and integration pipelines
- Optional real BLE scanning (`--features ble`) for Linux/Windows through `btleplug`

## Build
```bash
cd inspired-native
cargo build
```

## Run (stdin mode, no BLE needed)
```bash
cat <<'JSON' | cargo run -- --scanner stdin --rssi-threshold -75
{"device_address":"AA:BB:CC:DD:EE:FF","device_name":"Ray-Ban Meta","rssi":-60,"company_id":1422}
JSON
```

## Run real BLE scan (Linux/Windows)
```bash
cargo run --features ble -- --scanner auto --rssi-threshold -75 --cooldown-ms 10000
```

Scanner selection:
- `--scanner auto` picks current platform
- `--scanner linux` forces Linux scanner
- `--scanner windows` forces Windows scanner
- `--scanner stdin` consumes JSON lines from stdin

## Override IDs
```bash
cargo run -- --scanner stdin --override-company-ids 0x01AB,0x058E,0x0D53
```

## Tests
```bash
cargo test
```

## CI and Releases
- CI workflow: `.github/workflows/ci.yml`
  - Runs on `main` pushes and PRs
  - Matrix: Linux + Windows
  - Steps: `fmt`, `test`, `check --features ble`, `build --release --features ble`
- Release workflow: `.github/workflows/release.yml`
  - Trigger: `v*` tag push (example: `v0.1.0`)
  - Builds Linux/Windows binaries and publishes them in GitHub Releases

Tag and release example:
```bash
git tag v0.1.0
git push origin v0.1.0
```

## Make it a separate "inspired" repository
1. Create a new GitHub repository (for example `Whoneon/nearby-glasses-native`).
2. Copy `inspired-native/` as root of the new repository.
3. Keep attribution in README ("inspired by yj_nearbyglasses") and align license notices with your policy.
