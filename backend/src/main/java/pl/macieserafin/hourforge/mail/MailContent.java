package pl.macieserafin.hourforge.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What an e-mail says, independent of its format: {@link Mailer} renders the same content as text and as HTML, so the two
 * versions can never tell different stories. Values are plain text; the HTML renderer escapes them.
 */
public final class MailContent {
    public sealed interface Block permits Paragraph, Action, Link, Details, Note {
    }

    public record Paragraph(String text) implements Block {
    }

    /** The one main thing to do: a button in HTML, the address on its own line in text. */
    public record Action(String intro, String label, String url) implements Block {
    }

    /** A secondary address, written out in full. */
    public record Link(String intro, String url) implements Block {
    }

    public record Detail(String label, String value) {
    }

    public record Details(List<Detail> rows) implements Block {
    }

    /** Smaller print under the main content, e.g. what to do if it was not you. */
    public record Note(String text) implements Block {
    }

    private final String subject;
    private final String title;
    private final String preheader;
    private final String greeting;
    private final List<Block> blocks;

    private MailContent(Builder builder) {
        this.subject = builder.subject;
        this.title = builder.title == null ? builder.subject : builder.title;
        this.preheader = builder.preheader;
        this.greeting = builder.greeting;
        this.blocks = List.copyOf(builder.blocks);
    }

    public static Builder builder(String subject) {
        return new Builder(subject);
    }

    public String subject() {
        return subject;
    }

    public String title() {
        return title;
    }

    /** The line mail clients show after the subject in the inbox list; null when there is none. */
    public String preheader() {
        return preheader;
    }

    /** "Cześć Name," or null for a message without a known name. */
    public String greeting() {
        return greeting;
    }

    public List<Block> blocks() {
        return blocks;
    }

    public static final class Builder {
        private final String subject;
        private String title;
        private String preheader;
        private String greeting;
        private final List<Block> blocks = new ArrayList<>();

        private Builder(String subject) {
            this.subject = Objects.requireNonNull(subject);
        }

        public Builder title(String value) {
            this.title = value;
            return this;
        }

        public Builder preheader(String value) {
            this.preheader = value;
            return this;
        }

        public Builder greeting(String name) {
            this.greeting = name == null || name.isBlank() ? null : "Cześć " + name + ",";
            return this;
        }

        public Builder paragraph(String text) {
            blocks.add(new Paragraph(text));
            return this;
        }

        public Builder action(String intro, String label, String url) {
            if (blocks.stream().anyMatch(Action.class::isInstance)) throw new IllegalStateException("One main action per message");
            blocks.add(new Action(intro, label, url));
            return this;
        }

        public Builder link(String intro, String url) {
            blocks.add(new Link(intro, url));
            return this;
        }

        public Builder details(List<Detail> rows) {
            if (!rows.isEmpty()) blocks.add(new Details(rows));
            return this;
        }

        public Builder note(String text) {
            blocks.add(new Note(text));
            return this;
        }

        public MailContent build() {
            return new MailContent(this);
        }
    }
}
