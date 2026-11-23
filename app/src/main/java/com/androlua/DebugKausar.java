package com.androlua;

/**
 * DebugKausar — penjaga keutuhan string aplikasi (perintah al kautsar
 * 2026-11-23 20:21 WITA): setiap string antarmuka dan setiap berkas
 * panduan punya nama unik berpola string_kausar_<hex acak> yang memuat
 * nama "kausar". Kelas ini menyimpan sidik jari SHA-256 dari isi
 * setiap string/berkas beserta nama uniknya; bila ADA SATU HURUF SAJA
 * yang berubah — di tabel string (hasil kompilasi/smali) maupun di
 * berkas panduan — sidik jari tidak cocok dan aplikasi menutup
 * dirinya (lihat Aplikasi.onCreate).
 *
 * Gagal AMAN seperti Perisai: berkas panduan yang tidak ada atau
 * tidak bisa dibaca dicatat saja di catatan, BUKAN ketidakcocokan —
 * pengguna tidak dikunci dari aplikasinya sendiri karena kegagalan
 * baca. Yang dianggap dimodifikasi hanya berkas yang ADA dan isinya
 * berubah, atau string tabel yang berubah.
 *
 * Kelas murni tanpa Android (MessageDigest saja), bisa diuji di JVM.
 * JANGAN sunting berkas ini dengan tangan — jalankan gen_debugkausar.py
 * di folder terjemahan-panduan bila ada string/panduan yang berubah.
 * Nama unik dicatat di nama-string-kausar.json supaya bisa diaudit.
 */
public class DebugKausar {

    /** Kode bahasa, urutan sama dengan TeksUI dan katalog Bahasa. */
    public static final String[] KODE = {
            "id",
            "en",
            "es",
            "ar",
            "zh",
            "hi",
            "pt",
            "ru",
            "ja",
            "de",
            "fr",
            "ko",
            "it",
            "tr",
            "vi",
            "th",
            "pl",
            "uk",
            "nl",
            "fa",
            "ur",
            "bn",
            "ta",
            "te",
            "mr",
            "jv",
            "su",
            "ms",
            "fil",
            "sv",
            "no",
            "da",
            "fi",
            "el",
            "cs",
            "ro",
            "hu",
            "he",
            "sw",
            "am",
            "ha",
            "kk",
            "uz",
            "az",
            "my",
            "km",
            "lo",
            "ne",
            "si",
            "mn",
    };

