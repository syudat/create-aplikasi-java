package novan.terminal;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Parser VT (layar terminal) untuk Terminal Tahap 2 — port dari
 * Screen.java hasil dekompilasi (murni Java, tanpa JNI).
 *
 * Memelihara grid layar (baris x kolom) + riwayat gulir (scrollback),
 * memahami escape sequence VT yang dipakai program interaktif
 * (nano/vim/top/htop): CSI posisi kursor, hapus baris/layar, gulir,
 * region, layar alternatif, dan urutan ESC sederhana. Warna (SGR)
 * sengaja TIDAK dirender — grid ini teks saja, karena yang dibaca
 * Jieshuo adalah teks; urutan SGR hanya dikonsumsi lalu diabaikan.
 *
 * Dua perbaikan dari kode hasil dekompilasi (jadx):
 *  - CSI 'H' (CUP, posisi kursor) di kode lama tidak melakukan apa-apa
 *    (case kosong); di sini 'H' berperilaku sama seperti 'f' (HVP).
 *  - CSI 'G' (CHA, kolom absolut) juga kosong di kode lama; di sini
 *    mengatur kolom kursor.
 *  - CSI 'm' (SGR, warna/atribut) ditangani eksplisit: dikonsumsi,
 *    diabaikan.
 *
 * API dibuat public supaya TerminalActivity (paket com.androlua)
 * bisa memakainya — di kode lama semuanya package-private karena
 * MainActivity satu paket dengan Screen.
 */
public class Screen {
    boolean alt;
    int bot;
    int cols;
    int cx;
    int cy;
    char[][] g;
    int rows;
    char[][] saved;
    private int st;
    int sx;
    int sy;
    boolean wrapPending;
    final ArrayList<String> scroll = new ArrayList<>();
    private final StringBuilder par = new StringBuilder();
    int top = 0;

    public Screen(int i, int i2) {
        this.rows = i;
        this.cols = i2;
        this.g = blank(i, i2);
        this.bot = i - 1;
    }

    static char[][] blank(int i, int i2) {
        int[] iArr = {i, i2};
        char[][] cArr = (char[][]) Array.newInstance((Class<?>) Character.TYPE, iArr);
        for (char[] cArr2 : cArr) {
            Arrays.fill(cArr2, ' ');
        }
        return cArr;
    }

    public synchronized void write(String str) {
        for (int i = 0; i < str.length(); i++) {
            ch(str.charAt(i));
        }
    }

    private void ch(char c) {
        int i = this.st;
        if (i == 1) {
            this.st = 0;
            if (c == '[') {
                this.st = 2;
                this.par.setLength(0);
                return;
            }
            if (c == ']') {
                this.st = 3;
                return;
            }
            if (c == '(' || c == ')' || c == '#' || c == '%') {
                this.st = 4;
                return;
            }
            if (c == 'M') {
                int i2 = this.cy;
                if (i2 == this.top) {
                    scrollDown(1);
                    return;
                } else {
                    this.cy = Math.max(0, i2 - 1);
                    return;
                }
            }
            if (c == 'D') {
                lf();
                return;
            }
            if (c == 'E') {
                this.cx = 0;
                lf();
                return;
            }
            if (c == '7') {
                this.sx = this.cx;
                this.sy = this.cy;
                return;
            } else if (c == '8') {
                this.cx = this.sx;
                this.cy = this.sy;
                return;
            } else {
                if (c == 'c') {
                    clearAll();
                    this.cy = 0;
                    this.cx = 0;
                    return;
                }
                return;
            }
        }
        if (i == 2) {
            if (c >= '@' && c <= '~') {
                this.st = 0;
                csi(c, this.par.toString());
                return;
            } else {
                this.par.append(c);
                return;
            }
        }
        if (i == 3) {
            if (c == 7 || c == 27) {
                this.st = c != 27 ? 0 : 1;
                return;
            }
            return;
        }
        if (i == 4) {
            this.st = 0;
            return;
        }
        if (c == 27) {
            this.st = 1;
            return;
        }
        if (c == '\n' || c == 11 || c == '\f') {
            lf();
            return;
        }
        if (c == '\r') {
            this.cx = 0;
            this.wrapPending = false;
            return;
        }
        if (c == '\b') {
            int i3 = this.cx;
            if (i3 > 0) {
                this.cx = i3 - 1;
            }
            this.wrapPending = false;
            return;
        }
        if (c == '\t') {
            this.cx = Math.min(this.cols - 1, ((this.cx / 8) + 1) * 8);
            return;
        }
        if (c < ' ' || c == 127) {
            return;
        }
        if (this.wrapPending) {
            this.cx = 0;
            lf();
            this.wrapPending = false;
        }
        char[] cArr = this.g[this.cy];
        int i4 = this.cx;
        cArr[i4] = c;
        if (i4 < this.cols - 1) {
            this.cx = i4 + 1;
        } else {
            this.wrapPending = true;
        }
    }

    private void lf() {
        int i = this.cy;
        if (i == this.bot) {
            scrollUp(1);
        } else if (i < this.rows - 1) {
            this.cy = i + 1;
        }
    }

