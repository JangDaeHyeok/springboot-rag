package com.jdh.rag.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * search_logs 테이블 레포지토리.
 * 기본 save / saveAll 만 사용.
 */
public interface SearchLogJpaRepository extends JpaRepository<SearchLogEntity, Long> {
}
