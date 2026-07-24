import urllib.request
import os
import tarfile

deb_url = "https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main/pool/main/libc/libcrypt/libcrypt_0.2-6_aarch64.deb"
print("Downloading libcrypt.deb...")
urllib.request.urlretrieve(deb_url, "libcrypt.deb")

print("Extracting data.tar.xz from deb...")
with open("libcrypt.deb", "rb") as f:
    signature = f.read(8)
    while True:
        header = f.read(60)
        if not header: break
        name = header[0:16].decode("ascii").strip()
        size_str = header[48:58].decode("ascii").strip()
        size = int(size_str)
        if name.startswith("data.tar"):
            with open("data.tar.xz", "wb") as out:
                out.write(f.read(size))
            break
        else:
            f.seek(size, os.SEEK_CUR)
            if size % 2 != 0:
                f.seek(1, os.SEEK_CUR)

print("Extracting libcrypt.so from data.tar.xz...")
with tarfile.open("data.tar.xz", "r:xz") as tar:
    for member in tar.getmembers():
        if member.name.endswith("libcrypt.so"):
            f = tar.extractfile(member)
            with open("bundle_libs/libcrypt.so", "wb") as out:
                out.write(f.read())
            print("Successfully extracted libcrypt.so into bundle_libs!")
            break
