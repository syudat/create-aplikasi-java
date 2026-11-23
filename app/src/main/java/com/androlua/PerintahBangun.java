package com.androlua;

import java.io.File;

/**
 * Perintah "Bangun di Terminal" (Tahap 4).
 *
 * Murni Java (tanpa API Android) supaya bisa diuji lewat JRE.
 * Aturan (sesuai temuan Tahap 3H — proyek pengguna TIDAK punya
 * gradlew/build.gradle/settings.gradle, karena proyek dibuat dari
 * template IDE, bukan dari template Gradle):
 *  - ada gradlew          -> cd "<folder>" && ./gradlew assembleDebug
 *  - ada settings.gradle  -> cd "<folder>" && gradle assembleDebug
 *  - tidak ada keduanya   -> cd "<folder>" saja, beserta petunjuk jujur
 *    bahwa proyek ini tidak punya berkas Gradle (bangun lewat menu
 *    "Bangun proyek" di Kelola Project, atau lewat GitHub Actions).
 */
public class PerintahBangun {
    private PerintahBangun() {
    }

    /** Hasil perhitungan perintah bangun untuk satu folder proyek. */
    public static class Hasil {
        /** Perintah yang sudah diisi di Terminal (selalu diawali cd). */
        public final String perintah;
        /** true bila ada gradlew atau settings.gradle. */
        public final boolean adaGradle;
        /** Petunjuk tambahan (kosong bila adaGradle). */
        public final String petunjuk;

        public Hasil(String perintah, boolean adaGradle, String petunjuk) {
            this.perintah = perintah;
            this.adaGradle = adaGradle;
            this.petunjuk = petunjuk;
        }
    }

    /** Hitung perintah bangun untuk folder proyek. */
    public static Hasil untuk(File folderProyek) {
        String folder = folderProyek == null ? "" : folderProyek.getAbsolutePath();
        String cd = "cd \"" + folder + "\"";
        if (folderProyek != null && new File(folderProyek, "gradlew").exists()) {
            return new Hasil(cd + " && ./gradlew assembleDebug", true, "");
        }
        if (folderProyek != null && new File(folderProyek, "settings.gradle").exists()) {
            return new Hasil(cd + " && gradle assembleDebug", true, "");
        }
        return new Hasil(cd,
                false,
                TeksUI.t("Proyek ini tidak punya berkas Gradle (gradlew/settings.gradle), ")
                        + TeksUI.t("jadi perintah bangun Gradle tidak bisa dijalankan di sini. ")
                        + TeksUI.t("Bangun proyek lewat menu \"Bangun proyek\" di Kelola Project, ")
                        + TeksUI.t("atau lewat GitHub Actions."));
    }

    /** true bila folder proyek punya gradlew atau settings.gradle. */
    public static boolean adaGradle(File folderProyek) {
        if (folderProyek == null) return false;
        return new File(folderProyek, "gradlew").exists()
                || new File(folderProyek, "settings.gradle").exists();
    }
}
