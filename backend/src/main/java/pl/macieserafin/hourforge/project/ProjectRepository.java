package pl.macieserafin.hourforge.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByTeamIdOrderByCreatedAtDescIdAsc(UUID teamId);

    List<Project> findByTeamIdAndArchivedAtIsNullOrderByCreatedAtDescIdAsc(UUID teamId);

    Optional<Project> findByIdAndTeamId(UUID id, UUID teamId);
}
