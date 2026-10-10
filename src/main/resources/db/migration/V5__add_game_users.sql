create sequence GameUserEntity_SEQ start with 1 increment by 50;

create table GameUserEntity (
    id bigint not null primary key,
    subject varchar(128) not null,
    username varchar(64) not null,
    email varchar(254) not null,
    level varchar(16) not null,
    constraint game_user_level_check check (level in ('BASIC', 'PREMIUM', 'ADMIN')),
    constraint game_user_subject_unique unique (subject),
    constraint game_user_username_unique unique (username),
    constraint game_user_email_unique unique (email)
);

create unique index game_user_username_lower_unique on GameUserEntity (lower(username));
create unique index game_user_email_lower_unique on GameUserEntity (lower(email));

insert into GameUserEntity (id, subject, username, email, level)
select id, cast(id as varchar), 'legacy-ninja-' || id, 'legacy-ninja-' || id || '@example.invalid', 'BASIC'
from NinjaEntity;

alter table NinjaEntity add column owner_user_id bigint;
update NinjaEntity set owner_user_id = id;
alter table NinjaEntity
    add constraint ninja_owner_user_fk foreign key (owner_user_id) references GameUserEntity;
create index ninja_owner_user_idx on NinjaEntity (owner_user_id);

select setval(
    'GameUserEntity_SEQ',
    (select coalesce(max(id), 0) + 1 from GameUserEntity),
    false
);
