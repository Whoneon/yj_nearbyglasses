# nearby-glasses-native
Native BLE smart-glasses detector (Rust), inspired by `yj_nearbyglasses`.

## Known Company IDs
- `0x01AB` Meta Platforms
- `0x058E` Meta Platforms Technologies
- `0x0D53` EssilorLuxottica
- `0x03C2` Snap

## Build Requirements
- Rust stable toolchain
- Linux BLE builds: `pkg-config`, `libdbus-1-dev`

## Build
```bash
cargo build
```

## Test and Static Checks
```bash
cargo fmt --all -- --check
cargo test
cargo check --features ble
```

## Runtime
### Deterministic mode (`stdin`)
Consumes one JSON advertisement per line:
```bash
cat <<'JSON' | cargo run -- --scanner stdin --rssi-threshold -75
{"device_address":"AA:BB:CC:DD:EE:FF","device_name":"Ray-Ban Meta","rssi":-60,"company_id":1422}
JSON
```

### Live BLE scan (Linux/Windows)
```bash
cargo run --features ble -- --scanner auto --rssi-threshold -75 --cooldown-ms 10000
```

## CLI Reference
- `--scanner auto|linux|windows|stdin`
- `--rssi-threshold <i16>` (default: `-75`)
- `--cooldown-ms <u64>` (default: `10000`)
- `--override-company-ids <csv>` (`0x01AB,0x058E,0x0D53`)
- `--debug`

## License
AGPL-3.0

## Attribution
Inspired by `yj_nearbyglasses`.
