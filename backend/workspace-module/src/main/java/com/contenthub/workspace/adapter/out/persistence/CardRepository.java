package com.contenthub.workspace.adapter.out.persistence;

import com.contenthub.workspace.domain.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findByWorkspaceIdAndColumnIdAndDeletedAtIsNullOrderByPosition(UUID workspaceId, UUID columnId);
}
