create sequence MissionEntity_SEQ start with 1 increment by 50;

create table MissionEntity (
    id bigint not null primary key,
    active boolean not null default true,
    points integer not null,
    title varchar(120) not null,
    description varchar(1000),
    constraint mission_points_positive check (points >= 1)
);
