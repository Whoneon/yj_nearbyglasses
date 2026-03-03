use std::collections::HashSet;

pub const META_COMPANY_ID_1: u16 = 0x01AB;
pub const META_COMPANY_ID_2: u16 = 0x058E;
pub const ESSILOR_COMPANY_ID: u16 = 0x0D53;
pub const SNAP_COMPANY_ID: u16 = 0x03C2;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CompanyKey {
    Meta,
    Essilor,
    Snap,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum DetectionReason {
    CompanyIdMatch {
        company_id: u16,
        company_key: CompanyKey,
    },
    NameContains {
        token: &'static str,
    },
    OverrideCompanyIdMatch {
        company_id: u16,
    },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Advertisement {
    pub timestamp_ms: u64,
    pub device_address: String,
    pub device_name: Option<String>,
    pub rssi: i16,
    pub company_id: Option<u16>,
    pub manufacturer_data_hex: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DetectionDecision {
    pub matched: bool,
    pub reasons: Vec<DetectionReason>,
}

#[derive(Debug, Clone)]
pub struct Detector {
    rssi_threshold: i16,
    override_company_ids: HashSet<u16>,
}

impl Detector {
    pub fn new(rssi_threshold: i16, override_company_ids: HashSet<u16>) -> Self {
        Self {
            rssi_threshold,
            override_company_ids,
        }
    }

    pub fn evaluate(&self, adv: &Advertisement) -> DetectionDecision {
        if adv.rssi < self.rssi_threshold {
            return DetectionDecision {
                matched: false,
                reasons: vec![],
            };
        }

        let mut reasons = Vec::new();

        if let Some(company_id) = adv.company_id {
            if let Some(company_key) = company_key_for(company_id) {
                reasons.push(DetectionReason::CompanyIdMatch {
                    company_id,
                    company_key,
                });
            }
            if self.override_company_ids.contains(&company_id) {
                reasons.push(DetectionReason::OverrideCompanyIdMatch { company_id });
            }
        }

        if let Some(token) = find_name_token(adv.device_name.as_deref()) {
            reasons.push(DetectionReason::NameContains { token });
        }

        DetectionDecision {
            matched: !reasons.is_empty(),
            reasons,
        }
    }
}

pub fn company_key_for(company_id: u16) -> Option<CompanyKey> {
    match company_id {
        META_COMPANY_ID_1 | META_COMPANY_ID_2 => Some(CompanyKey::Meta),
        ESSILOR_COMPANY_ID => Some(CompanyKey::Essilor),
        SNAP_COMPANY_ID => Some(CompanyKey::Snap),
        _ => None,
    }
}

pub fn format_company_id(company_id: u16) -> String {
    format!("0x{company_id:04X}")
}

pub fn company_name(company_id: Option<u16>) -> String {
    match company_id.and_then(company_key_for) {
        Some(CompanyKey::Meta) => "Meta Platforms".to_string(),
        Some(CompanyKey::Essilor) => "EssilorLuxottica".to_string(),
        Some(CompanyKey::Snap) => "Snap".to_string(),
        None => match company_id {
            Some(id) => format!("Unknown ({})", format_company_id(id)),
            None => "Unknown".to_string(),
        },
    }
}

pub fn format_reasons(reasons: &[DetectionReason]) -> String {
    let mut chunks = Vec::new();
    for reason in reasons {
        let text = match reason {
            DetectionReason::CompanyIdMatch {
                company_id,
                company_key,
            } => match company_key {
                CompanyKey::Meta => format!("Meta company ID ({})", format_company_id(*company_id)),
                CompanyKey::Essilor => {
                    format!(
                        "EssilorLuxottica company ID ({})",
                        format_company_id(*company_id)
                    )
                }
                CompanyKey::Snap => format!("Snap company ID ({})", format_company_id(*company_id)),
            },
            DetectionReason::NameContains { token } => {
                format!("Device name contains '{token}'")
            }
            DetectionReason::OverrideCompanyIdMatch { company_id } => {
                format!(
                    "Override company ID match ({})",
                    format_company_id(*company_id)
                )
            }
        };
        chunks.push(text);
    }
    chunks.join(", ")
}

pub fn parse_company_ids_csv(raw: &str) -> Result<HashSet<u16>, String> {
    if raw.trim().is_empty() {
        return Ok(HashSet::new());
    }

    raw.split(',')
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(parse_company_id)
        .collect::<Result<HashSet<_>, _>>()
}

fn parse_company_id(token: &str) -> Result<u16, String> {
    let lowered = token.to_ascii_lowercase();
    let parsed = if let Some(hex) = lowered.strip_prefix("0x") {
        u16::from_str_radix(hex, 16)
    } else if lowered.chars().all(|c| c.is_ascii_digit()) {
        lowered.parse::<u16>()
    } else {
        u16::from_str_radix(&lowered, 16)
    };

    parsed.map_err(|_| format!("Invalid company ID: {token}"))
}

fn find_name_token(device_name: Option<&str>) -> Option<&'static str> {
    let lower = device_name?.to_ascii_lowercase();
    if lower.contains("rayban") {
        Some("rayban")
    } else if lower.contains("ray-ban") {
        Some("ray-ban")
    } else if lower.contains("ray ban") {
        Some("ray ban")
    } else {
        None
    }
}
