package com.asg.fabricerp.profile;

import com.asg.fabricerp.security.FabricUser;
import com.asg.fabricerp.security.FabricUserRepository;
import com.asg.fabricerp.security.Role;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The signed-in user's own profile: their name and contact details, their photo, and what the
 * system knows of them (roles, home unit, sign-ins). Everything here acts on one user - the caller
 * - and never takes a user id from a request; administrators keep users on the Users screen.
 *
 * <p>The photo is stored only as {@link ProfilePhotoProcessor} makes it; {@code photo_version}
 * changes with each new one, so its URL does too and browsers may cache it for a year.
 */
@Service
public class ProfileService {

    static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final Pattern PHONE = Pattern.compile("^[+0-9()\\-\\s]{6,40}$");

    public record ProfileRequest(String fullName, String email, String phone, String designation, String department, String bio) { }

    /** A stored photo, as served. */
    public record StoredPhoto(byte[] bytes, long version) { }

    private final FabricUserRepository users;
    private final ProfilePhotoProcessor processor;
    private final NamedParameterJdbcTemplate jdbc;

    public ProfileService(FabricUserRepository users, ProfilePhotoProcessor processor, NamedParameterJdbcTemplate jdbc) {
        this.users = users;
        this.processor = processor;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public Map<String, Object> profile(Long userId) {
        FabricUser u = user(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("fullName", u.getFullName());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("designation", u.getDesignation());
        m.put("department", u.getDepartment());
        m.put("bio", u.getBio());
        m.put("headline", String.join(" · ", java.util.stream.Stream.of(blank(u.getDesignation()), blank(u.getDepartment()))
            .filter(Objects::nonNull).toList()));
        m.put("photoVersion", u.getPhotoVersion());
        m.put("photoUrl", u.getPhotoVersion() == null ? null : "/account/photo/" + u.getId() + "?v=" + u.getPhotoVersion());
        m.put("roles", u.getRoles().stream().filter(r -> Boolean.TRUE.equals(r.getActive())).map(Role::getName).sorted().toList());
        m.put("unrestricted", u.isUnrestricted());
        m.put("lastLoginAt", u.getLastLoginAt());
        m.put("passwordChangedAt", u.getPasswordChangedAt());
        m.put("memberSince", u.getCreatedAt());
        m.put("home", jdbc.queryForMap("""
            SELECT o.name AS organization, b.name AS "businessUnit", w.name AS store
            FROM sec_fabric_users u
            LEFT JOIN org_organizations o ON o.id = u.organization_id
            LEFT JOIN org_business_units b ON b.id = u.business_unit_id
            LEFT JOIN org_warehouses w ON w.id = u.warehouse_id
            WHERE u.id = :id
            """, new MapSqlParameterSource("id", userId)));
        m.put("photo", jdbc.query("""
            SELECT width, height, original_bytes, stored_bytes, updated_at FROM sec_user_photos WHERE user_id = :id
            """, new MapSqlParameterSource("id", userId), rs -> {
            if (!rs.next()) return null;
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("width", rs.getInt(1));
            p.put("height", rs.getInt(2));
            p.put("originalBytes", rs.getInt(3));
            p.put("storedBytes", rs.getInt(4));
            p.put("updatedAt", rs.getTimestamp(5).toLocalDateTime());
            return p;
        }));
        m.put("completeness", completeness(u));
        m.put("recentSignIns", jdbc.queryForList("""
            SELECT event, source_address AS "sourceAddress", occurred_at AS "occurredAt"
            FROM sec_access_log WHERE user_id = :id AND event IN ('LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'SESSION_ENDED', 'PASSWORD_CHANGED')
            ORDER BY occurred_at DESC LIMIT 8
            """, new MapSqlParameterSource("id", userId)));
        return m;
    }

    /** How much of the profile is filled in - photo, name, email, phone, designation, department. */
    static int completeness(FabricUser u) {
        List<Object> parts = Arrays.asList(u.getPhotoVersion(), blank(u.getFullName()), blank(u.getEmail()), blank(u.getPhone()),
            blank(u.getDesignation()), blank(u.getDepartment()));
        long filled = parts.stream().filter(Objects::nonNull).count();
        return (int) Math.round(filled * 100.0 / parts.size());
    }

    // ----------------------------------------------------------------------------------- write

    @Transactional
    public void update(Long userId, ProfileRequest r) {
        FabricUser u = user(userId);
        String name = blank(r.fullName());
        if (name == null) throw new IllegalArgumentException("Give your name");
        if (name.length() > 150) throw new IllegalArgumentException("Your name is longer than 150 characters");
        String email = blank(r.email());
        if (email != null && (email.length() > 150 || !EMAIL.matcher(email).matches())) {
            throw new IllegalArgumentException("That email address does not look right");
        }
        String phone = blank(r.phone());
        if (phone != null && !PHONE.matcher(phone).matches()) {
            throw new IllegalArgumentException("A phone number is digits, spaces, +, - and brackets");
        }
        u.setFullName(name);
        u.setEmail(email == null ? null : email.toLowerCase(Locale.ROOT));
        u.setPhone(phone);
        u.setDesignation(limit(blank(r.designation()), 100, "Designation"));
        u.setDepartment(limit(blank(r.department()), 100, "Department"));
        u.setBio(limit(blank(r.bio()), 500, "About you"));
        users.save(u);
    }

    /** Stores a new photo, optimized; returns the new version. */
    @Transactional
    public long setPhoto(Long userId, byte[] upload) {
        FabricUser u = user(userId);
        ProfilePhotoProcessor.Photo photo = processor.process(upload);
        MapSqlParameterSource p = new MapSqlParameterSource("id", userId).addValue("image", photo.image())
            .addValue("thumb", photo.thumbnail()).addValue("w", photo.width()).addValue("h", photo.height())
            .addValue("original", upload.length).addValue("stored", photo.image().length).addValue("at", LocalDateTime.now());
        jdbc.update("""
            INSERT INTO sec_user_photos (user_id, content_type, image, thumbnail, width, height, original_bytes, stored_bytes, updated_at)
            VALUES (:id, 'image/jpeg', :image, :thumb, :w, :h, :original, :stored, :at)
            ON CONFLICT (user_id) DO UPDATE SET image = EXCLUDED.image, thumbnail = EXCLUDED.thumbnail, width = EXCLUDED.width,
                height = EXCLUDED.height, original_bytes = EXCLUDED.original_bytes, stored_bytes = EXCLUDED.stored_bytes,
                updated_at = EXCLUDED.updated_at
            """, p);
        long version = Math.max(System.currentTimeMillis(), u.getPhotoVersion() == null ? 0 : u.getPhotoVersion() + 1);
        u.setPhotoVersion(version);
        users.save(u);
        return version;
    }

    @Transactional
    public void removePhoto(Long userId) {
        FabricUser u = user(userId);
        jdbc.update("DELETE FROM sec_user_photos WHERE user_id = :id", new MapSqlParameterSource("id", userId));
        u.setPhotoVersion(null);
        users.save(u);
    }

    /**
     * A user's photo for someone in {@code viewerOrganizationIds} to see - colleagues see each
     * other's; nobody sees another organization's. Empty when there is none, or it is not theirs to see.
     */
    @Transactional(readOnly = true)
    public Optional<StoredPhoto> photo(Long userId, boolean thumbnail, Set<Long> viewerOrganizationIds) {
        if (viewerOrganizationIds == null || viewerOrganizationIds.isEmpty()) return Optional.empty();
        return Optional.ofNullable(jdbc.query("""
            SELECT %s, u.photo_version FROM sec_user_photos p JOIN sec_fabric_users u ON u.id = p.user_id
            WHERE p.user_id = :id AND u.organization_id IN (:orgs) AND u.deleted = FALSE
            """.formatted(thumbnail ? "p.thumbnail" : "p.image"),
            new MapSqlParameterSource("id", userId).addValue("orgs", viewerOrganizationIds),
            rs -> rs.next() ? new StoredPhoto(rs.getBytes(1), rs.getLong(2)) : null));
    }

    // --------------------------------------------------------------------------------- helpers

    private FabricUser user(Long id) {
        return users.findById(id).filter(u -> !Boolean.TRUE.equals(u.getDeleted()))
            .orElseThrow(() -> new IllegalArgumentException("Your account was not found"));
    }

    private static String limit(String v, int max, String what) {
        if (v != null && v.length() > max) throw new IllegalArgumentException("%s is longer than %d characters".formatted(what, max));
        return v;
    }

    static String blank(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }
}
