-- ==================================================================== legacy invoices, given their own copies
-- Why this file exists: the two backfills below first lived at the end of V10, added in a later commit than
-- the one that introduced that file. Flyway records a checksum for each applied version, so a migration
-- edited after the fact is a startup failure on every database that had already run it - and the failure
-- names nothing near its cause, because the EntityManagerFactory simply cannot be built. V10 is therefore
-- back to the bytes it had when it merged (checksum 1708328131, which is what every database that ran it
-- first has on record), and this version carries the same statements forward. See also V13, which exists
-- for the identical reason, and `docs/setup.md`.
--
-- What it does: before the snapshot columns existed, an invoice read its layout from the live template row.
-- That made a listing page dereference a lazily loaded association after its transaction had closed - a
-- hard 500 for every customer on an upgraded database - and it meant that editing a template changed what
-- an old document appeared to have said. Copying the current values into the snapshot columns once, at
-- upgrade time, ends both: from here a snapshot is always present, and it is the text the invoice was
-- printed with on the day these columns arrived.
--
-- Safe to replay. Every assignment is a COALESCE over the column it writes, so a row that already has a
-- snapshot keeps it: a database that ran these statements as part of the edited V10 changes nothing here,
-- and a database that never ran them gains exactly what it was missing.

UPDATE invoices i
   SET template_name_snapshot   = COALESCE(i.template_name_snapshot, t.name),
       template_style_snapshot  = COALESCE(i.template_style_snapshot, t.style_key),
       template_accent_snapshot = COALESCE(i.template_accent_snapshot, t.accent_color)
  FROM bill_templates t
 WHERE i.template_id = t.id;

UPDATE invoices i
   SET template_name_snapshot          = COALESCE(i.template_name_snapshot, c.name),
       template_style_snapshot          = COALESCE(i.template_style_snapshot, c.style_key),
       template_accent_snapshot         = COALESCE(i.template_accent_snapshot, c.accent_color),
       template_font_snapshot           = COALESCE(i.template_font_snapshot, c.font_key),
       template_density_snapshot        = COALESCE(i.template_density_snapshot, c.layout_density),
       template_terms_snapshot          = COALESCE(i.template_terms_snapshot, c.payment_terms),
       template_footer_snapshot         = COALESCE(i.template_footer_snapshot, c.footer_text),
       template_bank_snapshot           = COALESCE(i.template_bank_snapshot, c.bank_details),
       template_show_logo_snapshot      = COALESCE(i.template_show_logo_snapshot, c.show_logo),
       template_signature_label_snapshot = COALESCE(i.template_signature_label_snapshot, c.signature_label),
       template_signature_name_snapshot = COALESCE(i.template_signature_name_snapshot, c.signature_name)
  FROM custom_invoice_templates c
 WHERE i.custom_template_id = c.id;
