package com.androlua;

/**
 * Pemantau Kinerja Ringan (seperti Profiler di Android Studio, versi
 * ringan) — logika murni tanpa Android, bisa diuji di JVM.
 *
 * Berisi: pengurai /proc/<pid>/stat untuk waktu CPU proses, hitungan
 * persen CPU antar dua contoh, dan penampung statistik contoh
 * (rata-rata / puncak RAM dan CPU) beserta laporan teksnya. Pengambilan
 * datanya (ActivityManager + /proc) ada di KinerjaActivity.
 */
public class Kinerja {

    /**
     * Mengurai isi /proc/<pid>/stat menjadi {utime, stime} (satuan jiffy).
     * Nama proses di dalam kurung bisa mengandung spasi dan kurung, jadi
     * penguraian mulai sesudah kurung tutup terakhir. Null bila gagal.
     */
    public static long[] uraiStat(String isi) {
        if (isi == null) return null;
        int tutup = isi.lastIndexOf(')');
        if (tutup < 0 || tutup + 2 >= isi.length()) return null;
        String[] f = isi.substring(tutup + 2).trim().split("\\s+");
        // f[0] = field 3 (state); utime = field 14 -> f[11]; stime = field 15 -> f[12].
        if (f.length < 13) return null;
        try {
            return new long[]{Long.parseLong(f[11]), Long.parseLong(f[12])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Persen CPU antara dua contoh: (selisih jiffy / hz) dibanding
     * selisih waktu dinding, dinormalkan ke jumlah inti (100% = semua
     * inti sibuk penuh). -1 bila data tidak cukup.
     */
    public static double persenCpu(long jiffySebelum, long jiffySesudah,
                                   long dindingMsSebelum, long dindingMsSesudah,
                                   int hz, int jumlahInti) {
        if (hz <= 0 || jumlahInti <= 0) return -1;
        long dj = jiffySesudah - jiffySebelum;
        long dw = dindingMsSesudah - dindingMsSebelum;
        if (dj < 0 || dw <= 0) return -1;
        double detikCpu = dj / (double) hz;
        double detikDinding = dw / 1000.0;
        return detikCpu / detikDinding / jumlahInti * 100.0;
    }

    /** Format KB menjadi teks ramah (B / KB / MB). */
    public static String formatKB(long kb) {
        if (kb < 1024) return kb + " KB";
        return String.format("%.1f MB", kb / 1024.0);
    }

    /** Penampung contoh pemantauan: menghitung ringkasan RAM dan CPU. */
    public static class Catatan {
        public int contoh;
        public long pssTerakhirKB = -1;
        public long pssPuncakKB = -1;
        private long pssTotal;
        private int pssContoh;
        public double cpuTerakhir = -1;
        public double cpuPuncak = -1;
        private double cpuTotal;
        private int cpuContoh;

        public void tambah(long pssKB, double cpuPersen) {
            contoh++;
            if (pssKB >= 0) {
                pssTerakhirKB = pssKB;
                pssTotal += pssKB;
                pssContoh++;
                if (pssKB > pssPuncakKB) pssPuncakKB = pssKB;
            }
            if (cpuPersen >= 0) {
                cpuTerakhir = cpuPersen;
                cpuTotal += cpuPersen;
                cpuContoh++;
                if (cpuPersen > cpuPuncak) cpuPuncak = cpuPersen;
            }
        }

        public long pssRataKB() {
            return pssContoh == 0 ? -1 : pssTotal / pssContoh;
        }

        public double cpuRata() {
            return cpuContoh == 0 ? -1 : cpuTotal / cpuContoh;
        }
    }

    /** Laporan teks satu contoh berjalan. */
    public static String laporanContoh(String paket, int pid, Catatan c,
                                       long ramTotalKB, long ramBebasKB) {
        StringBuilder b = new StringBuilder();
        b.append("Memantau ").append(paket).append(" (pid ").append(pid).append(")\n");
        b.append(TeksUI.t("Contoh ke-")).append(c.contoh).append('\n');
        if (c.pssTerakhirKB >= 0) {
            b.append(TeksUI.t("RAM aplikasi (PSS): ")).append(formatKB(c.pssTerakhirKB)).append('\n');
        } else {
            b.append("RAM aplikasi: tidak terbaca\n");
        }
        if (c.cpuTerakhir >= 0) {
            b.append(TeksUI.t("CPU aplikasi: ")).append(String.format("%.1f", c.cpuTerakhir)).append("%\n");
        } else {
            b.append("CPU aplikasi: tidak terbaca di Android versi ini\n");
        }
        if (ramTotalKB > 0) {
            b.append("RAM HP: total ").append(formatKB(ramTotalKB))
             .append(", bebas ").append(formatKB(ramBebasKB)).append('\n');
        }
        return b.toString();
    }

    /** Laporan ringkasan sesudah pemantauan berhenti. */
    public static String laporanRingkasan(String paket, Catatan c) {
        StringBuilder b = new StringBuilder();
        b.append("Ringkasan pemantauan ").append(paket).append(":\n");
        b.append(c.contoh).append(" contoh tercatat.\n");
        if (c.pssRataKB() >= 0) {
            b.append("RAM (PSS) rata-rata ").append(formatKB(c.pssRataKB()))
             .append(", puncak ").append(formatKB(c.pssPuncakKB))
             .append(", terakhir ").append(formatKB(c.pssTerakhirKB)).append(".\n");
        } else {
            b.append("RAM aplikasi tidak terbaca selama pemantauan.\n");
        }
        if (c.cpuRata() >= 0) {
            b.append("CPU rata-rata ").append(String.format("%.1f", c.cpuRata()))
             .append("%, puncak ").append(String.format("%.1f", c.cpuPuncak)).append("%.\n");
        } else {
            b.append("CPU tidak terbaca di Android versi ini (akses /proc proses lain dibatasi).\n");
        }
        return b.toString();
    }
}
