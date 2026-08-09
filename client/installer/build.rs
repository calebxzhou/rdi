fn main() {
    println!("cargo:rerun-if-changed=app.rc");
    println!("cargo:rerun-if-changed=app.manifest");
    println!("cargo:rerun-if-changed=assets/client.tar.zst");
    println!("cargo:rerun-if-changed=../ui/assets/src/main/resources/icon.ico");

    if std::env::var_os("CARGO_CFG_WINDOWS").is_some() {
        embed_resource::compile("app.rc", embed_resource::NONE)
            .manifest_required()
            .expect("编译Windows资源失败");
    }
}
