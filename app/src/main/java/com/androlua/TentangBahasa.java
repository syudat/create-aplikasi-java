package com.androlua;

/**
 * Teks Tentang Alat dalam 50 bahasa (perintah al kautsar 2026-11-23):
 * judul layar, paragraf deskripsi profesional, dan catatan bahwa
 * panduan lengkap di bawahnya berbahasa Indonesia. Dipilih mengikuti
 * bahasa efektif aplikasi (Bahasa.kodeEfektif). Bahasa yang belum
 * memiliki terjemahan judul/catatan jatuh ke Inggris; deskripsi jatuh
 * ke Indonesia sebagai bahasa asal.
 */
public class TentangBahasa {

    private static String norm(String kode) {
        Bahasa.Entri e = Bahasa.dariKode(kode);
        return e != null ? e.kode : "id";
    }

    /** Judul layar Tentang dalam bahasa terpilih. */
    public static String judul(String kode) {
        switch (norm(kode)) {
            case "en": return "About This Tool";
            case "es": return "Acerca de esta herramienta";
            case "ar": return "حول هذه الأداة";
            case "zh": return "关于本工具";
            case "hi": return "इस टूल के बारे में";
            case "pt": return "Sobre esta ferramenta";
            case "ru": return "Об этом инструменте";
            case "ja": return "このツールについて";
            case "de": return "Über dieses Werkzeug";
            case "fr": return "À propos de cet outil";
            case "ko": return "이 도구 정보";
            case "it": return "Informazioni su questo strumento";
            case "tr": return "Bu Araç Hakkında";
            case "vi": return "Giới thiệu về công cụ này";
            case "th": return "เกี่ยวกับเครื่องมือนี้";
            case "pl": return "O tym narzędziu";
            case "uk": return "Про цей інструмент";
            case "nl": return "Over deze tool";
            case "fa": return "درباره این ابزار";
            case "ur": return "اس ٹول کے بارے میں";
            case "bn": return "এই টুল সম্পর্কে";
            case "ta": return "இந்தக் கருவி பற்றி";
            case "te": return "ఈ సాధనం గురించి";
            case "mr": return "या साधनाबद्दल";
            case "jv": return "Bab Piranti Iki";
            case "su": return "Ngeunaan Pakakas Ieu";
            case "ms": return "Perihal Alat Ini";
            case "fil": return "Tungkol sa Tool na Ito";
            case "sv": return "Om det här verktyget";
            case "no": return "Om dette verktøyet";
            case "da": return "Om dette værktøj";
            case "fi": return "Tietoa tästä työkalusta";
            case "el": return "Σχετικά με αυτό το εργαλείο";
            case "cs": return "O tomto nástroji";
            case "ro": return "Despre acest instrument";
            case "hu": return "Az eszköz névjegye";
            case "he": return "על הכלי הזה";
            case "sw": return "Kuhusu Zana Hii";
            case "am": return "ስለዚህ መሣሪያ";
            case "ha": return "Game da Wannan Kayan Aiki";
            case "kk": return "Бұл құрал туралы";
            case "uz": return "Ushbu vosita haqida";
            case "az": return "Bu alət haqqında";
            case "my": return "ဤကိရိယာအကြောင်း";
            case "km": return "អំពីឧបករណ៍នេះ";
            case "lo": return "ກ່ຽວກັບເຄື່ອງມືນີ້";
            case "ne": return "यो उपकरणको बारेमा";
            case "si": return "මෙම මෙවලම ගැන";
            case "mn": return "Энэ хэрэгслийн тухай";
            default: return "Tentang Alat";
        }
    }

