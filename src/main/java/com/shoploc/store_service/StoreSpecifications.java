package com.shoploc.store_service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class StoreSpecifications {

  private StoreSpecifications() {}

  public static Specification<Store> withFilters(String query, List<String> categories) {
    return (root, cq, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (query != null && !query.isBlank()) {
        String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        predicates.add(cb.like(cb.lower(root.get("name")), pattern));
      }

      if (categories != null && !categories.isEmpty()) {
        List<String> cleanedCategories =
            categories.stream()
                .filter(c -> c != null && !c.isBlank())
                .flatMap(c -> java.util.Arrays.stream(c.split(",")))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();

        if (!cleanedCategories.isEmpty()) {
          predicates.add(cb.lower(root.get("category")).in(cleanedCategories));
        }
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
