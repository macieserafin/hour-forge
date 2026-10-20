package pl.macieserafin.hourforge.entrytemplate;

import java.util.UUID;

public record TemplateResponse(UUID id, String name, UUID projectId, String description, String jiraIssueId, String jiraIssueKey) {
    static TemplateResponse of(EntryTemplate template) {
        return new TemplateResponse(template.getId(), template.getName(), template.getProjectId(), template.getDescription(),
                template.getJiraIssueId(), template.getJiraIssueKey());
    }
}