    /** Catatan satu kalimat: panduan lengkap di bawah berbahasa Indonesia. */
    public static String catatanPanduan(String kode) {
        switch (norm(kode)) {
            case "id": return "";
            case "en": return "The complete user guide below is currently available in Indonesian.";
            case "es": return "La guía completa del usuario que aparece a continuación está disponible actualmente en indonesio.";
            case "ar": return "دليل المستخدم الكامل أدناه متاح حاليًا باللغة الإندونيسية.";
            case "zh": return "下面的完整用户指南目前提供印度尼西亚语版本。";
            case "hi": return "नीचे दी गई संपूर्ण उपयोगकर्ता मार्गदर्शिका वर्तमान में इंडोनेशियाई भाषा में उपलब्ध है।";
            case "pt": return "O guia completo do usuário abaixo está disponível atualmente em indonésio.";
            case "ru": return "Полное руководство пользователя ниже в настоящее время доступно на индонезийском языке.";
            case "ja": return "以下の完全なユーザーガイドは、現在インドネシア語で提供されています。";
            case "de": return "Das vollständige Benutzerhandbuch unten ist derzeit auf Indonesisch verfügbar.";
            case "fr": return "Le guide d'utilisation complet ci-dessous est actuellement disponible en indonésien.";
            case "ko": return "아래의 전체 사용자 안내는 현재 인도네시아어로 제공됩니다.";
            case "it": return "La guida utente completa qui sotto è attualmente disponibile in indonesiano.";
            case "tr": return "Aşağıdaki eksiksiz kullanıcı kılavuzu şu anda Endonezyaca dilindedir.";
            case "vi": return "Hướng dẫn sử dụng đầy đủ bên dưới hiện có bằng tiếng Indonesia.";
            case "th": return "คู่มือผู้ใช้ฉบับสมบูรณ์ด้านล่างนี้มีให้บริการเป็นภาษาอินโดนีเซียในขณะนี้";
            case "pl": return "Pełny przewodnik użytkownika poniżej jest obecnie dostępny w języku indonezyjskim.";
            case "uk": return "Повний посібник користувача нижче наразі доступний індонезійською мовою.";
            case "nl": return "De volledige gebruikershandleiding hieronder is momenteel beschikbaar in het Indonesisch.";
            case "fa": return "راهنمای کامل کاربر در زیر در حال حاضر به زبان اندونزیایی موجود است.";
            case "ur": return "نیچے دی گئی مکمل صارف گائیڈ فی الحال انڈونیشیائی زبان میں دستیاب ہے۔";
            case "bn": return "নীচের সম্পূর্ণ ব্যবহারকারী নির্দেশিকা বর্তমানে ইন্দোনেশীয় ভাষায় উপলব্ধ।";
            case "ms": return "Panduan pengguna lengkap di bawah buat masa ini tersedia dalam bahasa Indonesia.";
            case "jv": return "Panduan panganggo jangkep ing ngisor iki saiki kasedhiya ing basa Indonesia.";
            case "fil": return "Ang kumpletong gabay sa gumagamit sa ibaba ay kasalukuyang nasa wikang Indonesian.";
            default: return "The complete user guide below is currently available in Indonesian.";
        }
    }

