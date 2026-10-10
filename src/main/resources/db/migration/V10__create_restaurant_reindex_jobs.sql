create sequence restaurant_reindex_job_SEQ start with 1 increment by 50;

create table restaurant_reindex_job (
    id bigint not null primary key,
    restaurant_after_id bigint not null default 0,
    menu_item_after_id bigint not null default 0,
    restaurants_queued bigint not null default 0,
    menu_items_queued bigint not null default 0,
    restaurants_done boolean not null default false,
    menu_items_done boolean not null default false,
    complete boolean not null default false
);

create index restaurant_reindex_job_pending_idx
    on restaurant_reindex_job (id)
    where complete = false;
