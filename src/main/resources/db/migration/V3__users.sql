-- Logins and their flat authority set. See FabricUser's javadoc for why there is no
-- Role/Permission table yet: authorization is declared with @PreAuthorize on each
-- controller method, so a role/permission table would have no request-time checker to
-- feed — it would just be an unused list of names until DynamicAuthorizationManager's
-- fail-open pattern (or something like it) is deliberately reintroduced.

CREATE TABLE sec_fabric_users (
    id                  BIGSERIAL    PRIMARY KEY,
    organization_id     BIGINT       NOT NULL,
    username            VARCHAR(80)  NOT NULL,
    password_hash       VARCHAR(100) NOT NULL,
    full_name           VARCHAR(150),
    business_unit_id    BIGINT       NOT NULL,
    business_unit_code  VARCHAR(4)   NOT NULL,
    warehouse_id        BIGINT,
    account_locked      BOOLEAN      NOT NULL DEFAULT FALSE,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    version             BIGINT,
    created_by          VARCHAR(100),
    created_at          TIMESTAMP,
    updated_by          VARCHAR(100),
    updated_at          TIMESTAMP,

    CONSTRAINT uk_fab_user_username UNIQUE (username)
);

CREATE INDEX ix_fab_user_org ON sec_fabric_users (organization_id);

-- Element collection backing FabricUser.authorities.
CREATE TABLE sec_fabric_user_authorities (
    user_id   BIGINT      NOT NULL,
    authority VARCHAR(60) NOT NULL,

    CONSTRAINT fk_fab_user_authority_user FOREIGN KEY (user_id)
        REFERENCES sec_fabric_users (id) ON DELETE CASCADE,
    CONSTRAINT pk_fab_user_authority PRIMARY KEY (user_id, authority)
);
-- The primary key above already covers user_id as its leading column, so
-- "every row for this user" (what loadUserByUsername needs) is index-served without
-- a second index.
