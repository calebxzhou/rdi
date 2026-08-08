use std::sync::Arc;

pub type MessageCallback = Arc<dyn Fn(String) + Send + Sync>;