    /** Nama unik tiap kunci antarmuka (indeks sama dengan TeksUI.KUNCI). */
    public static final String[] NAMA_STRING = {
            "string_kausar_905135",
            "string_kausar_c29e78",
            "string_kausar_cd5425",
            "string_kausar_a4030c",
            "string_kausar_8b17f4",
            "string_kausar_43f209",
            "string_kausar_1b9395",
            "string_kausar_e1f16c",
            "string_kausar_22e7ee",
            "string_kausar_cbf234",
            "string_kausar_3ccec0",
            "string_kausar_a03bbe",
            "string_kausar_6a4ffe",
            "string_kausar_cbe46a",
            "string_kausar_5f5c94",
            "string_kausar_de60cb",
            "string_kausar_ea62d9",
            "string_kausar_6dab27",
            "string_kausar_f9f8b7",
            "string_kausar_ee6e2d",
            "string_kausar_adc0b0",
            "string_kausar_a33c59",
            "string_kausar_17af5a",
            "string_kausar_f7183c",
            "string_kausar_a87de3",
            "string_kausar_0992bb",
            "string_kausar_aab839",
            "string_kausar_1f7bf5",
            "string_kausar_262ae6",
            "string_kausar_007812",
            "string_kausar_39fa0e",
            "string_kausar_48fd9b",
            "string_kausar_861fcb",
            "string_kausar_7d4ae3",
            "string_kausar_a76c00",
            "string_kausar_1aa1bc",
            "string_kausar_5a67a6",
            "string_kausar_ce2d6e",
            "string_kausar_ed7e37",
            "string_kausar_9c44c6",
            "string_kausar_0c4a0a",
            "string_kausar_2bceb0",
            "string_kausar_27e290",
            "string_kausar_9321e5",
            "string_kausar_78bd03",
            "string_kausar_c293f3",
            "string_kausar_c6d772",
            "string_kausar_4c797d",
            "string_kausar_13e384",
            "string_kausar_c2c038",
            "string_kausar_8b238b",
            "string_kausar_f9f3e9",
            "string_kausar_7d51af",
            "string_kausar_c45622",
            "string_kausar_f2f8d9",
            "string_kausar_831762",
            "string_kausar_15432b",
            "string_kausar_d61db4",
            "string_kausar_e27999",
            "string_kausar_b8c878",
            "string_kausar_0e5cbc",
            "string_kausar_c37bd9",
            "string_kausar_0104f5",
            "string_kausar_ad7b4f",
            "string_kausar_65523f",
            "string_kausar_d9982c",
            "string_kausar_a184e2",
            "string_kausar_513e9d",
            "string_kausar_c71ceb",
            "string_kausar_3b276b",
            "string_kausar_45e255",
            "string_kausar_049951",
            "string_kausar_54a53a",
            "string_kausar_4e5830",
            "string_kausar_0fd065",
            "string_kausar_37bb79",
            "string_kausar_17ab34",
            "string_kausar_6e018a",
            "string_kausar_232349",
            "string_kausar_319699",
            "string_kausar_1507c1",
            "string_kausar_9f8652",
            "string_kausar_fdcfbd",
            "string_kausar_49aee9",
            "string_kausar_f5c962",
            "string_kausar_ad8df0",
            "string_kausar_fa5c91",
            "string_kausar_76ebbe",
            "string_kausar_3b786b",
            "string_kausar_9304d0",
            "string_kausar_8cf593",
            "string_kausar_06d8ad",
            "string_kausar_d68637",
            "string_kausar_5be0b9",
            "string_kausar_71d6e7",
            "string_kausar_0805c9",
            "string_kausar_b191ed",
            "string_kausar_bf1485",
            "string_kausar_9d825f",
            "string_kausar_8fea09",
            "string_kausar_02ccc5",
            "string_kausar_061e67",
            "string_kausar_82639b",
            "string_kausar_0b5edc",
            "string_kausar_565a28",
            "string_kausar_bb7ce6",
            "string_kausar_7f1f9d",
            "string_kausar_57b447",
            "string_kausar_b88cc1",
            "string_kausar_fe2753",
            "string_kausar_2a1df6",
            "string_kausar_6c2729",
            "string_kausar_5bd4fc",
            "string_kausar_a75e17",
            "string_kausar_f727a2",
            "string_kausar_a2ec7a",
            "string_kausar_eb6ff6",
            "string_kausar_6b8692",
            "string_kausar_e29251",
            "string_kausar_bee089",
            "string_kausar_9ea1b5",
            "string_kausar_f8fac0",
            "string_kausar_d1026c",
            "string_kausar_689e3a",
            "string_kausar_c5c4ea",
            "string_kausar_894bcb",
            "string_kausar_21428f",
            "string_kausar_3b56a2",
            "string_kausar_ecd7fb",
            "string_kausar_26819a",
            "string_kausar_3f1371",
            "string_kausar_b8b31c",
            "string_kausar_2433d3",
            "string_kausar_4fa8ad",
            "string_kausar_b7302b",
            "string_kausar_bed561",
            "string_kausar_e34949",
            "string_kausar_e5aa31",
            "string_kausar_d8a3a9",
            "string_kausar_28d8b0",
            "string_kausar_7c3e41",
            "string_kausar_96ea7f",
            "string_kausar_5bef0f",
            "string_kausar_b13641",
            "string_kausar_8fa5ec",
            "string_kausar_7e0b4d",
            "string_kausar_dd3fd3",
            "string_kausar_a586ad",
            "string_kausar_1c5d31",
            "string_kausar_39b156",
    };

