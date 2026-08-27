package com.marmore.api.imageedit.storage;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio JPA dos metadados das imagens geradas. */
public interface GeneratedImageRepository extends JpaRepository<GeneratedImage, UUID> {}