    private void scrollUp(int i) {
        int i2;
        for (int i3 = 0; i3 < i; i3++) {
            if (!this.alt && this.top == 0) {
                this.scroll.add(rowText(this.g[0]));
                if (this.scroll.size() > 2000) {
                    this.scroll.subList(0, 500).clear();
                }
            }
            int i4 = this.top;
            i2 = this.bot;
            while (i4 < i2) {
                char[][] cArr = this.g;
                int i5 = i4 + 1;
                cArr[i4] = cArr[i5];
                i4 = i5;
            }
            char[][] cArr2 = this.g;
            char[] cArr3 = new char[this.cols];
            cArr2[i2] = cArr3;
            Arrays.fill(cArr3, ' ');
        }
    }

    private void scrollDown(int i) {
        int i2;
        for (int i3 = 0; i3 < i; i3++) {
            int i4 = this.bot;
            i2 = this.top;
            while (i4 > i2) {
                char[][] cArr = this.g;
                cArr[i4] = cArr[i4 - 1];
                i4--;
            }
            char[][] cArr2 = this.g;
            char[] cArr3 = new char[this.cols];
            cArr2[i2] = cArr3;
            Arrays.fill(cArr3, ' ');
        }
    }

    private static int[] nums(String str, int i) {
        if (str.startsWith("?") || str.startsWith(">")) {
            str = str.substring(1);
        }
        int i2 = 0;
        String[] strArrSplit = str.isEmpty() ? new String[0] : str.split(";", -1);
        int iMax = Math.max(1, strArrSplit.length);
        int[] iArr = new int[iMax];
        while (i2 < iMax) {
            try {
                iArr[i2] = (i2 >= strArrSplit.length || strArrSplit[i2].isEmpty()) ? i : Integer.parseInt(strArrSplit[i2]);
            } catch (Exception unused) {
                iArr[i2] = i;
            }
            i2++;
        }
        return iArr;
    }

    private void clearAll() {
        this.g = blank(this.rows, this.cols);
    }

    /** Mengosongkan layar + riwayat gulir (tombol "Bersihkan"). */
    public synchronized void clear() {
        clearAll();
        this.scroll.clear();
        this.cy = 0;
        this.cx = 0;
        this.top = 0;
        this.bot = this.rows - 1;
        this.wrapPending = false;
    }

    private void eraseRow(int i, int i2, int i3) {
        for (int iMax = Math.max(0, i2); iMax <= Math.min(this.cols - 1, i3); iMax++) {
            this.g[i][iMax] = ' ';
        }
    }

