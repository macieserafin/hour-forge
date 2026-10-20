package pl.macieserafin.hourforge.report;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.team.TeamMember;
import pl.macieserafin.hourforge.timeentry.DescriptionSearch;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntry;

@Repository
public class ReportRepository {
    private final EntityManager em;

    public ReportRepository(EntityManager em) { this.em = em; }

    private Predicate filter(CriteriaBuilder cb, Root<TimeEntry> root, UUID teamId,
                             ReportFilter filter, boolean active) {
        var predicates = new ArrayList<Predicate>();
        predicates.add(cb.equal(root.get("team").get("id"), teamId));
        if (active) {
            predicates.add(cb.equal(root.get("entryType"), EntryType.TIMER));
            predicates.add(cb.isNull(root.get("endedAt")));
        } else {
            predicates.add(cb.isNotNull(root.get("durationSeconds")));
        }
        if (filter.from() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("workDate"), filter.from()));
        if (filter.undated()) predicates.add(cb.isNull(root.get("workDate")));
        if (filter.to() != null) predicates.add(cb.lessThanOrEqualTo(root.get("workDate"), filter.to()));
        if (!filter.userIds().isEmpty()) predicates.add(root.get("user").get("id").in(filter.userIds()));
        if (!filter.projectIds().isEmpty()) predicates.add(root.get("project").get("id").in(filter.projectIds()));
        if (filter.entryType() != null) predicates.add(cb.equal(root.get("entryType"), filter.entryType()));
        if (filter.q() != null) predicates.add(DescriptionSearch.contains(cb, root.get("description"), filter.q()));
        return cb.and(predicates.toArray(Predicate[]::new));
    }

    public ReportResponse.Totals totals(UUID teamId, ReportFilter filter) {
        var cb = em.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        var root = query.from(TimeEntry.class);
        query.select(cb.tuple(cb.coalesce(cb.sumAsLong(root.get("durationSeconds")), 0L), cb.count(root)))
                .where(filter(cb, root, teamId, filter, false));
        var row = em.createQuery(query).getSingleResult();
        return new ReportResponse.Totals(row.get(0, Long.class), row.get(1, Long.class));
    }

    public ReportResponse.Page<ReportResponse.Bucket> breakdown(UUID teamId, ReportFilter filter,
            ReportGrouping grouping, int page, int size) {
        var cb = em.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        var root = query.from(TimeEntry.class);
        var dimensions = new ArrayList<Expression<?>>();
        if (grouping.day) dimensions.add(root.get("workDate"));
        if (grouping.member) {
            dimensions.add(root.get("user").get("id"));
            dimensions.add(root.get("user").get("displayName"));
        }
        if (grouping.project) {
            dimensions.add(root.get("project").get("id"));
            dimensions.add(root.get("project").get("name"));
            dimensions.add(root.get("project").get("archivedAt"));
        }
        var selections = new ArrayList<Selection<?>>(dimensions);
        selections.add(cb.coalesce(cb.sumAsLong(root.get("durationSeconds")), 0L));
        selections.add(cb.count(root));
        // IDs in the ordering make pagination stable even when names or totals match.
        query.select(cb.tuple(selections.toArray(Selection<?>[]::new))).where(filter(cb, root, teamId, filter, false))
                .groupBy(dimensions).orderBy(dimensions.stream().map(cb::asc).toList());
        var rows = em.createQuery(query).setFirstResult(page * size).setMaxResults(size + 1)
                .getResultList().stream().map(row -> bucket(row, grouping)).toList();
        return page(rows, page, size);
    }

    private ReportResponse.Bucket bucket(Tuple row, ReportGrouping grouping) {
        int index = 0;
        var day = grouping.day ? row.get(index++, java.time.LocalDate.class) : null;
        var userId = grouping.member ? row.get(index++, UUID.class) : null;
        var name = grouping.member ? row.get(index++, String.class) : null;
        var projectId = grouping.project ? row.get(index++, UUID.class) : null;
        var projectName = grouping.project ? row.get(index++, String.class) : null;
        var archived = grouping.project ? row.get(index++, java.time.Instant.class) : null;
        return new ReportResponse.Bucket(day, userId, name, projectId, projectName, archived,
                row.get(index++, Long.class), row.get(index, Long.class));
    }

    public List<TimeEntry> entries(UUID teamId, ReportFilter filter, boolean active,
            ReportEntrySort sort, boolean ascending, int page, int size) {
        var cb = em.getCriteriaBuilder();
        var query = cb.createQuery(TimeEntry.class);
        var root = query.from(TimeEntry.class);
        root.fetch("user");
        root.fetch("project");
        String field = switch (sort) {
            case WORK_DATE -> "workDate";
            case DURATION -> "durationSeconds";
            case CREATED_AT -> "createdAt";
        };
        query.select(root).where(filter(cb, root, teamId, filter, active)).orderBy(
                ascending ? cb.asc(root.get(field)) : cb.desc(root.get(field)), cb.asc(root.get("id")));
        return em.createQuery(query).setFirstResult(page * size).setMaxResults(size + 1).getResultList();
    }

    public List<TeamMember> members(UUID teamId, int page, int size) {
        return em.createQuery("select m from TeamMember m join fetch m.user where m.team.id = :teamId order by m.user.displayName, m.user.id", TeamMember.class)
                .setParameter("teamId", teamId).setFirstResult(page * size).setMaxResults(size + 1).getResultList();
    }

    public List<Project> projects(UUID teamId, int page, int size) {
        return em.createQuery("select p from Project p where p.team.id = :teamId order by p.name, p.id", Project.class)
                .setParameter("teamId", teamId).setFirstResult(page * size).setMaxResults(size + 1).getResultList();
    }

    public boolean containsMembers(UUID teamId, List<UUID> ids) {
        return ids.isEmpty() || em.createQuery("select count(m) from TeamMember m where m.team.id = :teamId and m.user.id in :ids", Long.class)
                .setParameter("teamId", teamId).setParameter("ids", ids).getSingleResult() == ids.stream().distinct().count();
    }

    public boolean containsProjects(UUID teamId, List<UUID> ids) {
        return ids.isEmpty() || em.createQuery("select count(p) from Project p where p.team.id = :teamId and p.id in :ids", Long.class)
                .setParameter("teamId", teamId).setParameter("ids", ids).getSingleResult() == ids.stream().distinct().count();
    }

    static <T> ReportResponse.Page<T> page(List<T> rows, int page, int size) {
        return new ReportResponse.Page<>(List.copyOf(rows.subList(0, Math.min(rows.size(), size))), page, size, rows.size() > size);
    }
}
