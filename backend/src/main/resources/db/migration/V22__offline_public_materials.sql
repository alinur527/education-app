ALTER TABLE materials ADD COLUMN offline_allowed BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE materials ADD COLUMN offline_rights_basis VARCHAR(1000);
