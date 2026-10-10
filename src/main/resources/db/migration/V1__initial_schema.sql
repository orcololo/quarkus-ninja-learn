create sequence NinjaEntity_SEQ start with 1 increment by 50;
create sequence VillageEntity_SEQ start with 1 increment by 50;
create table NinjaEntity (
    id bigint not null primary key,
    age integer not null,
    level smallint check (level between 0 and 3),
    name varchar(255),
    village_id bigint
);

create table VillageEntity (
    id bigint not null primary key,
    population integer,
    type smallint check (type between 0 and 4),
    description varchar(255),
    location varchar(255),
    name varchar(255),
    leader_id bigint
);

alter table NinjaEntity
    add constraint ninja_village_fk foreign key (village_id) references VillageEntity;

alter table VillageEntity
    add constraint village_leader_fk foreign key (leader_id) references NinjaEntity;

insert into VillageEntity (id, name, location, description, leader_id, population, type)
values (1, 'Hidden Leaf Village', 'Land of Fire', 'A thriving ninja village', null, 0, 4);

alter sequence VillageEntity_SEQ restart with 2;
