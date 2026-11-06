-- =============================================================================
-- Testree — script de création de la base (PostgreSQL 18+, pour uuidv7())
-- Source : MLD v1 (docs/03-conception/mld/MLD.md), lui-même tiré du MCD v3.
-- Première migration Flyway (ADR-032), appliquée au démarrage du back.
-- Une fois appliquée, elle ne se modifie plus (Flyway vérifie son empreinte) :
-- toute évolution du schéma passe par un nouveau fichier V2__…, V3__…
--
-- Conventions : tables et colonnes en anglais, snake_case, au singulier ;
-- clé primaire « id » en UUID v7 (aléatoire mais rangé dans le temps) ; clé étrangère « <table>_id » ; dates « *_at » en
-- timestamptz ; contraintes nommées (pk_, fk_, uk_, ck_, ix_) pour des
-- messages d'erreur lisibles.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Comptes
-- -----------------------------------------------------------------------------

-- « user » est un mot réservé de PostgreSQL, d'où « app_user ».
CREATE TABLE app_user (
    id             uuid         DEFAULT uuidv7(),
    email          varchar(254) NOT NULL,
    display_name   varchar(100) NOT NULL,
    password_hash  varchar(100) NOT NULL,
    role           varchar(20)  NOT NULL,
    deactivated_at timestamptz,                         -- vide = compte actif
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT ck_app_user_role CHECK (role IN ('TESTER', 'MANAGER', 'SUPER_ADMIN'))
);
-- Unicité insensible à la casse : « Jean@x.fr » et « jean@x.fr » sont le même compte.
CREATE UNIQUE INDEX uk_app_user_email ON app_user (lower(email));

-- -----------------------------------------------------------------------------
-- Projets
-- -----------------------------------------------------------------------------

CREATE TABLE project (
    id               uuid         DEFAULT uuidv7(),
    name             varchar(100) NOT NULL,
    token_hash       char(64)     NOT NULL,             -- SHA-256 en hexadécimal, jamais le token
    token_created_at timestamptz  NOT NULL DEFAULT now(),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_project PRIMARY KEY (id),
    CONSTRAINT uk_project_name UNIQUE (name),
    CONSTRAINT uk_project_token_hash UNIQUE (token_hash)  -- recherche du projet à chaque envoi
);