    /** Sidik jari SHA-256 per bahasa (indeks sama dengan KODE). */
    public static final String[] HASH_STRING = {
            "69126692618acef831d003a68b12e8b1eb4711648f959fbdc76f568763c6d8b1",
            "6bebcf3d244eb346bb5056bf1009081ccb8ecf2d200762b0ff3b5c14da3f208f",
            "a3fdc60b68a2d045921527a47008095b96f6149dabe606ba93929fab9e2cc155",
            "5e0321dd7dc525057650470ddd05042602a91a931db8e5d14317045fcc5c8769",
            "05b929860505b032bb56837401b84287b31d2f234edf96cd4c391f4d649ac169",
            "77c7c1d6dfd087d76da2215a0ab37224abaf966a5792ce50e58bc878d002d1f4",
            "ade17315e0d689a4098f5da144c15762fc2e0d5dd97a0ee008678e63dd0cacf1",
            "5e581ed5146c025cd23f9ba85bfdf81e9c693ec58d252138c1800d9877269240",
            "21e74a8170f2f6e0dc3fb8455a541d22655c088c24a8fdf36210f5ff40e7ddb0",
            "b2faea0b5e3f8bd16a1209ae4637ab594d6f4e9d13b59d944b7c83a5bbed2dcf",
            "653cf5442c71acee629654ee07c78b72c3e2ec59d8b00e45663c982f0f9732b7",
            "fca7b0eff6e0a388d431627a2e0d6c1403a2c7f23a8654fb5412463a91eefc6b",
            "7786c0cd79804fd69a4864248577f9b51c153379d2c9978f3b88560690aa9b96",
            "7048d5ca1339d9d2f808746ed6369a564a8756c713f3978ee7e32ace007916c1",
            "dfebd14f1c732162de6b0ec0cc5cd8d201d7fa97403fadc9799114d98da2fb99",
            "cefebf3408cebb90c04e1fb0b06f2c9ecf9a45d09c2217267fd9c1d5cfb3ec2b",
            "27b90e0712b1f6195dcd1f738f77c4cf785c754407afd7f085eb583e09bf1ca9",
            "80d009b07374d5db9341c61fb53fd8030716c1f7a9d36fb2f186a10951d771fe",
            "7897d8d1f497b827dfb1a0a114a88ee28e2e08a6216b38dab8580b1d6434fde0",
            "90764149060151c1d640144acfcad182d5dccbbafa143832159c623b5084e8b2",
            "6c49190b455f12e94ebf581291c0325e80a948fb6958e8a6c414cd26e7fe4037",
            "704a2cdb9acfdb377a77020d2289455b88bca5fa909f0cbde93a6d3c42c72e9c",
            "3121700ab15835d4ee9786dcbd92156c49e73c7e1cf6ffbe2045a8565a6144b0",
            "cb64b62fad58cc4b262a3ead12a187facd5c6a917267a3b12951f41075bd8d86",
            "1cabde14c17064d2fd0d320864a7bbe34d1dc90c78316a6d63d19380c8c120a1",
            "336b843281cdc44eb075ea7fa20a4c2c4382687a9e7a33e3b79bb1c35ce7255f",
            "8e4a88cf8e9d3ef6651d0e43c9b4139cc7cdaf73e833c8bfeeea8c52f165ce76",
            "56c8cc637556be87e3c2e3a024070927dcb43b05ff4081736267684991144f5e",
            "dce587d323c1a66dbd44ab35969193060f3b25fa805ccb377278a203cee74725",
            "921b8e4a2903b554c57d2779b688cc11fa3766bdee5e67f6e82b7481628caa7e",
            "e25a07b1117e309ad4d29d7a9f3a0d12b4dd6f083294620257b83cab4eb526f9",
            "4c382d53f8beaab0870055f8215e22f0a57e6f67d5b7b140c3023dc2eeba34c8",
            "3baec0c00cd79359f6d5dab5706ded30ace5e2108cbd590d5fcb830a7ab91daf",
            "7e982588c5ae68416fbeb57a1099e44f98255deb98d4d22886aca6d6466938f0",
            "a4db48b35e0c98950864c9cdcf80d0329ab19159a123fb52b1dd0bd55535e78d",
            "6615e5c46fef7c141c590b82f4aaf100505d3a0c712edd106eb97863db55527f",
            "2df5540d2000a349fa0de76271a5d888ae2accac5866031bca4bd222bd3edf7f",
            "270914ca85afcc13c4473719268c588b736f8ccb32c6d28b1d354c515c2328ca",
            "fe74c4f3913e6f9c597a4640d7dc06f4c04194b1244ae9779fce311b02725982",
            "bb0c0fc2f2cadd31752eeb467b1f4366a91060e7c8edf626a400b0bc3da99e5f",
            "e40915d014e52ce132008727b14847f4d6e5a1c28ede0df113642ccec004a3e0",
            "d9b5c5a1798b1db1adedb2a48b1d4917ac6f32ff9ff422d9303341574ecc4704",
            "c2760d380050f6a1ee5e6b4742c8ca8f3d3564c2ea71df869711fc311d1fa548",
            "80ebcecc5a215f1f1ce993dfbfe6a00dc680c34e033454f90b573d36e404f3e3",
            "83f602ac84a0c039aba4c8a7920dc66976c5a451b0c981d5344e807687a11681",
            "c0905181d860b276290b0333a152d127297e2ff9d34bef917815dd2d9b210842",
            "ab128c61a72eca306f845ed4323f3914db7fca4349758f8a3662e7f305d84ca4",
            "321fa355a7568d1703cf680fd27de69b716a78877481632df6a5fc8553a021ff",
            "7e39861bb6b0f45d4620aa6d38139a705f5b6fc5c1d427e62e898078b3153e78",
            "6e3241d1ac7374e5ca23b03c7a6ee97e3121fb30ef318b1665a56bb917460084",
    };

