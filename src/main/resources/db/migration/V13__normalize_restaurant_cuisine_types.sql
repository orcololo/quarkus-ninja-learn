update restaurant
set cuisine = case lower(trim(cuisine))
    when 'african' then 'AFRICAN'
    when 'american' then 'AMERICAN'
    when 'argentinian' then 'ARGENTINIAN'
    when 'brazilian' then 'BRAZILIAN'
    when 'caribbean' then 'CARIBBEAN'
    when 'chinese' then 'CHINESE'
    when 'ethiopian' then 'ETHIOPIAN'
    when 'filipino' then 'FILIPINO'
    when 'french' then 'FRENCH'
    when 'greek' then 'GREEK'
    when 'indian' then 'INDIAN'
    when 'indonesian' then 'INDONESIAN'
    when 'italian' then 'ITALIAN'
    when 'japanese' then 'JAPANESE'
    when 'korean' then 'KOREAN'
    when 'mediterranean' then 'MEDITERRANEAN'
    when 'mexican' then 'MEXICAN'
    when 'middle eastern' then 'MIDDLE_EASTERN'
    when 'middle_eastern' then 'MIDDLE_EASTERN'
    when 'peruvian' then 'PERUVIAN'
    when 'spanish' then 'SPANISH'
    when 'thai' then 'THAI'
    when 'turkish' then 'TURKISH'
    when 'vietnamese' then 'VIETNAMESE'
    when 'other' then 'OTHER'
    else 'OTHER'
end;

alter table restaurant
    add constraint restaurant_cuisine_check
        check (cuisine in (
            'AFRICAN', 'AMERICAN', 'ARGENTINIAN', 'BRAZILIAN', 'CARIBBEAN',
            'CHINESE', 'ETHIOPIAN', 'FILIPINO', 'FRENCH', 'GREEK', 'INDIAN',
            'INDONESIAN', 'ITALIAN', 'JAPANESE', 'KOREAN', 'MEDITERRANEAN',
            'MEXICAN', 'MIDDLE_EASTERN', 'PERUVIAN', 'SPANISH', 'THAI',
            'TURKISH', 'VIETNAMESE', 'OTHER'
        ));

insert into restaurant_search_outbox (id, aggregate_type, aggregate_id)
select nextval('restaurant_search_outbox_seq'), 'RESTAURANT', id
from restaurant;
