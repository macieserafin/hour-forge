package pl.macieserafin.hourforge.user;

import java.util.Locale;

/** Shared input normalization for stored addresses and account lookups; it does not fold visually similar letters. */
public final class EmailAddress {
    private EmailAddress() {
    }

    public static String normalize(String email) {
        if (email == null) return null;
        int start = 0;
        int end = email.length();
        // Spring's password filter uses String.trim(), while our forms also accept Unicode whitespace.
        // Remove both kinds together so repeated normalization cannot change the account key again.
        while (start < end && padding(email.codePointAt(start))) start += Character.charCount(email.codePointAt(start));
        while (end > start && padding(email.codePointBefore(end))) end -= Character.charCount(email.codePointBefore(end));
        return email.substring(start, end).toLowerCase(Locale.ROOT);
    }

    private static boolean padding(int character) {
        return character <= 0x20 || Character.isWhitespace(character);
    }
}
