create sequence restaurant_opening_period_SEQ start with 1 increment by 50;

alter table restaurant
    add column time_zone varchar(64);

create table restaurant_opening_period (
    id bigint not null primary key,
    restaurant_id bigint not null references restaurant on delete cascade,
    day_of_week varchar(9) not null
        check (day_of_week in ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
                               'FRIDAY', 'SATURDAY', 'SUNDAY')),
    opens_at time not null,
    closes_at time not null,
    constraint restaurant_opening_period_nonzero check (opens_at <> closes_at)
);

create index restaurant_opening_period_restaurant_day_idx
    on restaurant_opening_period (restaurant_id, day_of_week, opens_at);
