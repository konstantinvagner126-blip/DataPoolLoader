select
    src.id * 100 + replica.seq as id,
    src.payload || '-x18-' || replica.seq as payload,
    src.source_name
from datapool_manual_big.source_5 src
cross join generate_series(1, 18) as replica(seq)
