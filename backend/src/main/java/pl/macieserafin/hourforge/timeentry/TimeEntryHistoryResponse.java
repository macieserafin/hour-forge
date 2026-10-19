package pl.macieserafin.hourforge.timeentry;

import java.util.List;

public record TimeEntryHistoryResponse(List<TimeEntryHistoryItem> content, int page, int size,
                                       long totalElements, int totalPages) {
}