    /** Nama unik tiap berkas panduan (indeks sama dengan KODE). */
    public static final String[] NAMA_PANDUAN = {
            "string_kausar_e57b33",
            "string_kausar_d2c25d",
            "string_kausar_8b2bc3",
            "string_kausar_770be2",
            "string_kausar_59dafc",
            "string_kausar_e0fa8b",
            "string_kausar_101117",
            "string_kausar_96d3f3",
            "string_kausar_aee5d9",
            "string_kausar_e9dc18",
            "string_kausar_41033c",
            "string_kausar_fdb756",
            "string_kausar_dbc617",
            "string_kausar_0da457",
            "string_kausar_784696",
            "string_kausar_ca781a",
            "string_kausar_23c904",
            "string_kausar_b88f88",
            "string_kausar_8239c2",
            "string_kausar_7810a6",
            "string_kausar_4f6de9",
            "string_kausar_b3bdcb",
            "string_kausar_2f26f3",
            "string_kausar_9278e1",
            "string_kausar_66c0a2",
            "string_kausar_f0d10c",
            "string_kausar_f01001",
            "string_kausar_63fbbe",
            "string_kausar_af2fb5",
            "string_kausar_6cba8c",
            "string_kausar_c1b649",
            "string_kausar_37ad4d",
            "string_kausar_ac5faa",
            "string_kausar_ee38b8",
            "string_kausar_51c9b2",
            "string_kausar_e8c64e",
            "string_kausar_d58b44",
            "string_kausar_690e39",
            "string_kausar_9450e1",
            "string_kausar_1b7b57",
            "string_kausar_c61388",
            "string_kausar_35d4a2",
            "string_kausar_32caa5",
            "string_kausar_5f0047",
            "string_kausar_011e92",
            "string_kausar_73a84c",
            "string_kausar_08da95",
            "string_kausar_536c60",
            "string_kausar_db3578",
            "string_kausar_71421c",
    };

