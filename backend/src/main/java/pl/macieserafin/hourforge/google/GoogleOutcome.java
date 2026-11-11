package pl.macieserafin.hourforge.google;

/** The codes the browser is sent back to the frontend with, as {@code ?google=<code>}. */
public enum GoogleOutcome {
    GOOGLE_CANCELLED, GOOGLE_FAILED, GOOGLE_DISABLED, GOOGLE_EMAIL_UNVERIFIED, GOOGLE_ACCOUNT_EXISTS, GOOGLE_ALREADY_LINKED,
    GOOGLE_ALREADY_LINKED_TO_YOU, LINK_EXPIRED, ACCOUNT_BLOCKED, EMAIL_NOT_VERIFIED, LINKED
}