    /** Paragraf deskripsi profesional aplikasi dalam bahasa terpilih. */
    public static String deskripsi(String kode) {
        switch (norm(kode)) {
            case "en": return "Create Aplikasi is a complete Android application development environment that runs entirely on your phone. Developed by al kautsar, it lets you design, write, build, sign, and test Android applications in Java, Kotlin, HTML, Python, and Jetpack Compose — without needing a computer. Every feature is designed to be fully accessible with a screen reader.";
            case "es": return "Create Aplikasi es un entorno completo de desarrollo de aplicaciones Android que funciona por completo en tu teléfono. Desarrollado por al kautsar, te permite diseñar, escribir, compilar, firmar y probar aplicaciones Android en Java, Kotlin, HTML, Python y Jetpack Compose, sin necesidad de una computadora. Cada función está diseñada para ser totalmente accesible con un lector de pantalla.";
            case "ar": return "تطبيق Create Aplikasi هو بيئة متكاملة لتطوير تطبيقات أندرويد تعمل بالكامل على هاتفك. طوّره al kautsar، وهو يتيح لك تصميم تطبيقات أندرويد وكتابتها وبناءها وتوقيعها واختبارها بلغات Java وKotlin وHTML وPython وJetpack Compose، دون الحاجة إلى جهاز كمبيوتر. وقد صُممت كل ميزة فيه لتكون قابلة للوصول بالكامل باستخدام قارئ الشاشة.";
            case "zh": return "Create Aplikasi 是一个完整的 Android 应用开发环境，完全在您的手机上运行。由 al kautsar 开发，它让您无需电脑即可使用 Java、Kotlin、HTML、Python 和 Jetpack Compose 设计、编写、构建、签名和测试 Android 应用。每个功能都经过精心设计，可通过屏幕阅读器完全无障碍使用。";
            case "hi": return "Create Aplikasi एक संपूर्ण Android ऐप्लिकेशन विकास परिवेश है जो पूरी तरह आपके फ़ोन पर चलता है। al kautsar द्वारा विकसित, यह आपको कंप्यूटर की आवश्यकता के बिना Java, Kotlin, HTML, Python और Jetpack Compose में Android ऐप डिज़ाइन करने, लिखने, बनाने, हस्ताक्षर करने और परखने देता है। हर सुविधा को स्क्रीन रीडर के साथ पूरी तरह सुलभ बनाया गया है।";
            case "pt": return "O Create Aplikasi é um ambiente completo de desenvolvimento de aplicativos Android que funciona inteiramente no seu telefone. Desenvolvido por al kautsar, ele permite projetar, escrever, compilar, assinar e testar aplicativos Android em Java, Kotlin, HTML, Python e Jetpack Compose, sem precisar de um computador. Cada recurso foi projetado para ser totalmente acessível com um leitor de tela.";
            case "ru": return "Create Aplikasi — это полноценная среда разработки Android-приложений, которая полностью работает на вашем телефоне. Созданная разработчиком al kautsar, она позволяет проектировать, писать, собирать, подписывать и тестировать приложения Android на Java, Kotlin, HTML, Python и Jetpack Compose — без компьютера. Каждая функция спроектирована так, чтобы быть полностью доступной с программой чтения с экрана.";
            case "ja": return "Create Aplikasi は、スマートフォン上ですべて完結する完全な Android アプリ開発環境です。al kautsar によって開発され、パソコンを使わずに Java、Kotlin、HTML、Python、Jetpack Compose で Android アプリの設計、作成、ビルド、署名、テストが行えます。すべての機能は、スクリーンリーダーで完全に利用できるよう設計されています。";
            case "de": return "Create Aplikasi ist eine vollständige Entwicklungsumgebung für Android-Apps, die vollständig auf Ihrem Telefon läuft. Entwickelt von al kautsar, können Sie damit Android-Apps in Java, Kotlin, HTML, Python und Jetpack Compose entwerfen, schreiben, erstellen, signieren und testen — ganz ohne Computer. Jede Funktion ist so gestaltet, dass sie mit einem Screenreader vollständig zugänglich ist.";
            case "fr": return "Create Aplikasi est un environnement complet de développement d'applications Android qui fonctionne entièrement sur votre téléphone. Développé par al kautsar, il vous permet de concevoir, écrire, compiler, signer et tester des applications Android en Java, Kotlin, HTML, Python et Jetpack Compose, sans avoir besoin d'un ordinateur. Chaque fonctionnalité est conçue pour être entièrement accessible avec un lecteur d'écran.";
            case "ko": return "Create Aplikasi는 휴대폰에서 완전히 실행되는 완성형 Android 앱 개발 환경입니다. al kautsar가 개발했으며, 컴퓨터 없이도 Java, Kotlin, HTML, Python, Jetpack Compose로 Android 앱을 설계, 작성, 빌드, 서명, 테스트할 수 있습니다. 모든 기능은 화면 낭독 프로그램으로 완전히 이용할 수 있도록 설계되었습니다.";
            case "it": return "Create Aplikasi è un ambiente completo di sviluppo di applicazioni Android che funziona interamente sul tuo telefono. Sviluppato da al kautsar, ti permette di progettare, scrivere, compilare, firmare e testare applicazioni Android in Java, Kotlin, HTML, Python e Jetpack Compose, senza bisogno di un computer. Ogni funzione è progettata per essere completamente accessibile con un lettore di schermo.";
            case "tr": return "Create Aplikasi, tamamen telefonunuzda çalışan eksiksiz bir Android uygulama geliştirme ortamıdır. al kautsar tarafından geliştirilen bu uygulama, bilgisayar gerektirmeden Java, Kotlin, HTML, Python ve Jetpack Compose ile Android uygulamaları tasarlamanıza, yazmanıza, derlemenize, imzalamanıza ve test etmenize olanak tanır. Her özellik, ekran okuyucuyla tamamen erişilebilir olacak şekilde tasarlanmıştır.";
            case "vi": return "Create Aplikasi là một môi trường phát triển ứng dụng Android hoàn chỉnh, hoạt động hoàn toàn trên điện thoại của bạn. Do al kautsar phát triển, ứng dụng cho phép bạn thiết kế, viết, xây dựng, ký và kiểm thử ứng dụng Android bằng Java, Kotlin, HTML, Python và Jetpack Compose — mà không cần máy tính. Mọi tính năng đều được thiết kế để có thể truy cập hoàn toàn bằng trình đọc màn hình.";
            case "th": return "Create Aplikasi คือสภาพแวดล้อมการพัฒนาแอปพลิเคชัน Android ที่สมบูรณ์ ซึ่งทำงานทั้งหมดบนโทรศัพท์ของคุณ พัฒนาโดย al kautsar ช่วยให้คุณออกแบบ เขียน สร้าง ลงนาม และทดสอบแอป Android ด้วย Java, Kotlin, HTML, Python และ Jetpack Compose โดยไม่ต้องใช้คอมพิวเตอร์ ทุกฟีเจอร์ได้รับการออกแบบให้เข้าถึงได้อย่างเต็มที่ผ่านโปรแกรมอ่านหน้าจอ";
            case "pl": return "Create Aplikasi to kompletne środowisko programistyczne do tworzenia aplikacji na Androida, które działa w całości na telefonie. Stworzone przez al kautsar, pozwala projektować, pisać, kompilować, podpisywać i testować aplikacje na Androida w językach Java, Kotlin, HTML, Python oraz w Jetpack Compose — bez potrzeby korzystania z komputera. Każda funkcja została zaprojektowana tak, aby była w pełni dostępna dla czytnika ekranu.";
            case "uk": return "Create Aplikasi — це повноцінне середовище розробки застосунків Android, яке повністю працює на вашому телефоні. Створене розробником al kautsar, воно дає змогу проєктувати, писати, збирати, підписувати й тестувати застосунки Android мовами Java, Kotlin, HTML, Python та Jetpack Compose — без комп'ютера. Кожну функцію спроєктовано так, щоб вона була повністю доступною з програмою читання з екрана.";
            case "nl": return "Create Aplikasi is een complete ontwikkelomgeving voor Android-apps die volledig op je telefoon draait. Ontwikkeld door al kautsar, kun je er Android-apps mee ontwerpen, schrijven, bouwen, ondertekenen en testen in Java, Kotlin, HTML, Python en Jetpack Compose — zonder computer. Elke functie is ontworpen om volledig toegankelijk te zijn met een schermlezer.";
            case "fa": return "اپلیکیشن Create Aplikasi یک محیط کامل توسعه برنامه‌های اندروید است که به‌طور کامل روی تلفن شما اجرا می‌شود. این برنامه که توسط al kautsar ساخته شده است، به شما امکان می‌دهد بدون نیاز به رایانه، برنامه‌های اندروید را با Java و Kotlin و HTML و Python و Jetpack Compose طراحی کنید، بنویسید، بسازید، امضا کنید و بیازمایید. هر قابلیت آن طوری طراحی شده است که با صفحه‌خوان کاملاً در دسترس باشد.";
            case "ur": return "Create Aplikasi ایک مکمل اینڈرائیڈ ایپلیکیشن ڈیولپمنٹ ماحول ہے جو مکمل طور پر آپ کے فون پر چلتا ہے۔ al kautsar کی تیار کردہ یہ ایپ آپ کو کمپیوٹر کے بغیر Java، Kotlin، HTML، Python اور Jetpack Compose میں اینڈرائیڈ ایپس ڈیزائن کرنے، لکھنے، بنانے، دستخط کرنے اور جانچنے کی سہولت دیتی ہے۔ ہر خصوصیت اس طرح بنائی گئی ہے کہ اسکرین ریڈر کے ساتھ مکمل طور پر قابل رسائی ہو۔";
            case "bn": return "Create Aplikasi একটি সম্পূর্ণ Android অ্যাপ্লিকেশন ডেভেলপমেন্ট পরিবেশ যা সম্পূর্ণরূপে আপনার ফোনেই চলে। al kautsar কর্তৃক তৈরি, এটি আপনাকে কম্পিউটার ছাড়াই Java, Kotlin, HTML, Python এবং Jetpack Compose-এ Android অ্যাপ ডিজাইন, লেখা, তৈরি, স্বাক্ষর এবং পরীক্ষা করতে দেয়। প্রতিটি বৈশিষ্ট্য স্ক্রিন রিডারের সাথে সম্পূর্ণ অ্যাক্সেসযোগ্য করে ডিজাইন করা হয়েছে।";
            case "ta": return "Create Aplikasi என்பது உங்கள் தொலைபேசியிலேயே முழுமையாக இயங்கும் ஒரு முழுமையான Android பயன்பாட்டு மேம்பாட்டுச் சூழல் ஆகும். al kautsar உருவாக்கிய இது, கணினி தேவையின்றி Java, Kotlin, HTML, Python மற்றும் Jetpack Compose ஆகியவற்றில் Android பயன்பாடுகளை வடிவமைக்க, எழுத, உருவாக்க, கையொப்பமிட மற்றும் சோதிக்க உங்களை அனுமதிக்கிறது. ஒவ்வொரு அம்சமும் திரை வாசகருடன் முழுமையாக அணுகக்கூடியதாக வடிவமைக்கப்பட்டுள்ளது.";
            case "te": return "Create Aplikasi అనేది మీ ఫోన్‌లోనే పూర్తిగా నడిచే సంపూర్ణ Android అప్లికేషన్ డెవలప్‌మెంట్ వాతావరణం. al kautsar అభివృద్ధి చేసిన ఇది, కంప్యూటర్ అవసరం లేకుండా Java, Kotlin, HTML, Python మరియు Jetpack Compose‌లో Android యాప్‌లను డిజైన్ చేయడానికి, రాయడానికి, నిర్మించడానికి, సంతకం చేయడానికి మరియు పరీక్షించడానికి మిమ్మల్ని అనుమతిస్తుంది. ప్రతి ఫీచర్ స్క్రీన్ రీడర్‌తో పూర్తిగా అందుబాటులో ఉండేలా రూపొందించబడింది.";
            case "mr": return "Create Aplikasi हे एक संपूर्ण Android अॅप्लिकेशन विकास वातावरण आहे जे पूर्णपणे तुमच्या फोनवर चालते. al kautsar यांनी विकसित केलेले, हे तुम्हाला संगणकाशिवाय Java, Kotlin, HTML, Python आणि Jetpack Compose मध्ये Android अॅप्स डिझाइन, लिहिणे, तयार करणे, स्वाक्षरी करणे आणि तपासणे शक्य करते. प्रत्येक वैशिष्ट्य स्क्रीन रीडरसह पूर्णपणे प्रवेशयोग्य असे डिझाइन केले आहे.";
            case "jv": return "Create Aplikasi iku lingkungan pangembangan aplikasi Android jangkep sing lumaku sakabehe ing HP panjenengan. Dikembangake dening al kautsar, aplikasi iki ngidini panjenengan ngrancang, nulis, mbangun, nandatangani, lan nguji aplikasi Android nganggo Java, Kotlin, HTML, Python, lan Jetpack Compose — tanpa prelu komputer. Saben fitur dirancang supaya bisa diakses kanthi jangkep nganggo pamaca layar.";
            case "su": return "Create Aplikasi téh lingkungan pamekaran aplikasi Android anu lengkep anu jalan sagemblengna dina telepon anjeun. Dimekarkeun ku al kautsar, aplikasi ieu ngamungkinkeun anjeun ngarancang, nulis, ngawangun, nandatanganan, jeung nguji aplikasi Android ku Java, Kotlin, HTML, Python, jeung Jetpack Compose — tanpa perlu komputer. Unggal fitur dirancang sangkan bisa diaksés sagemblengna ku pamaca layar.";
            case "ms": return "Create Aplikasi ialah persekitaran pembangunan aplikasi Android yang lengkap dan berjalan sepenuhnya pada telefon anda. Dibangunkan oleh al kautsar, ia membolehkan anda mereka bentuk, menulis, membina, menandatangani dan menguji aplikasi Android dalam Java, Kotlin, HTML, Python dan Jetpack Compose — tanpa memerlukan komputer. Setiap ciri direka untuk boleh diakses sepenuhnya dengan pembaca skrin.";
            case "fil": return "Ang Create Aplikasi ay isang kumpletong kapaligiran sa pagbuo ng Android application na ganap na tumatakbo sa iyong telepono. Binuo ni al kautsar, pinahihintulutan ka nitong magdisenyo, magsulat, bumuo, pumirma, at sumubok ng mga Android app sa Java, Kotlin, HTML, Python, at Jetpack Compose — nang hindi nangangailangan ng computer. Ang bawat tampok ay idinisenyo upang ganap na magamit kasama ang screen reader.";
            case "sv": return "Create Aplikasi är en komplett utvecklingsmiljö för Android-appar som körs helt på din telefon. Utvecklad av al kautsar låter den dig designa, skriva, bygga, signera och testa Android-appar i Java, Kotlin, HTML, Python och Jetpack Compose — utan att behöva en dator. Varje funktion är utformad för att vara helt tillgänglig med en skärmläsare.";
            case "no": return "Create Aplikasi er et komplett utviklingsmiljø for Android-apper som kjører i sin helhet på telefonen din. Utviklet av al kautsar, lar det deg designe, skrive, bygge, signere og teste Android-apper i Java, Kotlin, HTML, Python og Jetpack Compose — uten å trenge en datamaskin. Hver funksjon er utformet for å være fullt tilgjengelig med en skjermleser.";
            case "da": return "Create Aplikasi er et komplet udviklingsmiljø til Android-apps, der kører helt på din telefon. Udviklet af al kautsar giver det dig mulighed for at designe, skrive, bygge, signere og teste Android-apps i Java, Kotlin, HTML, Python og Jetpack Compose — uden at skulle bruge en computer. Hver funktion er designet til at være fuldt tilgængelig med en skærmlæser.";
            case "fi": return "Create Aplikasi on täydellinen Android-sovellusten kehitysympäristö, joka toimii kokonaan puhelimessasi. Sen on kehittänyt al kautsar, ja sen avulla voit suunnitella, kirjoittaa, rakentaa, allekirjoittaa ja testata Android-sovelluksia Javalla, Kotlinilla, HTML:llä, Pythonilla ja Jetpack Composella — ilman tietokonetta. Jokainen ominaisuus on suunniteltu täysin saavutettavaksi ruudunlukijalla.";
            case "el": return "Το Create Aplikasi είναι ένα πλήρες περιβάλλον ανάπτυξης εφαρμογών Android που λειτουργεί εξ ολοκλήρου στο τηλέφωνό σας. Αναπτύχθηκε από τον al kautsar και σας επιτρέπει να σχεδιάζετε, να γράφετε, να δημιουργείτε, να υπογράφετε και να δοκιμάζετε εφαρμογές Android σε Java, Kotlin, HTML, Python και Jetpack Compose — χωρίς να χρειάζεστε υπολογιστή. Κάθε λειτουργία έχει σχεδιαστεί ώστε να είναι πλήρως προσβάσιμη με πρόγραμμα ανάγνωσης οθόνης.";
            case "cs": return "Create Aplikasi je kompletní vývojové prostředí pro aplikace Android, které běží celé ve vašem telefonu. Vytvořil jej al kautsar a umožňuje vám navrhovat, psát, sestavovat, podepisovat a testovat aplikace pro Android v jazycích Java, Kotlin, HTML, Python a Jetpack Compose — bez potřeby počítače. Každá funkce je navržena tak, aby byla plně přístupná s čtečkou obrazovky.";
            case "ro": return "Create Aplikasi este un mediu complet de dezvoltare a aplicațiilor Android care rulează în întregime pe telefonul dumneavoastră. Dezvoltat de al kautsar, vă permite să proiectați, să scrieți, să construiți, să semnați și să testați aplicații Android în Java, Kotlin, HTML, Python și Jetpack Compose — fără a avea nevoie de un computer. Fiecare funcție este concepută pentru a fi complet accesibilă cu un cititor de ecran.";
            case "hu": return "A Create Aplikasi egy teljes Android-alkalmazás-fejlesztői környezet, amely teljes egészében a telefonján fut. Az al kautsar által fejlesztett alkalmazással számítógép nélkül tervezhet, írhat, készíthet, aláírhat és tesztelhet Android-alkalmazásokat Java, Kotlin, HTML, Python és Jetpack Compose nyelven. Minden funkció úgy készült, hogy képernyőolvasóval teljesen hozzáférhető legyen.";
            case "he": return "Create Aplikasi היא סביבת פיתוח מלאה ליישומי Android הפועלת כולה בטלפון שלכם. היא פותחה על ידי al kautsar ומאפשרת לכם לעצב, לכתוב, לבנות, לחתום ולבדוק יישומי Android ב-Java, Kotlin, HTML, Python ו-Jetpack Compose — ללא צורך במחשב. כל תכונה תוכננה להיות נגישה לחלוטין עם קורא מסך.";
            case "sw": return "Create Aplikasi ni mazingira kamili ya kutengeneza programu za Android yanayoendeshwa yote kwenye simu yako. Imetengenezwa na al kautsar, inakuwezesha kubuni, kuandika, kujenga, kutia saini na kujaribu programu za Android kwa Java, Kotlin, HTML, Python na Jetpack Compose — bila kuhitaji kompyuta. Kila kipengele kimebuniwa kiweze kufikiwa kikamilifu kwa kisoma skrini.";
            case "am": return "Create Aplikasi ሙሉ በሙሉ በስልክዎ ላይ የሚሰራ የተሟላ የአንድሮይድ መተግበሪያ ማዘጋጃ አካባቢ ነው። በal kautsar የተዘጋጀ ሲሆን ኮምፒውተር ሳያስፈልግዎ በJava፣ Kotlin፣ HTML፣ Python እና Jetpack Compose የአንድሮይድ መተግበሪያዎችን እንዲነድፉ፣ እንዲጽፉ፣ እንዲገነቡ፣ እንዲፈርሙ እና እንዲሞክሩ ያስችልዎታል። እያንዳንዱ ባህሪ በማያ ገጽ አንባቢ ሙሉ በሙሉ ተደራሽ እንዲሆን ተደርጎ ተዘጋጅቷል።";
            case "ha": return "Create Aplikasi cikakken muhallin haɓaka manhajojin Android ne wanda ke aiki gaba ɗaya a wayarka. al kautsar ne ya haɓaka shi, yana ba ka damar tsarawa, rubutawa, ginawa, sa hannu da gwada manhajojin Android a Java, Kotlin, HTML, Python da Jetpack Compose — ba tare da buƙatar kwamfuta ba. Kowane fasali an tsara shi don ya kasance mai sauƙin isa gaba ɗaya tare da mai karanta allo.";
            case "kk": return "Create Aplikasi — телефоныңызда толығымен жұмыс істейтін Android қолданбаларын әзірлеудің толық ортасы. Оны al kautsar әзірлеген; ол компьютерсіз Java, Kotlin, HTML, Python және Jetpack Compose тілдерінде Android қолданбаларын жобалауға, жазуға, құрастыруға, қол қоюға және сынауға мүмкіндік береді. Әрбір функция экран оқу құралымен толық қолжетімді болатындай етіп жасалған.";
            case "uz": return "Create Aplikasi — bu telefoningizda to'liq ishlaydigan Android ilovalarini ishlab chiqish uchun mukammal muhit. al kautsar tomonidan ishlab chiqilgan bu ilova kompyutersiz Java, Kotlin, HTML, Python va Jetpack Compose tillarida Android ilovalarini loyihalash, yozish, yaratish, imzolash va sinash imkonini beradi. Har bir funksiya ekran o'quvchi bilan to'liq foydalanish mumkin bo'lishi uchun mo'ljallangan.";
            case "az": return "Create Aplikasi tamamilə telefonunuzda işləyən tam Android tətbiq hazırlama mühitidir. al kautsar tərəfindən hazırlanmış bu tətbiq kompüterə ehtiyac olmadan Java, Kotlin, HTML, Python və Jetpack Compose ilə Android tətbiqləri dizayn etməyə, yazmağa, qurmağa, imzalamağa və sınaqdan keçirməyə imkan verir. Hər bir funksiya ekran oxuyucusu ilə tam əlçatan olmaq üçün nəzərdə tutulmuşdur.";
            case "my": return "Create Aplikasi သည် သင့်ဖုန်းပေါ်တွင် လုံးဝလည်ပတ်နိုင်သော ပြည့်စုံသည့် Android အက်ပ်ဖွံ့ဖြိုးရေးပတ်ဝန်းကျင်တစ်ခုဖြစ်သည်။ al kautsar မှ ဖန်တီးထားပြီး ကွန်ပျူတာမလိုဘဲ Java, Kotlin, HTML, Python နှင့် Jetpack Compose တို့ဖြင့် Android အက်ပ်များကို ဒီဇိုင်းဆွဲ၊ ရေး၊ တည်ဆောက်၊ လက်မှတ်ထိုးနှင့် စမ်းသပ်နိုင်စေသည်။ လုပ်ဆောင်ချက်တိုင်းကို စခရင်ဖတ်စက်ဖြင့် အပြည့်အဝအသုံးပြုနိုင်အောင် ဒီဇိုင်းထုတ်ထားသည်။";
            case "km": return "Create Aplikasi គឺជាបរិស្ថានអភិវឌ្ឍន៍កម្មវិធី Android ពេញលេញ ដែលដំណើរការទាំងស្រុងនៅលើទូរសព្ទរបស់អ្នក។ អភិវឌ្ឍដោយ al kautsar វាអនុញ្ញាតឱ្យអ្នករចនា សរសេរ សាងសង់ ចុះហត្ថលេខា និងសាកល្បងកម្មវិធី Android ជាមួយ Java, Kotlin, HTML, Python និង Jetpack Compose — ដោយមិនចាំបាច់មានកុំព្យូទ័រ។ មុខងារនីមួយៗត្រូវបានរចនាឡើងដើម្បីអាចចូលប្រើបានពេញលេញជាមួយកម្មវិធីអានអេក្រង់។";
            case "lo": return "Create Aplikasi ແມ່ນສະພາບແວດລ້ອມການພັດທະນາແອັບ Android ທີ່ສົມບູນ ເຊິ່ງເຮັດວຽກທັງໝົດຢູ່ໃນໂທລະສັບຂອງທ່ານ. ພັດທະນາໂດຍ al kautsar, ມັນຊ່ວຍໃຫ້ທ່ານອອກແບບ, ຂຽນ, ສ້າງ, ເຊັນ ແລະ ທົດສອບແອັບ Android ດ້ວຍ Java, Kotlin, HTML, Python ແລະ Jetpack Compose — ໂດຍບໍ່ຕ້ອງໃຊ້ຄອມພິວເຕີ. ທຸກຄຸນສົມບັດຖືກອອກແບບໃຫ້ສາມາດເຂົ້າເຖິງໄດ້ຢ່າງເຕັມທີ່ດ້ວຍໂປຣແກຣມອ່ານໜ້າຈໍ.";
            case "ne": return "Create Aplikasi एक पूर्ण Android अनुप्रयोग विकास वातावरण हो जुन पूर्ण रूपमा तपाईंको फोनमा चल्छ। al kautsar द्वारा विकसित, यसले तपाईंलाई कम्प्युटर बिना Java, Kotlin, HTML, Python र Jetpack Compose मा Android एपहरू डिजाइन, लेख्न, निर्माण, हस्ताक्षर र परीक्षण गर्न दिन्छ। प्रत्येक सुविधा स्क्रिन रिडरसँग पूर्ण रूपमा पहुँचयोग्य हुने गरी डिजाइन गरिएको छ।";
            case "si": return "Create Aplikasi යනු ඔබේ දුරකථනයේ සම්පූර්ණයෙන්ම ක්‍රියාත්මක වන සම්පූර්ණ Android යෙදුම් සංවර්ධන පරිසරයකි. al kautsar විසින් සංවර්ධනය කරන ලද මෙය, පරිගණකයක් අවශ්‍ය නොවී Java, Kotlin, HTML, Python සහ Jetpack Compose වලින් Android යෙදුම් සැලසුම් කිරීමට, ලිවීමට, ගොඩනැගීමට, අත්සන් කිරීමට සහ പരീක්ෂා කිරීමට ඔබට ඉඩ දෙයි. සෑම විශේෂාංගයක්ම තිර කියවනයක් සමඟ සම්පූර්ණයෙන්ම ප්‍රවේශ විය හැකි පරිදි සැලසුම් කර ඇත.";
            case "mn": return "Create Aplikasi нь таны утсан дээр бүрэн ажилладаг Android апп хөгжүүлэлтийн иж бүрэн орчин юм. al kautsar-ийн хөгжүүлсэн энэ апп нь компьютер шаардлагагүйгээр Java, Kotlin, HTML, Python болон Jetpack Compose ашиглан Android апп зохион бүтээх, бичих, бүтээх, гарын үсэг зурах, турших боломжийг олгодог. Бүх функц нь дэлгэц уншигчтай бүрэн хүртээмжтэй байхаар бүтээгдсэн.";
            default: return "Create Aplikasi adalah lingkungan pengembangan aplikasi Android lengkap yang berjalan sepenuhnya di ponsel Anda. Dikembangkan oleh al kautsar, aplikasi ini memungkinkan Anda merancang, menulis, membangun, menandatangani, dan menguji aplikasi Android dalam bahasa Java, Kotlin, HTML, Python, dan Jetpack Compose — tanpa memerlukan komputer. Setiap fitur dirancang agar dapat diakses sepenuhnya dengan pembaca layar.";
        }
    }
}
