CREATE OR REPLACE FUNCTION ensure_audit_partition(month_date DATE)
RETURNS TEXT AS $$
DECLARE
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
BEGIN
    partition_name := 'audit_events_' || TO_CHAR(month_date, 'YYYY_MM');
    start_date := DATE_TRUNC('month', month_date);
    end_date := start_date + INTERVAL '1 month';

    IF NOT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON c.relnamespace = n.oid
        WHERE n.nspname = 'public' AND c.relname = partition_name
    ) THEN
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF audit_events FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
    END IF;

    RETURN partition_name;
END;
$$ LANGUAGE plpgsql;

SELECT ensure_audit_partition(CURRENT_DATE::DATE);
SELECT ensure_audit_partition((CURRENT_DATE + INTERVAL '1 month')::DATE);
