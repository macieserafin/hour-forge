package pl.macieserafin.hourforge.tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TagRepository extends JpaRepository<Tag, UUID> {
    List<Tag> findByTeamIdOrderByNameAscIdAsc(UUID teamId);

    List<Tag> findByTeamIdAndArchivedAtIsNullOrderByNameAscIdAsc(UUID teamId);

    Optional<Tag> findByIdAndTeamId(UUID id, UUID teamId);

    boolean existsByTeamIdAndNameIgnoreCaseAndIdNot(UUID teamId, String name, UUID id);

    @Modifying
    @Query("delete from Tag tag where tag.team.id = :teamId")
    int deleteAllByTeamId(UUID teamId);
}
