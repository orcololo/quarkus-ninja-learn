create sequence NinjaMissionEntity_SEQ start with 1 increment by 50;
create sequence PointTransactionEntity_SEQ start with 1 increment by 50;

create table NinjaMissionEntity (
    id bigint not null primary key,
    ninja_id bigint not null,
    mission_id bigint not null,
    status varchar(16) not null,
    assigned_at timestamp with time zone not null,
    submitted_at timestamp with time zone,
    decided_at timestamp with time zone,
    constraint ninja_mission_status_check
        check (status in ('ASSIGNED', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    constraint ninja_mission_assignment_unique unique (ninja_id, mission_id),
    constraint ninja_mission_ninja_fk foreign key (ninja_id) references NinjaEntity,
    constraint ninja_mission_mission_fk foreign key (mission_id) references MissionEntity
);

create table PointTransactionEntity (
    id bigint not null primary key,
    ninja_id bigint not null,
    ninja_mission_id bigint not null,
    points integer not null,
    granted_at timestamp with time zone not null,
    constraint point_transaction_points_positive check (points > 0),
    constraint point_transaction_assignment_unique unique (ninja_mission_id),
    constraint point_transaction_ninja_fk foreign key (ninja_id) references NinjaEntity,
    constraint point_transaction_mission_fk foreign key (ninja_mission_id) references NinjaMissionEntity
);
