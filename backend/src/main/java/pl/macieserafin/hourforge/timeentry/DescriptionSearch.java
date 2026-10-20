package pl.macieserafin.hourforge.timeentry;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/** Case-insensitive "contains" match on an entry description, with LIKE wildcards in the input taken literally. */
public final class DescriptionSearch {
    public static final int MAX_LENGTH = 200;

    private DescriptionSearch() {
    }

    public static Predicate contains(CriteriaBuilder cb, Expression<String> description, String text) {
        var escaped = text.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return cb.like(cb.lower(description), "%" + escaped + "%", '\\');
    }
}
