#!/usr/bin/env python3
"""把预编译的 .so 重排到 16 KB 页对齐（Android 15/16 的 16 KB page size 要求）。

为什么要这个脚本：`libnode.so` 是 nodejs-mobile v18.20.4 的官方预编译产物，构建时
用的是 4 KB 页（PT_LOAD 的 p_align = 0x1000，且段间偏移只按 4 KB 排列）。官方后续
没有再发新版，也没有源码产物可用，所以不能重新链接，只能改文件布局。

做法：ELF 的跨引用全部基于虚拟地址，文件偏移可以被安全地移动。于是：
  1. 按顺序为每个 PT_LOAD 选一个新的文件偏移，使其满足
     `new_offset % 16384 == p_vaddr % 16384`（加载器按页 mmap 的必要条件）；
  2. 段与段之间插入零填充，保证两个不同权限的段不会落在同一个 16 KB 页里
     （否则 mprotect 会让两者互相踩）；
  3. 把 p_align 抬到 16384，并按映射关系同步修正所有 program header 的
     p_offset、section header 的 sh_offset 与 e_shoff。
p_vaddr / p_memsz / 段内容一律不动。

用法：
    python scripts/align-elf-16kb.py androidApp/src/main/jniLibs/arm64-v8a/libnode.so
    # 只检查不写回
    python scripts/align-elf-16kb.py --check <file>
"""
import struct
import sys

PAGE = 16 * 1024
PT_LOAD = 1
PT_DYNAMIC = 2
PT_INTERP = 3
PT_NOTE = 4
PT_PHDR = 6
PT_GNU_EH_FRAME = 0x6474E550
SHT_NOBITS = 8


def align_to_mod(x, mod):
    """不小于 x、且 x % PAGE == mod 的最小值。"""
    return x + ((mod - x) % PAGE)


class Elf:
    def __init__(self, data):
        if data[:4] != b"\x7fELF" or data[4] != 2:
            raise SystemExit("只支持 ELF64")
        self.data = bytearray(data)
        (self.e_type, self.e_machine, _, self.e_entry, self.e_phoff, self.e_shoff,
         self.e_flags, self.e_ehsize, self.e_phentsize, self.e_phnum,
         self.e_shentsize, self.e_shnum, self.e_shstrndx) = struct.unpack_from(
            "<HHIQQQIHHHHHH", self.data, 16)

    # ---- program headers ----
    def phdrs(self):
        out = []
        for i in range(self.e_phnum):
            off = self.e_phoff + i * self.e_phentsize
            ival = struct.unpack_from("<II", self.data, off)
            qval = struct.unpack_from("<QQQQQQ", self.data, off + 8)
            out.append({
                "i": i, "type": ival[0], "flags": ival[1],
                "offset": qval[0], "vaddr": qval[1], "paddr": qval[2],
                "filesz": qval[3], "memsz": qval[4], "align": qval[5],
            })
        return out

    def write_phdr(self, ph):
        off = self.e_phoff + ph["i"] * self.e_phentsize
        struct.pack_into("<II", self.data, off, ph["type"], ph["flags"])
        struct.pack_into("<QQQQQQ", self.data, off + 8,
                         ph["offset"], ph["vaddr"], ph["paddr"],
                         ph["filesz"], ph["memsz"], ph["align"])

    # ---- section headers ----
    def shdrs(self):
        out = []
        for i in range(self.e_shnum):
            off = self.e_shoff + i * self.e_shentsize
            name, stype = struct.unpack_from("<II", self.data, off)
            flags, addr, soff, size = struct.unpack_from("<QQQQ", self.data, off + 8)
            out.append({
                "i": i, "name": name, "type": stype, "flags": flags,
                "addr": addr, "offset": soff, "size": size,
                "shoff_pos": off,
            })
        return out

    def write_shdr_offset(self, sh, new_offset):
        struct.pack_into("<Q", self.data, sh["shoff_pos"] + 24, new_offset)


