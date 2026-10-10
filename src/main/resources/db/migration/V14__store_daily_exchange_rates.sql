create table restaurant_exchange_rate_snapshot (
    id bigint primary key check (id = 1),
    rate_date date not null,
    fetched_at timestamp with time zone not null,
    rates_json text not null
);
