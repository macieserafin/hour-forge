package pl.macieserafin.hourforge.security;

/** Proof that a person, not a script, filled in a public form (registration). Off unless the operator configures it. */
public interface BotCheck {
    /** The public key the form widget needs, or null when the check is off. */
    String siteKey();

    /** Whether the token from the widget is valid; always true when the check is off. */
    boolean passes(String token, String remoteAddress);
}
