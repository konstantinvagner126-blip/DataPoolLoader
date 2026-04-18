select
    src.id * 100 + replica.seq as id,
    src.payload || '-x54-' || replica.seq as payload,
    src.source_name
from datapool_manual_big.source_1 src
cross join generate_series(1, 54) as replica(seq)
