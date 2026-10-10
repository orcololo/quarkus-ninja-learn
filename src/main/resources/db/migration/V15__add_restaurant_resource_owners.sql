alter table restaurant
    add column owner_subject varchar(255);

alter table restaurant_menu_item
    add column owner_subject varchar(255);
