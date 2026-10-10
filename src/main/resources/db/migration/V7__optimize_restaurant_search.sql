alter table restaurant
    add column average_rating double precision not null default 0,
    add column review_count bigint not null default 0;

update restaurant r
set average_rating = reviews.average_rating,
    review_count = reviews.review_count
from (
    select restaurant_id, avg(rating)::double precision as average_rating, count(*) as review_count
    from restaurant_review
    group by restaurant_id
) reviews
where r.id = reviews.restaurant_id;

alter table restaurant_search_outbox
    drop column event_type,
    drop column document;
