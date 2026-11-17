#!/usr/bin/env python3
"""Menyiapkan aapt2 (arm64, dari Termux) sebagai pustaka jniLibs agar bisa dijalankan dari aplikasi.

Pemakaian: siapkan_jnilibs.py FOLDER_alat-unduhan TUJUAN(jniLibs/arm64-v8a atau armeabi-v7a)\nMendukung ELF 64-bit (arm64) dan 32-bit (armv7). Hanya pustaka yang dibutuhkan aapt2 yang disalin.
- bin/aapt2 -> libaapt2.so
- nama pustaka yang tidak berpola lib*.so diganti (libz.so.1 -> libz.so milik sistem,
  libexpat.so.1 -> libexpat_.so); DT_NEEDED & DT_SONAME ditulis ulang di tempat.
"""
import shutil
import struct
import sys
from pathlib import Path

src = Path(sys.argv[1]); dst = Path(sys.argv[2])
RENAME = {"libz.so.1": "libz.so", "libexpat.so.1": "libexpat_.so"}
DROP = {"libz.so.1"}            # pakai libz.so bawaan Android
DT_NEEDED, DT_SONAME, DT_STRTAB = 1, 14, 5


def parse(data):
    """kembalikan (is64, phoff, phentsize, phnum)"""
    assert data[:4] == b"\x7fELF", "bukan ELF"
    if data[4] == 2:
        return True, struct.unpack_from("<Q", data, 0x20)[0], *struct.unpack_from("<HH", data, 0x36)
    return False, struct.unpack_from("<I", data, 0x1C)[0], *struct.unpack_from("<HH", data, 0x2A)


def dynentries(data):
    """daftar entri dinamis [(tag, val, offset_entri)] + fungsi vaddr->offset berkas"""
    is64, phoff, phentsize, phnum = parse(data)
    loads, dyn = [], None
    for i in range(phnum):
        o = phoff + i * phentsize
        if is64:
            p_type, _, p_off, p_vaddr, _, p_filesz = struct.unpack_from("<IIQQQQ", data, o)
        else:
            p_type, p_off, p_vaddr, _, p_filesz = struct.unpack_from("<IIIII", data, o)
        if p_type == 1:
            loads.append((p_vaddr, p_off, p_filesz))
        elif p_type == 2:
            dyn = (p_off, p_filesz)
    if not dyn:
        return [], None

    def v2o(v):
        for va, off, sz in loads:
            if va <= v < va + sz:
                return v - va + off
        raise ValueError("vaddr")

    fmt, step = ("<qQ", 16) if is64 else ("<iI", 8)
    ents, o = [], dyn[0]
    while o < dyn[0] + dyn[1]:
        tag, val = struct.unpack_from(fmt, data, o)
        if tag == 0:
            break
        ents.append((tag, val)); o += step
    return ents, v2o(next(v for t, v in ents if t == DT_STRTAB))


def names(data):
    """[(jenis, nama, offset_awal_string)]"""
    ents, strtab = dynentries(data)
    out = []
    for tag, val in ents:
        if tag in (DT_NEEDED, DT_SONAME):
            s0 = strtab + val
            e = data.index(b"\0", s0)
            out.append((tag, data[s0:e].decode(), s0, e))
    return out


def patch(data: bytearray) -> int:
    n = 0
    for tag, name, s0, e in names(data):
        if name in RENAME:
            new = RENAME[name].encode()
            assert len(new) <= len(name), name
            data[s0:e] = new + b"\0" * (len(name) - len(new))
            n += 1
    return n


# kumpulkan aapt2 + himpunan pustaka yang benar-benar dibutuhkan (rekursif)
SYSTEM = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libz.so", "libandroid.so", "libstdc++.so"}
need, todo = {}, [src / "bin" / "aapt2"]
items = [(src / "bin" / "aapt2", "libaapt2.so")]
while todo:
    f = todo.pop()
    for tag, name, _, _ in names(bytearray(f.read_bytes())):
        if tag != DT_NEEDED or name in need or name in SYSTEM or name in DROP:
            continue
        lib = src / "lib" / name
        if not lib.exists():
            sys.exit("pustaka tidak ditemukan: " + name)
        need[name] = lib
        todo.append(lib)
        items.append((lib, RENAME.get(name, name)))

dst.mkdir(parents=True, exist_ok=True)
total = 0
for f, name in items:
    d = bytearray(f.read_bytes())
    total += patch(d)
    (dst / name).write_bytes(d)
print("berkas:", len(items), "| entri dinamis diubah:", total)
