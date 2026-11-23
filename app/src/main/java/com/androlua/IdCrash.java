package com.androlua;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Pembuat ID crash unik (kelas murni tanpa Android, bisa diuji di JVM).
 * Format: CR-<yyyyMMdd>-<HHmmss>-<6 hex acak>, contoh CR-20261123-141500-1A2B3C.
 * Setiap kejadian crash mendapat ID berbeda lewat gabungan detik kejadian
 * dan nilai acak 24-bit.
 */
public class IdCrash {

    public static String buat(long waktuMs, int acak) {
        String waktu = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(waktuMs));
        return "CR-" + waktu + "-" + String.format("%06X", acak & 0xFFFFFF);
    }
}
