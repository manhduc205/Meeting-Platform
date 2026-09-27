-- V6 has already been applied in deployed databases. LiveKit delivery retries
-- now handle the short StartEgress/INSERT race, so this table is obsolete.
DROP TABLE IF EXISTS pending_egress_webhook_events;
