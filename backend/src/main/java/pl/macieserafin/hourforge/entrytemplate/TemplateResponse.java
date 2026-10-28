package pl.macieserafin.hourforge.entrytemplate;

import java.util.UUID;

/** {@code tagArchived} tells the client that the template's tag cannot be put on a new entry any more. */
public record TemplateResponse(UUID id, String name, UUID projectId, String description, UUID tagId, String tagName,
                               boolean tagArchived, String jiraIssueId, String jiraIssueKey) {
    static TemplateResponse of(EntryTemplate template) {
        var tag = template.getTag();
        return new TemplateResponse(template.getId(), template.getName(), template.getProjectId(), template.getDescription(),
                tag == null ? null : tag.getId(), tag == null ? null : tag.getName(), tag != null && tag.getArchivedAt() != null,
                template.getJiraIssueId(), template.getJiraIssueKey());
    }
}