-- Association porteuse APPARTENIR (n ↔ n, avec date d'ajout).
CREATE TABLE user_project (
    user_id    uuid        NOT NULL,
    project_id uuid        NOT NULL,
    added_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_project PRIMARY KEY (user_id, project_id),
    CONSTRAINT fk_user_project_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_project_project FOREIGN KEY (project_id)
        REFERENCES project (id) ON DELETE CASCADE
);
-- La clé primaire indexe (user_id, project_id) ; il faut aussi l'autre sens
-- pour « les membres d'un projet ».
CREATE INDEX ix_user_project_project ON user_project (project_id);

CREATE TABLE invitation (
    id            uuid         DEFAULT uuidv7(),
    email         varchar(254) NOT NULL,
    proposed_role varchar(20)  NOT NULL,
    token_hash    char(64)     NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    expires_at    timestamptz  NOT NULL,                -- création + 72 h, fixé par l'appli
    used_at       timestamptz,                          -- vide = pas encore utilisée
    invited_by    uuid,                                 -- vide si l'émetteur a été supprimé
    CONSTRAINT pk_invitation PRIMARY KEY (id),
    CONSTRAINT uk_invitation_token_hash UNIQUE (token_hash),
    -- Le rôle SUPER_ADMIN n'est jamais donné depuis l'appli (ADR-029).
    CONSTRAINT ck_invitation_role CHECK (proposed_role IN ('TESTER', 'MANAGER')),
    CONSTRAINT ck_invitation_expiry CHECK (expires_at > created_at),
    CONSTRAINT fk_invitation_invited_by FOREIGN KEY (invited_by)
        REFERENCES app_user (id) ON DELETE SET NULL
);

-- DONNER ACCÈS À (n ↔ n). Le minimum « au moins un projet par invitation »
-- (cardinalité 1,n) n'est pas exprimable en SQL simple : il est vérifié par
-- le service qui crée l'invitation.
CREATE TABLE invitation_project (
    invitation_id uuid   NOT NULL,
    project_id    uuid   NOT NULL,
    CONSTRAINT pk_invitation_project PRIMARY KEY (invitation_id, project_id),
    CONSTRAINT fk_invitation_project_invitation FOREIGN KEY (invitation_id)
        REFERENCES invitation (id) ON DELETE CASCADE,
    CONSTRAINT fk_invitation_project_project FOREIGN KEY (project_id)
        REFERENCES project (id) ON DELETE CASCADE
);
CREATE INDEX ix_invitation_project_project ON invitation_project (project_id);

-- -----------------------------------------------------------------------------
-- Ingestion des résultats
-- -----------------------------------------------------------------------------

CREATE TABLE script (
    id                uuid         DEFAULT uuidv7(),
    project_id        uuid         NOT NULL,
    full_path         varchar(500) NOT NULL,            -- ex. appli.module_A.Script_01
    first_seen_at     timestamptz  NOT NULL,
    failing_since     timestamptz,                      -- survit à la purge (ADR-013)
    failing_run_count integer      NOT NULL DEFAULT 0,  -- survit à la purge (ADR-013)
    CONSTRAINT pk_script PRIMARY KEY (id),
    -- L'index de cette contrainte sert aussi « les scripts d'un projet ».
    CONSTRAINT uk_script_project_path UNIQUE (project_id, full_path),
    CONSTRAINT ck_script_failing_count CHECK (failing_run_count >= 0),
    CONSTRAINT fk_script_project FOREIGN KEY (project_id)
        REFERENCES project (id) ON DELETE CASCADE
);

-- Une EXÉCUTION = un build Jenkins reçu.
CREATE TABLE test_run (
    id                  uuid         DEFAULT uuidv7(),
    project_id          uuid         NOT NULL,
    job_name            varchar(300) NOT NULL,
    build_number        integer      NOT NULL,
    build_url           varchar(500) NOT NULL,
    suite_name          varchar(200) NOT NULL,
    git_commit          varchar(40),
    git_previous_commit varchar(40),
    git_branch          varchar(200),
    started_at          timestamptz  NOT NULL,
    duration_ms         bigint       NOT NULL,
    received_at         timestamptz  NOT NULL DEFAULT now(),
    payload_bytes       integer      NOT NULL,
    config_error        varchar(2000),                  -- méthode de config de suite en échec
    CONSTRAINT pk_test_run PRIMARY KEY (id),
    -- Clé de doublon (ADR-026, précision MCD v3).
    CONSTRAINT uk_test_run_build UNIQUE (project_id, job_name, build_number, suite_name, started_at),
    CONSTRAINT ck_test_run_build_number CHECK (build_number > 0),
    CONSTRAINT ck_test_run_build_url CHECK (build_url ~ '^https?://'),  -- anti-XSS (lien affiché)
    CONSTRAINT ck_test_run_duration CHECK (duration_ms >= 0),
    CONSTRAINT ck_test_run_payload CHECK (payload_bytes >= 0),
    CONSTRAINT fk_test_run_project FOREIGN KEY (project_id)
        REFERENCES project (id) ON DELETE CASCADE
);
-- Frise et listes de runs d'un projet, triées par date.
CREATE INDEX ix_test_run_project_started ON test_run (project_id, started_at);
-- Purge de nuit : « tout ce qui a été reçu il y a plus de 60 jours ».
CREATE INDEX ix_test_run_received ON test_run (received_at);

CREATE TABLE error_signature (
    id               uuid          DEFAULT uuidv7(),
    project_id       uuid          NOT NULL,
    hash             char(64)      NOT NULL,            -- SHA-256 (ADR-023, règle N7)
    action           varchar(100),
    error_code       varchar(20),
    location         varchar(500),                      -- emplacement sans la ligne
    normalized_text  varchar(1000) NOT NULL,
    first_seen_at    timestamptz   NOT NULL,
    last_seen_at     timestamptz   NOT NULL,
    occurrence_count integer       NOT NULL DEFAULT 1,
    label            varchar(20),                       -- posée par un testeur
    label_by         uuid,
    label_at         timestamptz,
    guess            varchar(20),                       -- devinée par les règles
    guess_reason     varchar(500),
    CONSTRAINT pk_error_signature PRIMARY KEY (id),
    CONSTRAINT uk_error_signature_hash UNIQUE (project_id, hash),
    CONSTRAINT ck_error_signature_label CHECK (label IN ('APPLI', 'TEST', 'ENVIRONNEMENT')),
    CONSTRAINT ck_error_signature_guess CHECK (guess IN ('APPLI', 'TEST', 'ENVIRONNEMENT')),
    -- Une étiquette a toujours une date ; l'auteur, lui, peut disparaître.
    CONSTRAINT ck_error_signature_label_at CHECK ((label IS NULL) = (label_at IS NULL)),
    CONSTRAINT ck_error_signature_seen CHECK (last_seen_at >= first_seen_at),
    CONSTRAINT ck_error_signature_count CHECK (occurrence_count >= 1),
    CONSTRAINT fk_error_signature_project FOREIGN KEY (project_id)
        REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_error_signature_label_by FOREIGN KEY (label_by)
        REFERENCES app_user (id) ON DELETE SET NULL
);

-- RÉSULTAT : la brique atomique (un script dans un build).
CREATE TABLE run_result (
    id             uuid          DEFAULT uuidv7(),
    test_run_id    uuid          NOT NULL,
    script_id      uuid          NOT NULL,
    status         varchar(10)   NOT NULL,
    started_at     timestamptz   NOT NULL,
    duration_ms    bigint        NOT NULL,
    error_message  varchar(2000),
    error_location varchar(500),                        -- « script:ligne », garde la ligne
    steps_done     integer,
    ats_call_stack text,                                -- 10 lignes max, échecs seulement
    signature_id   uuid,
    CONSTRAINT pk_run_result PRIMARY KEY (id),
    -- Un seul résultat par script et par build (cas L09 : fusion, pire statut gardé).
    CONSTRAINT uk_run_result_run_script UNIQUE (test_run_id, script_id),
    CONSTRAINT ck_run_result_status CHECK (status IN ('PASS', 'FAIL', 'SKIP')),
    CONSTRAINT ck_run_result_duration CHECK (duration_ms >= 0),
    CONSTRAINT ck_run_result_steps CHECK (steps_done >= 0),
    -- Un résultat vert n'a jamais de cause d'échec.
    CONSTRAINT ck_run_result_pass_no_signature CHECK (status <> 'PASS' OR signature_id IS NULL),
    CONSTRAINT fk_run_result_test_run FOREIGN KEY (test_run_id)
        REFERENCES test_run (id) ON DELETE CASCADE,
    -- RESTRICT : un script ne peut pas être supprimé tant qu'il a des résultats
    -- (cardinalité 1,n du MCD). La purge supprime d'abord les vieux builds,
    -- puis les scripts restés sans résultat.
    CONSTRAINT fk_run_result_script FOREIGN KEY (script_id)
        REFERENCES script (id) ON DELETE RESTRICT,
    -- SET NULL par sécurité ; en pratique une signature survit à ses résultats.
    CONSTRAINT fk_run_result_signature FOREIGN KEY (signature_id)
        REFERENCES error_signature (id) ON DELETE SET NULL
);
-- PostgreSQL n'indexe pas les clés étrangères tout seul.
-- (test_run_id est déjà couvert par uk_run_result_run_script.)
CREATE INDEX ix_run_result_script ON run_result (script_id, started_at);  -- frise d'un script
CREATE INDEX ix_run_result_signature ON run_result (signature_id);       -- « N autres tests »

CREATE TABLE changed_file (
    id          uuid         DEFAULT uuidv7(),
    test_run_id uuid         NOT NULL,
    path        varchar(500) NOT NULL,                  -- sans lien vers le script (MCD § 5)
    CONSTRAINT pk_changed_file PRIMARY KEY (id),
    CONSTRAINT fk_changed_file_test_run FOREIGN KEY (test_run_id)
        REFERENCES test_run (id) ON DELETE CASCADE
);
CREATE INDEX ix_changed_file_test_run ON changed_file (test_run_id);

-- Journal d'ingestion : écrit même quand l'envoi est refusé (token inconnu → sans projet).
-- Jamais de token dans cette table (OWASP A09).
CREATE TABLE ingest_log (
    id           uuid         DEFAULT uuidv7(),
    project_id   uuid,
    received_at  timestamptz  NOT NULL DEFAULT now(),
    http_status  smallint     NOT NULL,
    outcome      varchar(20)  NOT NULL,
    reason       varchar(500),
    job_name     varchar(300),
    build_number integer,
    CONSTRAINT pk_ingest_log PRIMARY KEY (id),
    CONSTRAINT ck_ingest_log_outcome CHECK (outcome IN ('ACCEPTE', 'DEJA_RECU', 'VIDE', 'REFUSE')),
    CONSTRAINT ck_ingest_log_http CHECK (http_status BETWEEN 100 AND 599),
    CONSTRAINT fk_ingest_log_project FOREIGN KEY (project_id)
        REFERENCES project (id) ON DELETE SET NULL
);
CREATE INDEX ix_ingest_log_project_received ON ingest_log (project_id, received_at);
CREATE INDEX ix_ingest_log_received ON ingest_log (received_at);  -- purge à 60 jours
