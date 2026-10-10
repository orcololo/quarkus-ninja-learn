create index ninja_village_idx on NinjaEntity (village_id);
create index point_transaction_ninja_granted_idx
    on PointTransactionEntity (ninja_id, granted_at desc, id desc);
