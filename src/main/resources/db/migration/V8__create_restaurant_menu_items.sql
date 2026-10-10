create sequence restaurant_menu_item_SEQ start with 1 increment by 50;

create table restaurant_menu_item (
    id bigint not null primary key,
    restaurant_id bigint not null references restaurant on delete cascade,
    name varchar(160) not null,
    description varchar(2000),
    category varchar(100),
    price numeric(12, 3) not null check (price >= 0),
    currency varchar(3) not null check (currency ~ '^[A-Z]{3}$'),
    is_available boolean not null default true
);

create index restaurant_menu_item_restaurant_category_name_idx
    on restaurant_menu_item (restaurant_id, category, name, id);
