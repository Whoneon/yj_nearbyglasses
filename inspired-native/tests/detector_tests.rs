use std::collections::HashSet;

use nearby_glasses_native::{
    company_key_for, format_company_id, parse_company_ids_csv, Advertisement, CompanyKey,
    DetectionReason, Detector, META_COMPANY_ID_1, SNAP_COMPANY_ID,
};

#[test]
fn matches_known_company_id() {
    let detector = Detector::new(-80, HashSet::new());
    let adv = Advertisement {
        timestamp_ms: 0,
        device_address: "AA:BB:CC:DD:EE:FF".into(),
        device_name: None,
        rssi: -60,
        company_id: Some(META_COMPANY_ID_1),
        manufacturer_data_hex: None,
    };

    let decision = detector.evaluate(&adv);
    assert!(decision.matched);
    assert!(decision.reasons.iter().any(|r| matches!(
        r,
        DetectionReason::CompanyIdMatch {
            company_key: CompanyKey::Meta,
            ..
        }
    )));
}

#[test]
fn matches_override_company_id() {
    let detector = Detector::new(-80, [0x1234].into_iter().collect());
    let adv = Advertisement {
        timestamp_ms: 0,
        device_address: "AA".into(),
        device_name: Some("Headset".into()),
        rssi: -40,
        company_id: Some(0x1234),
        manufacturer_data_hex: None,
    };

    let decision = detector.evaluate(&adv);
    assert!(decision.matched);
    assert!(decision.reasons.iter().any(|r| matches!(
        r,
        DetectionReason::OverrideCompanyIdMatch { company_id: 0x1234 }
    )));
}

#[test]
fn filters_below_rssi_threshold() {
    let detector = Detector::new(-75, HashSet::new());
    let adv = Advertisement {
        timestamp_ms: 0,
        device_address: "AA".into(),
        device_name: Some("Ray-Ban".into()),
        rssi: -90,
        company_id: Some(SNAP_COMPANY_ID),
        manufacturer_data_hex: None,
    };

    let decision = detector.evaluate(&adv);
    assert!(!decision.matched);
}

#[test]
fn parses_company_ids_csv() {
    let ids = parse_company_ids_csv("0x01AB,0x058E,427").unwrap();
    assert!(ids.contains(&0x01AB));
    assert!(ids.contains(&0x058E));
    assert!(ids.contains(&427));
}

#[test]
fn formatting_helpers_work() {
    assert_eq!(format_company_id(0x03C2), "0x03C2");
    assert_eq!(company_key_for(SNAP_COMPANY_ID), Some(CompanyKey::Snap));
}
