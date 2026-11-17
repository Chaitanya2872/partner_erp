-- Lead registration: when the project type is Other, the registrant says what it is. The text is only kept for
-- OTHER (existing OTHER rows keep a NULL until someone edits them; the application requires it on every save).
ALTER TABLE leads ADD COLUMN project_type_other varchar(100);
ALTER TABLE leads ADD CONSTRAINT ck_leads_project_type_other
    CHECK (project_type_other IS NULL OR (project_type = 'OTHER' AND btrim(project_type_other) <> ''));