def realign(path, write=True):
    data = open(path, "rb").read()
    elf = Elf(data)
    phdrs = elf.phdrs()          # 只解析一次：后面写回要用同一批对象
    loads = sorted([p for p in phdrs if p["type"] == PT_LOAD],
                   key=lambda p: p["offset"])

    # 1) 规划布局：为每个 LOAD 算出新的文件偏移（保持 16 KB 同余），
    #    段与段之间留出零填充，避免不同权限的段共用一个 16 KB 页。
    cur = 0
    plan = []             # (old_start, old_end, new_start, kind)
    pending_gap_from = 0
    for ph in loads:
        new_off = align_to_mod(max(ph["offset"], cur), ph["vaddr"] % PAGE)
        plan.append((pending_gap_from, ph["offset"], None, "gap"))
        plan.append((ph["offset"], ph["offset"] + ph["filesz"], new_off, "load"))
        ph["new_offset"] = new_off
        pending_gap_from = ph["offset"] + ph["filesz"]
        cur = new_off + ph["filesz"]
    plan.append((pending_gap_from, len(data), None, "gap"))

    # 2) 先算出每个区间在新文件里的起点（此时还不写内容），这样才能把
    #    program header / section header 的偏移一次性改对，再统一输出。
    shifts = []           # (old_start, old_end, new_start)
    cursor = 0
    for (start, end, new_start, kind) in plan:
        if end <= start:
            continue
        if kind == "load":
            if new_start < cursor:
                new_start += -(-(cursor - new_start) // PAGE) * PAGE
            cursor = new_start
        shifts.append((start, end, cursor))
        cursor += end - start

    def map_off(x):
        for (s, e, n) in shifts:
            if s <= x < e:
                return n + (x - s)
        if shifts:
            s, e, n = shifts[-1]
            return n + (x - s)
        return x

    # 3) 修正 program headers（ELF 头与 phdr 表本身位于第一个 LOAD 内，偏移不变）
    for ph in phdrs:
        if ph["type"] == PT_LOAD:
            ph["offset"] = ph["new_offset"]
            ph["align"] = max(ph["align"], PAGE)
        else:
            ph["offset"] = map_off(ph["offset"])
        elf.write_phdr(ph)

    # 4) 修正 section headers（SHT_NOBITS / 空 section 没有文件内容）
    for sh in elf.shdrs():
        if sh["type"] == SHT_NOBITS or sh["size"] == 0:
            continue
        elf.write_shdr_offset(sh, map_off(sh["offset"]))

    # 5) e_shoff
    struct.pack_into("<Q", elf.data, 0x28, map_off(elf.e_shoff))

    # 6) 输出：从**已修正**的 elf.data 里取内容，否则尾部（section header 表）
    #    会把刚写好的偏移又覆盖回原值。
    total = shifts[-1][2] + (shifts[-1][1] - shifts[-1][0])
    result = bytearray(total)
    for (start, end, new_start, kind) in plan:
        if end <= start:
            continue
        dst = map_off(start)
        result[dst:dst + (end - start)] = elf.data[start:end]

    if write:
        open(path, "wb").write(result)
    return elf, len(data), len(result)


def check(path):
    elf = Elf(open(path, "rb").read())
    ok = True
    for ph in elf.phdrs():
        if ph["type"] != PT_LOAD:
            continue
        congruent = (ph["vaddr"] - ph["offset"]) % PAGE == 0
        good = ph["align"] >= PAGE and congruent
        ok = ok and good
        print("  LOAD off=0x%08x vaddr=0x%08x align=0x%x %s"
              % (ph["offset"], ph["vaddr"], ph["align"], "OK" if good else "BAD"))
    return ok


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args:
        raise SystemExit(__doc__)
    if args[0] == "--check":
        sys.exit(0 if check(args[1]) else 1)
    for path in args:
        elf, before, after = realign(path, write=True)
        print("%s: %d -> %d bytes" % (path, before, after))
        check(path)
