package com.clip2house.ledger.persona;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RelationshipRepository extends JpaRepository<Relationship, Relationship.Key> {
}
