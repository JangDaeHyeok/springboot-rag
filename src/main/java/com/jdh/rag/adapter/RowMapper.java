package com.jdh.rag.adapter;

/**
 * 네이티브 쿼리 Object[] 행을 도메인 타입으로 변환하는 공용 유틸.
 * PgDocumentAdapter, PgSearchAnalyticsAdapter 등 같은 패키지 어댑터에서만 사용한다.
 */
final class RowMapper {

    private RowMapper() {}

    static String str(Object o) {
        return o != null ? o.toString() : null;
    }

    static long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    static Double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }
}