package com.shoploc.store_service.validation;

import java.util.regex.Pattern;

/**
 * Utility helper for validating French RIB (Relevé d'Identité Bancaire) and international IBANs.
 * Difference:
 * - RIB: French national 23-character format (5-digit bank, 5-digit branch, 11-char account, 2-digit key).
 * - IBAN: ISO 13616 international format (2-letter country code + 2 check digits + BBAN, 27 characters for France).
 */
public final class RibIbanHelper {

    private static final Pattern IBAN_FORMAT_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");
    private static final Pattern FRENCH_RIB_PATTERN = Pattern.compile("^[0-9]{5}[0-9]{5}[A-Z0-9]{11}[0-9]{2}$");

    private RibIbanHelper() {
    }

    // Strips whitespace and hyphens, then converts all characters to uppercase.
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String stripped = raw.replaceAll("[\\s-]+", "");
        return stripped.toUpperCase();
    }

    public static boolean isValidRibOrIban(String raw) {
        String cleaned = clean(raw);
        if (cleaned == null || cleaned.isEmpty()) {
            return false;
        }

        if (isPotentialIban(cleaned)) {
            return isValidIban(cleaned);
        }

        if (cleaned.length() == 23) {
            return isValidFrenchRib(cleaned);
        }

        return false;
    }

    private static boolean isPotentialIban(String cleaned) {
        if (cleaned.length() < 15) {
            return false;
        }
        char firstChar = cleaned.charAt(0);
        char secondChar = cleaned.charAt(1);
        return Character.isLetter(firstChar) && Character.isLetter(secondChar);
    }

    /**
     * Validates IBAN according to ISO 7064 MOD 97-10 algorithm.
     */
    public static boolean isValidIban(String cleaned) {
        if (cleaned == null) {
            return false;
        }
        if (!IBAN_FORMAT_PATTERN.matcher(cleaned).matches()) {
            return false;
        }
        if (cleaned.startsWith("FR") && cleaned.length() != 27) {
            return false;
        }

        String rearranged = rearrangeIban(cleaned);
        String numericRepresentation = convertLettersToDigits(rearranged);
        if (numericRepresentation == null) {
            return false;
        }

        int remainder = calculateModulo97(numericRepresentation);
        return remainder == 1;
    }

    // Moves the first 4 characters (country code and check digits) to the end per ISO 7064 checksum rules.
    private static String rearrangeIban(String iban) {
        String countryAndChecksum = iban.substring(0, 4);
        String bbanPart = iban.substring(4);
        return bbanPart + countryAndChecksum;
    }

    private static String convertLettersToDigits(String str) {
        StringBuilder numericBuilder = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (Character.isDigit(ch)) {
                numericBuilder.append(ch);
            } else if (Character.isLetter(ch)) {
                int numericValue = ch - 'A' + 10;
                numericBuilder.append(numericValue);
            } else {
                return null;
            }
        }
        return numericBuilder.toString();
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
        String numCompte = cleaned.substring(10, 21);
        String cleFournieStr = cleaned.substring(21, 23);

        String numericCompte = transliterateAccountNumber(numCompte);
        if (numericCompte == null) {
            return false;
        }

        try {
            long banque = Long.parseLong(codeBanque);
            long guichet = Long.parseLong(codeGuichet);
            long cleFournie = Long.parseLong(cleFournieStr);

            long termBanque = (89 * banque) % 97;
            long termGuichet = (15 * guichet) % 97;
            long termCompte = (3L * calculateModulo97(numericCompte)) % 97;

            long totalModulo = (termBanque + termGuichet + termCompte) % 97;
            long cleCalculee = 97 - totalModulo;

            return cleFournie == cleCalculee;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String transliterateAccountNumber(String rawAccount) {
        StringBuilder numericAccount = new StringBuilder();
        for (int i = 0; i < rawAccount.length(); i++) {
            char ch = rawAccount.charAt(i);
            if (Character.isDigit(ch)) {
                numericAccount.append(ch);
            } else {
                int digit = transliterateRibLetter(ch);
                if (digit < 0) {
                    return null;
                }
                numericAccount.append(digit);
            }
        }
        return numericAccount.toString();
    }

    /**
     * Transliterates French banking letters to digits:
     * A-I -> 1-9, J-R -> 1-9, S-Z -> 2-9
     */
    private static int transliterateRibLetter(char ch) {
        if (ch >= 'A' && ch <= 'I') {
            return ch - 'A' + 1;
        }
        if (ch >= 'J' && ch <= 'R') {
            return ch - 'J' + 1;
        }
        if (ch >= 'S' && ch <= 'Z') {
            return ch - 'S' + 2;
        }
        return -1;
    }

    /**
     * Computes (numericString mod 97) iteratively to prevent integer overflow and avoid BigInteger overhead.
     */
    private static int calculateModulo97(String numericString) {
        int remainder = 0;
        for (int i = 0; i < numericString.length(); i++) {
            int digit = numericString.charAt(i) - '0';
            remainder = (remainder * 10 + digit) % 97;
        }
        return remainder;
    }
}
