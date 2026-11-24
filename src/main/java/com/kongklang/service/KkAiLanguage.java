package com.kongklang.service;

import java.text.Normalizer;
import java.util.Locale;

/** Conservative normalization; product codes are never corrected automatically. */
final class KkAiLanguage {
    private KkAiLanguage() {}

    static String normalize(String value) {
        String text = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}\\u200B]+", "");
        String[][] aliases = {{"สต๊อก", "สต็อก"}, {"สตอก", "สต็อก"}, {"สต้อก", "สต็อก"},
                {"stock", "สต็อก"}, {"สินคัา", "สินค้า"}, {"สินคา", "สินค้า"},
                {"เหลือเท่าไหร่", "เหลือกี่"}, {"เหลือเท่าไร", "เหลือกี่"},
                {"ราคาเท่าไหร่", "ราคา"}, {"ราคาเท่าไร", "ราคา"}, {"ขายเท่าไหร่", "ราคา"},
                {"ขายเท่าไร", "ราคา"}, {"ยอดข่าย", "ยอดขาย"}, {"ไกล้หมด", "ใกล้หมด"},
                {"หมดอายู", "หมดอายุ"}, {"7วัน", "7 วัน"}};
        for (var pair : aliases) text = text.replace(pair[0], pair[1]);
        return text;
    }

    static boolean has(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    static String productTerm(String question) {
        String text = normalize(question);
        for (String word : new String[]{"มีอะไรบ้าง", "รายการสินค้า", "สินค้าทั้งหมด", "คงเหลือ", "เหลือกี่",
                "ราคา", "ต้นทุน", "ในสต็อก", "สต็อก", "ในคลัง", "สินค้า", "จำนวน", "เท่าไหร่", "เท่าไร",
                "ของ", "ขอดู", "อยากรู้", "ช่วยดู", "ช่วย", "แล้ว", "ตัวนี้", "อันนี้", "ตัวเดิม", "ครับ", "ค่ะ", "หน่อย", "บ้าง", "มีไหม", "ไหม", "กี่", "หน่วย"}) {
            text = text.replace(word, "");
        }
        return text.strip();
    }

    static int distance(String a, String b) {
        int[] row = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) row[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int diagonal = row[0]; row[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int old = row[j];
                row[j] = Math.min(Math.min(row[j] + 1, row[j - 1] + 1), diagonal + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
                diagonal = old;
            }
        }
        return row[b.length()];
    }
}
