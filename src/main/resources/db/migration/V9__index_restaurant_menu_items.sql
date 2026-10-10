alter table restaurant_search_outbox
    add column aggregate_type varchar(16) not null default 'RESTAURANT',
    add column aggregate_id bigint;

update restaurant_search_outbox
set aggregate_id = restaurant_id;

alter table restaurant_search_outbox
    alter column aggregate_id set not null,
    alter column aggregate_type drop default,
    drop column restaurant_id,
    add constraint restaurant_search_outbox_aggregate_type_check
        check (aggregate_type in ('RESTAURANT', 'MENU_ITEM'));
