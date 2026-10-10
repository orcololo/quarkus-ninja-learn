create sequence restaurant_SEQ start with 1 increment by 50;
create sequence restaurant_review_SEQ start with 1 increment by 50;
create sequence restaurant_search_outbox_SEQ start with 1 increment by 50;

create table restaurant (
    id bigint not null primary key,
    name varchar(160) not null,
    description varchar(2000),
    address varchar(255) not null,
    city varchar(120) not null,
    cuisine varchar(100) not null,
    latitude double precision not null,
    longitude double precision not null,
    constraint restaurant_latitude_check check (latitude between -90 and 90),
    constraint restaurant_longitude_check check (longitude between -180 and 180)
);

create index restaurant_city_idx on restaurant (city);
create index restaurant_cuisine_idx on restaurant (cuisine);

create table restaurant_review (
    id bigint not null primary key,
    restaurant_id bigint not null references restaurant on delete cascade,
    reviewer_subject text not null,
    rating integer not null check (rating between 1 and 5),
    comment varchar(2000),
    created_at timestamp with time zone not null,
    constraint restaurant_review_reviewer_unique unique (restaurant_id, reviewer_subject)
);

create index restaurant_review_restaurant_created_idx
    on restaurant_review (restaurant_id, created_at desc);

create table restaurant_search_outbox (
    id bigint not null primary key,
    restaurant_id bigint not null,
    event_type varchar(8) not null check (event_type in ('UPSERT', 'DELETE')),
    document text,
    constraint restaurant_outbox_document_check
        check ((event_type = 'UPSERT' and document is not null)
            or (event_type = 'DELETE' and document is null))
);
