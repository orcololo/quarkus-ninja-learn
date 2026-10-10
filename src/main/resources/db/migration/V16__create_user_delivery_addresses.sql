create sequence user_delivery_address_SEQ start with 1 increment by 50;

create table user_delivery_address (
    id bigint not null primary key,
    user_id bigint not null references GameUserEntity on delete cascade,
    label varchar(60) not null,
    recipient_name varchar(120) not null,
    phone varchar(25) not null,
    street varchar(160) not null,
    number varchar(30) not null,
    complement varchar(120),
    neighborhood varchar(100) not null,
    city varchar(120) not null,
    state varchar(100) not null,
    postal_code varchar(20) not null,
    country varchar(2) not null,
    delivery_instructions varchar(500),
    is_default boolean not null default false,
    created_at timestamp with time zone not null,
    constraint user_delivery_address_country_check check (country ~ '^[A-Z]{2}$')
);

create index user_delivery_address_user_created_idx
    on user_delivery_address (user_id, created_at, id);

create unique index user_delivery_address_one_default_idx
    on user_delivery_address (user_id)
    where is_default;
