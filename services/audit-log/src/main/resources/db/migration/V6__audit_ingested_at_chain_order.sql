WITH RECURSIVE audit_chain(id, hash, depth) AS (
    SELECT id, hash, 1
    FROM audit_events
    WHERE previous_hash = repeat('0', 64)
    UNION ALL
    SELECT child.id, child.hash, audit_chain.depth + 1
    FROM audit_events child
    JOIN audit_chain ON child.previous_hash = audit_chain.hash
    WHERE child.id <> audit_chain.id
),
ranked_events AS (
    SELECT id, row_number() OVER (ORDER BY depth, id) AS position
    FROM audit_chain
),
base_time AS (
    SELECT COALESCE(MIN(ingested_at), NOW()) AS started_at
    FROM audit_events
)
UPDATE audit_events
SET ingested_at = base_time.started_at + ranked_events.position * INTERVAL '1 microsecond'
FROM ranked_events, base_time
WHERE audit_events.id = ranked_events.id;
