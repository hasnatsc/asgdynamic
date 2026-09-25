package com.asg.fabricerp.global.numbering;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NumberingSchemeRepository extends JpaRepository<NumberingScheme, Long> {

    Optional<NumberingScheme> findByOrganizationIdAndSeriesCode(Long organizationId, String seriesCode);

    List<NumberingScheme> findByOrganizationId(Long organizationId);

    Optional<NumberingScheme> findByOrganizationIdAndPrefix(Long organizationId, String prefix);

    /**
     * Writes a series' defaults unless the organization already has a row for it - or already uses
     * its prefix for another series, which {@code ON CONFLICT DO NOTHING} also absorbs. Either way the
     * caller reads the row back and decides; an insert racing another first use cannot fail.
     */
    @Modifying
    @Query(value = """
            INSERT INTO gbl_numbering_schemes
                (organization_id, series_code, prefix, pattern, sequence_width, reset_policy, counter_scope,
                 version, created_by, created_at, updated_by, updated_at)
            VALUES (:orgId, :seriesCode, :prefix, :pattern, :width, :reset, :scope, 0, :user, now(), :user, now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("orgId") Long organizationId, @Param("seriesCode") String seriesCode,
                       @Param("prefix") String prefix, @Param("pattern") String pattern, @Param("width") int width,
                       @Param("reset") String resetPolicy, @Param("scope") String counterScope,
                       @Param("user") String user);

    default void insertDefaults(Long organizationId, NumberSeries series, String user) {
        insertIfAbsent(organizationId, series.seriesCode(), series.defaultPrefix(), NumberPattern.DEFAULT,
            series.defaultWidth(), ResetPolicy.FINANCIAL_YEAR.name(), CounterScope.ORGANIZATION.name(), user);
    }
}