    /** Sidik jari SHA-256 per berkas panduan (indeks sama dengan KODE). */
    public static final String[] HASH_PANDUAN = {
            "4286700665f9bcaec54735f24a38926cef1e061743593e12542a2ea4171bd356",
            "f1dfeb0d2f2120462fc7423d1ab68c06d985fd60167401586db03a449112bdd3",
            "b1ad2b2a8cf81ec4ddd11a9eea4f37463facc783461b1dd35eb454498c79a94d",
            "87b45e786d38f3801771129076f28fd58d4ea2f2337f65e20bfc68ab0e0418c0",
            "38bfe0737459af9d4fbf134d5c92d4cb6a983c848a37a65c1defc25db857fb70",
            "48b5fd100f9688b9ebdb8fb64be6c6298ec7967b012f266743610ca900d5d9dd",
            "bfe3f64314bb0f91328f98a13e76a6b355a66aca4bba7a985bcb0d5dc53e3aff",
            "c2904d5ceaa6a821bbb415b04334f27cc40822fe13932cf1c4fc900dac499334",
            "dd0826da9dee262c9d250b946f5ddfdcf4b7ae7e9335006d8760a83f50f2ee66",
            "546b5dc7d2c7509dc5c24600f757b1730f06c49405b015e1e7a4d358e3fffeb1",
            "8ffe2bd3c6bda02751ad3ea90fde13bb4c43dada2b8818e50e1f2a525d80be9d",
            "30a8b037f263cd4ee9c82b81e8ce4fbfbee1b086bceb79f8b663194c5463c536",
            "b041cf24ba0be8891660170b1a2632d79520b20ef998128741184a841f543b23",
            "e490e648ac6cc97713d20d6b384ae106d9c96493f538c9cde03ee45f9c70a841",
            "b7879b0c6bd598a46dbb9a18c2289dbc9404869d0fd0786de9710d0cf9ff728a",
            "371eed77e7bf7a5b1954e3efb6cb65463fadad716f16ba2c2af3875a403b7474",
            "92d4890b639e60c1b41e961422ce56ca2bf98d582806fbcd2dc8db451510dc7c",
            "84ad6119703ea9e5e9c48fd7602eea1685af0431e25049ff875b8eef1f29abb7",
            "9059c332653e8bf7f9db94932a0e62df3ea4a9a6fffc0f2b76ddf3b4bb54f074",
            "0c9047a481912aa99d3283c12ce16a51bea064b4dce7b43089022ece963a262c",
            "3becfdfd766485951b189ce72899386e5253dd8235d060089912c8cb3ad20b88",
            "f9d9b23530401a16ee8ac2d10db7fb884f8f5765d3a2e7121907c0824548d043",
            "83ca896ecdb012adffd52337165252fc253f46094cc6bf60e8e01eb5d2f44009",
            "b9b0198136bc17c2162e446018cfdea484e5a8ea3aadfabe361fec64143eb44a",
            "6a63bfcdff3000c50b5dc0218162b27ff17e3944b8b7c57a50f739abb94bab97",
            "575be716c3c2ba0d2a1504dc5a60f4a5396bba92a33cd35bdf8d81c19fe793f1",
            "f2e6333c13f82bdbf26c70a2f6bae959447f5b8ecd72f6a1163d3b0a4b1c47fa",
            "57b914094e5b9d7387385278d4b5fa482ae39b70b85e65af4cb9cf235cf2fa54",
            "27bac8ebcf084e8460167519feeb75ddf5c0d4dd953d66a707e298d90dbd7a51",
            "48b570c8d314afaff1c2d87c83c1b6277403b275e2015b37a2d9196f79bbc306",
            "0521faa1f9d42d7bc4e8e37564b241d7ef24ed75c5ce62ec8745d28e5a281663",
            "eced981ce9b0b74df6eccbbd802407c36941c9fbd29214e427dd98ef0ebd21a2",
            "624fed690472fb49811b7ab29f88377ae31efe2e97a986a11d5ac5d2b25268e1",
            "9df96378cd1a9fbf50147f109486d95d0e54658bf262eda6ca379f59bed6ae13",
            "be9d1c372b4e346af7dd0496d26ac61867b3fd4b5e37777f204ca27b5926602b",
            "a4eec736fff329f2a83128e7c5edb58e68b3108d38baf1f49674e88a446f78b6",
            "d0b480daf471a27c23eeab0e460f9e579354870509a87d6660dd457c856970dd",
            "655d78e548d51e19d8da6422eb309615ec4a2670e62b7378e914894e9d7fbe08",
            "0d0369b2385f1cbfcbc4188be965645f6329a9a36662eea66f9c3aaaf1d7333a",
            "7ee0435586e060c429eafdcdb25b9f66e0b1508128fdb4dfdc9794c57fab9d7d",
            "16c0eb5af31b28c8f7f390b2747c4014c138d22152e03a3854a00d97d8bcd8db",
            "01944e91c31b4f618973809319931df5ad42011d9e5b295c4d87d733ff215c1f",
            "30499a275e75370ec4850f5f10ec7dd03a07ecc7ddc4eddefc35c7bf9efbcdc1",
            "543453003b37adc8dc99f08e6423240e84fc7a697104e302be178f79510588f3",
            "e5963f4d124a5ae45dff667bd0c5176f06c5604925c741478ed1ae2c07775338",
            "dcd0746d189c530a153ce762914d03c70cc55889aa6f9217376ec8df7caa9166",
            "7129fa6ca6a60e572d2cdb013e1af94b77ff99f1c5197b71b5ed0efa69c120a5",
            "1c3c922c6c185e50d6656e7f8d2d96ee9d26eb62ae1f2e785a3679cbfdb2289f",
            "e60f1c5e10cef6437b3b0ca5df999392ed73f41dd617a6b244b27bd3e4257559",
            "6d63635708d9b9360385f6b1037ab789589276abeea8963afcbe5bcf526dad0f",
    };

