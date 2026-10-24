-- A lead's status columns go together, as LeadService and LeadConversionService write them; the
-- database backs those rules up. A reason only with UNQUALIFIED (which needs one), the time and
-- the contact of a conversion only with CONVERTED (which needs both), its company and deal
-- (optional) only with them.
ALTER TABLE leads
  ADD CONSTRAINT ck_leads_unqualified_reason
    CHECK ((status = 'UNQUALIFIED') = (unqualified_reason IS NOT NULL)),
  ADD CONSTRAINT ck_leads_converted
    CHECK ((status = 'CONVERTED') = (converted_at IS NOT NULL)
      AND (status = 'CONVERTED') = (converted_contact_id IS NOT NULL)),
  ADD CONSTRAINT ck_leads_converted_links
    CHECK (status = 'CONVERTED' OR (converted_company_id IS NULL AND converted_deal_id IS NULL));