    private void csi(char c, String str) {
        int i;
        int i2;
        this.wrapPending = false;
        boolean zStartsWith = str.startsWith("?");
        int[] iArrNums = nums(str, 0);
        int i3 = iArrNums[0];
        int i4 = i3 <= 0 ? 1 : i3;
        if (c == 'm') {
            // SGR (warna/atribut): dikonsumsi, diabaikan — grid teks saja.
            return;
        }
        if (c == 'H' || c == 'f') {
            // CUP / HVP: posisi kursor (baris;kolom, 1-based).
            // PERBAIKAN Tahap 2: di kode lama 'H' tidak melakukan apa-apa.
            int[] iArrNums3 = nums(str, 1);
            int i18 = this.rows - 1;
            int i19 = iArrNums3[0];
            if (i19 <= 0) {
                i19 = 1;
            }
            this.cy = Math.max(0, Math.min(i18, i19 - 1));
            int i20 = this.cols - 1;
            if (iArrNums3.length <= 1 || (i = iArrNums3[1]) <= 0) {
                i = 1;
            }
            this.cx = Math.max(0, Math.min(i20, i - 1));
            return;
        }
        if (c == 'G') {
            // CHA: kolom absolut (1-based).
            // PERBAIKAN Tahap 2: di kode lama 'G' tidak melakukan apa-apa.
            this.cx = Math.max(0, Math.min(this.cols - 1, i4 - 1));
            return;
        }
        if (c == 'd') {
            // VPA: baris absolut (1-based).
            this.cy = Math.max(0, Math.min(this.rows - 1, i4 - 1));
            return;
        }
        if (c == '`') {
            this.cx = Math.max(0, Math.min(this.cols - 1, i4 - 1));
            return;
        }
        if (c == 'X') {
            int i21 = this.cy;
            int i22 = this.cx;
            eraseRow(i21, i22, (i4 + i22) - 1);
            return;
        }
        if (c == 'P') {
            char[] cArr2 = this.g[this.cy];
            int i23 = this.cx;
            while (true) {
                int i24 = this.cols;
                if (i23 >= i24) {
                    return;
                }
                int i25 = i23 + i4;
                cArr2[i23] = i25 < i24 ? cArr2[i25] : ' ';
                i23++;
            }
        }
        if (c == 'h' || c == 'l') {
            if (zStartsWith) {
                for (int i5 : iArrNums) {
                    if (i5 == 1049 || i5 == 1047 || i5 == 47) {
                        setAlt(c == 'h');
                    }
                }
                return;
            }
            return;
        }
        if (c == 'u') {
            this.cx = this.sx;
            this.cy = this.sy;
            return;
        }
        if (c == 'S') {
            scrollUp(i4);
            return;
        }
        if (c == 'T') {
            scrollDown(i4);
            return;
        }
        if (c == 'r') {
            int[] iArrNums2 = nums(str, 0);
            int i6 = iArrNums2[0];
            if (i6 <= 0) {
                i6 = 1;
            }
            if (iArrNums2.length <= 1 || (i2 = iArrNums2[1]) <= 0) {
                i2 = this.rows;
            }
            if (i6 >= i2 || i2 > this.rows) {
                return;
            }
            int i7 = i6 - 1;
            this.top = i7;
            this.bot = i2 - 1;
            this.cx = 0;
            this.cy = i7;
            return;
        }
        if (c == 's') {
            this.sx = this.cx;
            this.sy = this.cy;
            return;
        }
        switch (c) {
            case '@':
                char[] cArr = this.g[this.cy];
                int i8 = this.cols - 1;
                while (true) {
                    int i9 = this.cx;
                    if (i8 < i9) {
                        break;
                    } else {
                        int i10 = i8 - i4;
                        cArr[i8] = i10 >= i9 ? cArr[i10] : ' ';
                        i8--;
                    }
                }
                return;
            case 'A':
                this.cy = Math.max(this.top, this.cy - i4);
                return;
            case 'B':
                this.cy = Math.min(this.bot, this.cy + i4);
                return;
            case 'C':
                this.cx = Math.min(this.cols - 1, this.cx + i4);
                return;
            case 'D':
                this.cx = Math.max(0, this.cx - i4);
                return;
            case 'E':
                this.cy = Math.min(this.bot, this.cy + i4);
                this.cx = 0;
                return;
            case 'F':
                this.cy = Math.max(this.top, this.cy - i4);
                this.cx = 0;
                return;
            case 'J':
                if (i3 == 0) {
                    eraseRow(this.cy, this.cx, this.cols - 1);
                    for (int i11 = this.cy + 1; i11 < this.rows; i11++) {
                        eraseRow(i11, 0, this.cols - 1);
                    }
                } else if (i3 != 1) {
                    clearAll();
                } else {
                    int i12 = 0;
                    while (true) {
                        int i13 = this.cy;
                        if (i12 >= i13) {
                            eraseRow(i13, 0, this.cx);
                            break;
                        } else {
                            eraseRow(i12, 0, this.cols - 1);
                            i12++;
                        }
                    }
                }
                return;
            case 'K':
                if (i3 == 0) {
                    eraseRow(this.cy, this.cx, this.cols - 1);
                } else if (i3 == 1) {
                    eraseRow(this.cy, 0, this.cx);
                } else {
                    eraseRow(this.cy, 0, this.cols - 1);
                }
                return;
            case 'L':
                int i14 = this.cy;
                int i15 = this.top;
                if (i14 >= i15 && i14 <= this.bot) {
                    this.top = i14;
                    scrollDown(i4);
                    this.top = i15;
                }
                return;
            case 'M':
                int i16 = this.cy;
                int i17 = this.top;
                if (i16 >= i17 && i16 <= this.bot) {
                    this.top = i16;
                    scrollUp(i4);
                    this.top = i17;
                }
                return;
            default:
                return;
        }
    }

    private void setAlt(boolean z) {
        if (z == this.alt) {
            return;
        }
        if (z) {
            this.saved = this.g;
            this.g = blank(this.rows, this.cols);
            this.alt = true;
            this.cy = 0;
            this.cx = 0;
            this.top = 0;
            this.bot = this.rows - 1;
            return;
        }
        char[][] cArr = this.saved;
        if (cArr != null) {
            this.g = cArr;
        }
        this.saved = null;
        this.alt = false;
        this.top = 0;
        this.bot = this.rows - 1;
    }

    static String rowText(char[] cArr) {
        int length = cArr.length;
        while (length > 0 && cArr[length - 1] == ' ') {
            length--;
        }
        return new String(cArr, 0, length);
    }

    public synchronized ArrayList<String> snapshot() {
        ArrayList<String> arrayList;
        arrayList = new ArrayList<>();
        for (char[] cArr : this.g) {
            arrayList.add(rowText(cArr));
        }
        while (!arrayList.isEmpty() && arrayList.get(arrayList.size() - 1).isEmpty()) {
            arrayList.remove(arrayList.size() - 1);
        }
        return arrayList;
    }

    public synchronized String cursorLine() {
        return rowText(this.g[this.cy]);
    }

    /** Salinan riwayat gulir (baris yang sudah tergulir keluar layar). */
    public synchronized ArrayList<String> scrollback() {
        return new ArrayList<>(this.scroll);
    }

    public synchronized int rows() {
        return this.rows;
    }

    public synchronized int cols() {
        return this.cols;
    }

    public synchronized int cursorRow() {
        return this.cy;
    }

    public synchronized int cursorCol() {
        return this.cx;
    }

    public synchronized boolean diLayarAlternatif() {
        return this.alt;
    }
}
