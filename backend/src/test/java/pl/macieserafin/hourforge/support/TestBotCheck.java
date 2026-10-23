package pl.macieserafin.hourforge.support;

import pl.macieserafin.hourforge.security.BotCheck;

/** A bot check tests switch on, with one token that passes; off (like an instance without keys) by default. */
public class TestBotCheck implements BotCheck {
    public static final String SITE_KEY = "test-site-key";
    public static final String VALID_TOKEN = "solved-by-a-person";

    private volatile boolean on;

    public void turnOn() { on = true; }

    public void turnOff() { on = false; }

    @Override
    public String siteKey() {
        return on ? SITE_KEY : null;
    }

    @Override
    public boolean passes(String token, String remoteAddress) {
        return !on || VALID_TOKEN.equals(token);
    }
}
