package com.shoploc.store_service.validation;

import java.math.BigInteger;
import java.util.Map;
import java.util.regex.Pattern;

public final class RibIbanHelper {

    private static final Pattern IBAN_FORMAT_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");
    private static final Pattern FRENCH_RIB_PATTERN = Pattern.compile("^[0-9]{5}[0-9]{5}[A-Z0-9]{11}[0-9]{2}$");

    // Standard French banking transliteration table for alphanumeric account numbers
    private static final Map<Character, Integer> FRENCH_RIB_LETTER_MAP = Map.ofEntries(
            Map.entry('A', 1), Map.entry('J', 1),
            Map.entry('B', 2), Map.entry('K', 2), Map.entry('S', 2),
            Map.entry('C', 3), Map.entry('L', 3), Map.entry('T', 3),
            Map.entry('D', 4), Map.entry('M', 4), Map.entry('U', 4),
            Map.entry('E', 5), Map.entry('N', 5), Map.entry('V', 5),
            Map.entry('F', 6), Map.entry('O', 6), Map.entry('W', 6),
            Map.entry('G', 7), Map.entry('P', 7), Map.entry('X', 7),
            Map.entry('H', 8), Map.entry('Q', 8), Map.entry('Y', 8),
            Map.entry('I', 9), Map.entry('R', 9), Map.entry('Z', 9)
    );

    private RibIbanHelper() {
    }

    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replaceAll("[\\s-]+", "").toUpperCase();
    }

    public static boolean isValidRibOrIban(String raw) {
        String cleaned = clean(raw);
        if (cleaned == null || cleaned.isEmpty()) {
            return false;
        }

        if (cleaned.length() >= 15 && Character.isLetter(cleaned.charAt(0)) && Character.isLetter(cleaned.charAt(1))) {
            return isValidIban(cleaned);
        }

        if (cleaned.length() == 23) {
            return isValidFrenchRib(cleaned);
        }

        return false;
    }

    /**
     * Validates IBAN according to ISO 7064 MOD 97-10 algorithm.
     */
    public static boolean isValidIban(String cleaned) {
        if (cleaned == null || !IBAN_FORMAT_PATTERN.matcher(cleaned).matches()) {
            return false;
        }

        // Specific length check for France
        if (cleaned.startsWith("FR") && cleaned.length() != 27) {
            return false;
        }

        // Move first 4 characters to the end
        String rearranged = cleaned.substring(4) + cleaned.substring(0, 4);

        // Convert letters to numbers: A -> 10, B -> 11, ..., Z -> 35
        StringBuilder numericString = new StringBuilder();
        for (int i = 0; i < rearranged.length(); i++) {
            char ch = rearranged.charAt(i);
            if (Character.isDigit(ch)) {
                numericString.append(ch);
            } else if (Character.isLetter(ch)) {
                numericString.append(ch - 'A' + 10);
            } else {
                return false;
            }
        }

        // Verify checksum modulo 97 == 1
        BigInteger bigInt = new BigInteger(numericString.toString());
        return bigInt.mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    /**
     * Validates a 23-character French RIB (Relevé d'Identité Bancaire).
     * Structure: 5 digits (Banque) + 5 digits (Guichet) + 11 chars (Compte) + 2 digits (Clé).
     * Formula: Clé = 97 - ((89 * Banque + 15 * Guichet + 3 * Compte) mod 97)
     */
    public static boolean isValidFrenchRib(String cleaned) {
        if (cleaned == null || !FRENCH_RIB_PATTERN.matcher(cleaned).matches()) {
            return false;
        }

        String codeBanque = cleaned.substring(0, 5);
        String codeGuichet = cleaned.substring(5, 10);
        String numCompteRaw = cleaned.substring(10, 21);
        String cleStr = cleaned.substring(21, 23);

        // Transliterate letters in account number according to standard French banking rules
        StringBuilder numCompteNumeric = new StringBuilder();
        for (int i = 0; i < numCompteRaw.length(); i++) {
            char ch = numCompteRaw.charAt(i);
            if (Character.isDigit(ch)) {
                numCompteNumeric.append(ch);
            } else {
                Integer mapped = FRENCH_RIB_LETTER_MAP.get(ch);
                if (mapped == null) {
                    return false;
                }
                numCompteNumeric.append(mapped);
            }
        }

        try {
            long banque = Long.parseLong(codeBanque);
            long guichet = Long.parseLong(codeGuichet);
            long cleFournie = Long.parseLong(cleStr);

            long term1 = (89 * banque) % 97;
            long term2 = (15 * guichet) % 97;

            BigInteger compteBigInt = new BigInteger(numCompteNumeric.toString());
            long term3 = compteBigInt.multiply(BigInteger.valueOf(3)).mod(BigInteger.valueOf(97)).longValue();

            long remainder = (term1 + term2 + term3) % 97;
            long cleCalculee = 97 - remainder;

            return cleFournie == cleCalculee;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