    /** SHA-256 hex (huruf kecil) dari biner. */
    public static String sha256Hex(byte[] data) {
        try {
            java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] hasil = md.digest(data);
            StringBuilder sb = new StringBuilder(hasil.length * 2);
            for (int i = 0; i < hasil.length; i++) {
                int v = hasil[i] & 0xff;
                if (v < 16) sb.append('0');
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String sha256Hex(String teks) {
        if (teks == null) return null;
        try {
            return sha256Hex(teks.getBytes("UTF-8"));
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Sidik jari satu bahasa dari tabel data[indeksBahasa][indeksKunci]
     * dengan nama unik yang diberikan. Dipakai periksaData dan uji JVM.
     */
    public static String sidikBahasa(String[] nama, String[] terjemahan) {
        if (nama == null || terjemahan == null) return null;
        if (nama.length != terjemahan.length) return null;
        try {
            java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < nama.length; i++) {
                md.update((nama[i] + "\n").getBytes("UTF-8"));
                md.update(((terjemahan[i] == null ? "" : terjemahan[i])
                        + "\n").getBytes("UTF-8"));
            }
            byte[] hasil = md.digest();
            StringBuilder sb = new StringBuilder(hasil.length * 2);
            for (int i = 0; i < hasil.length; i++) {
                int v = hasil[i] & 0xff;
                if (v < 16) sb.append('0');
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Periksa tabel string: data[b][k] = terjemahan kunci ke-k dalam
     * bahasa kode[b]. Nama unik ikut dihitung, jadi mengganti nama
     * unik saja sudah terdeteksi. Mengembalikan true bila semua
     * sidik jari cocok; ketidakcocokan ditulis ke catatan.
     */
    public static boolean periksaData(String[] kode, String[] nama,
            String[][] data, StringBuilder catatan) {
        boolean ok = true;
        if (kode == null || nama == null || data == null) {
            if (catatan != null) catatan.append("tabel string tidak ada\n");
            return false;
        }
        if (kode.length != HASH_STRING.length
                || data.length != HASH_STRING.length) {
            if (catatan != null) catatan.append("jumlah bahasa tabel berubah\n");
            return false;
        }
        for (int b = 0; b < kode.length; b++) {
            if (!KODE[b].equals(kode[b])) {
                if (catatan != null) {
                    catatan.append("urutan bahasa berubah di indeks ").append(b).append("\n");
                }
                ok = false;
                continue;
            }
            String sidik = sidikBahasa(nama, data[b]);
            if (sidik == null || !sidik.equals(HASH_STRING[b])) {
                if (catatan != null) {
                    catatan.append("string bahasa ").append(kode[b])
                            .append(" berubah\n");
                }
                ok = false;
            }
        }
        return ok;
    }

    /** Periksa tabel string aplikasi yang sedang berjalan (TeksUI). */
    public static boolean periksaStrings(StringBuilder catatan) {
        return periksaData(TeksUI.semuaKode(), NAMA_STRING,
                TeksUI.semuaData(), catatan);
    }

    /**
     * Penyedia berkas panduan (di Android: aset; di uji JVM: tiruan).
     * ada() benar bila berkasnya memang ada; baca() mengembalikan isi
     * atau null bila tidak bisa dibaca.
     */
    public interface PenyediaPanduan {
        boolean ada(String namaBerkas);
        byte[] baca(String namaBerkas);
    }

    /**
     * Periksa berkas panduan. Berkas yang tidak ada atau tidak bisa
     * dibaca dicatat saja (gagal aman). Hanya berkas yang ADA tapi
     * isinya berubah yang dianggap dimodifikasi.
     */
    public static boolean periksaPanduan(PenyediaPanduan penyedia,
            StringBuilder catatan) {
        boolean ok = true;
        if (penyedia == null) {
            if (catatan != null) catatan.append("penyedia panduan tidak ada\n");
            return true;
        }
        for (int b = 0; b < KODE.length; b++) {
            String namaBerkas = "panduan-" + KODE[b] + ".txt";
            boolean ada;
            try {
                ada = penyedia.ada(namaBerkas);
            } catch (Throwable t) {
                if (catatan != null) {
                    catatan.append("gagal memeriksa ").append(namaBerkas).append("\n");
                }
                continue;
            }
            if (!ada) {
                if (catatan != null) {
                    catatan.append("panduan ").append(namaBerkas)
                            .append(" tidak ada, dilewati\n");
                }
                continue;
            }
            byte[] isi;
            try {
                isi = penyedia.baca(namaBerkas);
            } catch (Throwable t) {
                isi = null;
            }
            if (isi == null) {
                if (catatan != null) {
                    catatan.append("panduan ").append(namaBerkas)
                            .append(" tidak terbaca, dilewati\n");
                }
                continue;
            }
            String sidik;
            try {
                java.security.MessageDigest md =
                        java.security.MessageDigest.getInstance("SHA-256");
                md.update((NAMA_PANDUAN[b] + "\n").getBytes("UTF-8"));
                md.update(isi);
                byte[] hasil = md.digest();
                StringBuilder sb = new StringBuilder(hasil.length * 2);
                for (int i = 0; i < hasil.length; i++) {
                    int v = hasil[i] & 0xff;
                    if (v < 16) sb.append('0');
                    sb.append(Integer.toHexString(v));
                }
                sidik = sb.toString();
            } catch (Throwable t) {
                sidik = null;
            }
            if (sidik == null || !sidik.equals(HASH_PANDUAN[b])) {
                if (catatan != null) {
                    catatan.append("panduan ").append(namaBerkas)
                            .append(" berubah\n");
                }
                ok = false;
            }
        }
        return ok;
    }

    /** Periksa string + panduan sekaligus. */
    public static boolean periksaSemua(PenyediaPanduan penyedia,
            StringBuilder catatan) {
        boolean stringsOk = periksaStrings(catatan);
        boolean panduanOk = periksaPanduan(penyedia, catatan);
        return stringsOk && panduanOk;
    }
}
