use cc;
use std::env;

fn main() {
    let target = env::var("TARGET").unwrap();

    let unrar_path = {
        let mut cur_dir = env::current_dir().unwrap();
        cur_dir.push("vendor");
        cur_dir.push("unrar");

        cur_dir
    };

    env::set_var("LIBFLAGS", "-fPIC");
    env::set_var("LDFLAGS", "-pthread");
    //env::set_var("CXX", "i686-linux-android-clang++");
    //env::set_var("CXXSTDLIB", "c++_static");
    env::set_var("CXXFLAGS", "-O2");

    let is_android_targer = target.contains("android");

    let mut cc_build = cc::Build::new();

    {
        let cc_build = cc_build.cpp(true);

        let cc_build = if is_android_targer {
            cc_build.cpp_set_stdlib(None) //"c++_static"
        } else {
            cc_build
        };

        let cc_build = match target.splitn(2, '-').next().unwrap() {
            "x86_64" | "aarch64" => vec![("_LARGEFILE_SOURCE", None)],
            //"i686" | "armv7" => Vec::new(),
            _ => Vec::new(),
        }
            .into_iter()
            .fold(cc_build, |acc, (var, val)| acc.define(var, val));

        let cc_build = cc_build
            .define("_FILE_OFFSET_BITS", Some("64"))
            //.define("_LARGEFILE_SOURCE", None)
            .define("UNIX_TIME_NS", None) //needed for android compilation. unlinks.cpp lutimes Android > 26
            .define("RAR_SMP", None)
            .define("RARDLL", None)
            .file(unrar_path.join("rar.cpp"))
            .file(unrar_path.join("strlist.cpp"))
            .file(unrar_path.join("strfn.cpp"))
            .file(unrar_path.join("pathfn.cpp"))
            .file(unrar_path.join("smallfn.cpp"))
            .file(unrar_path.join("global.cpp"))
            .file(unrar_path.join("file.cpp"))
            .file(unrar_path.join("filefn.cpp"))
            .file(unrar_path.join("filcreat.cpp"))
            .file(unrar_path.join("archive.cpp"))
            .file(unrar_path.join("arcread.cpp"))
            .file(unrar_path.join("unicode.cpp"))
            .file(unrar_path.join("system.cpp"))
            .file(unrar_path.join("isnt.cpp"))
            .file(unrar_path.join("crypt.cpp"))
            .file(unrar_path.join("crc.cpp"))
            .file(unrar_path.join("rawread.cpp"))
            .file(unrar_path.join("encname.cpp"))
            .file(unrar_path.join("resource.cpp"))
            .file(unrar_path.join("match.cpp"))
            .file(unrar_path.join("timefn.cpp"))
            .file(unrar_path.join("rdwrfn.cpp"))
            .file(unrar_path.join("consio.cpp"))
            .file(unrar_path.join("options.cpp"))
            .file(unrar_path.join("errhnd.cpp"))
            .file(unrar_path.join("rarvm.cpp"))
            .file(unrar_path.join("secpassword.cpp"))
            .file(unrar_path.join("rijndael.cpp"))
            .file(unrar_path.join("getbits.cpp"))
            .file(unrar_path.join("sha1.cpp"))
            .file(unrar_path.join("sha256.cpp"))
            .file(unrar_path.join("blake2s.cpp"))
            .file(unrar_path.join("hash.cpp"))
            .file(unrar_path.join("extinfo.cpp"))
            .file(unrar_path.join("extract.cpp"))
            .file(unrar_path.join("volume.cpp"))
            .file(unrar_path.join("list.cpp"))
            .file(unrar_path.join("find.cpp"))
            .file(unrar_path.join("unpack.cpp"))
            .file(unrar_path.join("headers.cpp"))
            .file(unrar_path.join("threadpool.cpp"))
            .file(unrar_path.join("rs16.cpp"))
            .file(unrar_path.join("cmddata.cpp"))
            .file(unrar_path.join("ui.cpp"))
            .file(unrar_path.join("filestr.cpp"))
            .file(unrar_path.join("scantree.cpp"))
            .file(unrar_path.join("dll.cpp"))
            .file(unrar_path.join("qopen.cpp"));
    }

    cc_build.compile("libunrar.a");
}