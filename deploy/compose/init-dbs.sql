-- Initialize separate PostgreSQL databases for each service (ADR-0001)
CREATE DATABASE dayclose_auth;
CREATE DATABASE dayclose_inventory;
CREATE DATABASE dayclose_commitment;
CREATE DATABASE dayclose_knowledge;
CREATE DATABASE dayclose_notification;
CREATE DATABASE dayclose_voice;

-- Enable extensions in appropriate databases
\c dayclose_inventory
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c dayclose_knowledge
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";

\c dayclose_auth
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c dayclose_voice
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
