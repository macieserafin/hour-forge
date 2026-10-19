package pl.macieserafin.hourforge.team;

import java.util.List;

public record TeamMembersResponse(List<TeamMemberResponse> content, int page, int size,
                                  long totalElements, int totalPages) {
}
