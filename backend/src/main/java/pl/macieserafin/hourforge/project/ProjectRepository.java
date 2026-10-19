package pl.macieserafin.hourforge.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByTeamIdOrderByCreatedAtDescIdAsc(UUID teamId);

    List<Project> findByTeamIdAndArchivedAtIsNullOrderByCreatedAtDescIdAsc(UUID teamId);

    Optional<Project> findByIdAndTeamId(UUID id, UUID teamId);

    long countByTeamId(UUID teamId);

    @Modifying
    @Query("delete from Project project where project.team.id = :teamId")
    int deleteAllByTeamId(UUID teamId);
}
