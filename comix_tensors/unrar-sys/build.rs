use cc;
use std::env;

fn main() {
    let unrar_src_path = {
        let mut path = env::current_dir().unwrap();

        path.push("vendor");
        path.push("unrar");

        path
    };

    let mut cc_build = cc::Build::new();

    {
        cc_build.cpp(true);

        let target = env::var("TARGET").unwrap();

        if target.contains("android") {
            //add libtrary to the rustc linker
            //println!("cargo:rustc-flags=-l c++abi");

            cc_build.cpp_set_stdlib(None);
        }

        let is_64 = match target.splitn(2, '-').next().unwrap() {
            "x86_64" | "aarch64" => true,
            _ => false,
        };

        if is_64 {
            cc_build.define("_LARGEFILE_SOURCE", None);
        }

        cc_build
            .flag("-pthread")
            .flag("-Wno-logical-op-parentheses")
            .flag("-Wno-switch")
            .flag("-Wno-dangling-else")
            .define("_FILE_OFFSET_BITS", Some("64"))
            .define("RAR_SMP", None)
            .define("RARDLL", None)
            .file(unrar_src_path.join("rar.cpp"))
            .file(unrar_src_path.join("strlist.cpp"))
            .file(unrar_src_path.join("strfn.cpp"))
            .file(unrar_src_path.join("pathfn.cpp"))
            .file(unrar_src_path.join("smallfn.cpp"))
            .file(unrar_src_path.join("global.cpp"))
            .file(unrar_src_path.join("file.cpp"))
            .file(unrar_src_path.join("filefn.cpp"))
            .file(unrar_src_path.join("filcreat.cpp"))
            .file(unrar_src_path.join("archive.cpp"))
            .file(unrar_src_path.join("arcread.cpp"))
            .file(unrar_src_path.join("unicode.cpp"))
            .file(unrar_src_path.join("system.cpp"))
            .file(unrar_src_path.join("isnt.cpp"))
            .file(unrar_src_path.join("crypt.cpp"))
            .file(unrar_src_path.join("crc.cpp"))
            .file(unrar_src_path.join("rawread.cpp"))
            .file(unrar_src_path.join("encname.cpp"))
            .file(unrar_src_path.join("resource.cpp"))
            .file(unrar_src_path.join("match.cpp"))
            .file(unrar_src_path.join("timefn.cpp"))
            .file(unrar_src_path.join("rdwrfn.cpp"))
            .file(unrar_src_path.join("consio.cpp"))
            .file(unrar_src_path.join("options.cpp"))
            .file(unrar_src_path.join("errhnd.cpp"))
            .file(unrar_src_path.join("rarvm.cpp"))
            .file(unrar_src_path.join("secpassword.cpp"))
            .file(unrar_src_path.join("rijndael.cpp"))
            .file(unrar_src_path.join("getbits.cpp"))
            .file(unrar_src_path.join("sha1.cpp"))
            .file(unrar_src_path.join("sha256.cpp"))
            .file(unrar_src_path.join("blake2s.cpp"))
            .file(unrar_src_path.join("hash.cpp"))
            .file(unrar_src_path.join("extinfo.cpp"))
            .file(unrar_src_path.join("extract.cpp"))
            .file(unrar_src_path.join("volume.cpp"))
            .file(unrar_src_path.join("list.cpp"))
            .file(unrar_src_path.join("find.cpp"))
            .file(unrar_src_path.join("unpack.cpp"))
            .file(unrar_src_path.join("headers.cpp"))
            .file(unrar_src_path.join("threadpool.cpp"))
            .file(unrar_src_path.join("rs16.cpp"))
            .file(unrar_src_path.join("cmddata.cpp"))
            .file(unrar_src_path.join("ui.cpp"))
            .file(unrar_src_path.join("filestr.cpp"))
            .file(unrar_src_path.join("scantree.cpp"))
            .file(unrar_src_path.join("dll.cpp"))
            .file(unrar_src_path.join("qopen.cpp"));
    }

    cc_build.compile("unrar");
}
