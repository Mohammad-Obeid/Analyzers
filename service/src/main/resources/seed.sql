-- CBC configuration seed. Spring runs this after schema.sql on startup.
-- Safe to rerun: existing rows are preserved. No patients, samples, or results are stored here.
-- The DHIS2 UIDs below belong to the configured local CBC program; change them for another DHIS2 instance.

-- ============================================================================
-- 1. DHIS2 connection, program, stage, and organisation unit
-- ============================================================================
INSERT INTO dhis2_connection (id, environment, base_url, username) VALUES
    (1, 'LOCAL', 'http://localhost:8082', 'admin')
ON CONFLICT DO NOTHING;

INSERT INTO dhis2_program (uid, connection_id, code, name, program_type) VALUES
    ('CAB738DA06F', 1, 'CBC_RESULTS', 'CBC Laboratory Results', 'WITHOUT_REGISTRATION')
ON CONFLICT DO NOTHING;

INSERT INTO dhis2_program_stage (uid, program_uid, name, barcode_data_element_uid) VALUES
    ('CC615299A29', 'CAB738DA06F', 'CBC Results', 'C6713153A46')
ON CONFLICT DO NOTHING;

INSERT INTO dhis2_org_unit (uid, code, name) VALUES
    ('qABHIseAqcT', 'HISP_MENA', 'Health Information System Program MENA')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 2. DHIS2 data elements: Analyzer, Patient Info, Results
-- ============================================================================
INSERT INTO dhis2_data_element (uid, code, name, value_type) VALUES
    -- Analyzer
    ('plTBJagjGMi', 'CBC_ANALYZER_ID',   'Analyzer ID',   'TEXT'),
    ('xpNOZE09SWL', 'CBC_ANALYZER_NAME', 'Analyzer Name', 'TEXT'),
    ('C6713153A46', 'CBC_BARCODE',       'Barcode',       'TEXT'),
    -- Patient Info
    ('I0daj7N4Sih', 'CBC_PATIENT_CODE',   'Patient Code',   'TEXT'),
    ('oCHnvII6iUQ', 'CBC_PATIENT_NAME',   'Patient Name',   'TEXT'),
    ('zK6i0HDVTtr', 'CBC_PATIENT_GENDER', 'Gender',         'TEXT'),
    ('TMmFsuXnTR8', 'CBC_PATIENT_AGE',    'Age',            'NUMBER'),
    -- Results
    ('C83F5365060', 'CBC_WBC',   'CBC - WBC',   'NUMBER'),
    ('C58561BD760', 'CBC_RBC',   'CBC - RBC',   'NUMBER'),
    ('CCF5EA60A2D', 'CBC_HGB',   'CBC - HGB',   'NUMBER'),
    ('C2508770869', 'CBC_HCT',   'CBC - HCT',   'NUMBER'),
    ('CBD9E45005E', 'CBC_MCV',   'CBC - MCV',   'NUMBER'),
    ('C0D65B04FAD', 'CBC_MCH',   'CBC - MCH',   'NUMBER'),
    ('C3E59478B91', 'CBC_MCHC',  'CBC - MCHC',  'NUMBER'),
    ('C4ADB8B437E', 'CBC_PLT',   'CBC - PLT',   'NUMBER'),
    ('C68FC2E78BE', 'CBC_NEUT',  'CBC - NEUT',  'NUMBER'),
    ('C44C79EE177', 'CBC_LYM',   'CBC - LYM',   'NUMBER'),
    ('C2969857371', 'CBC_MONO',  'CBC - MONO',  'NUMBER'),
    ('CE7E0C08FDC', 'CBC_EOS',   'CBC - EOS',   'NUMBER'),
    ('C906F7ABC83', 'CBC_BASO',  'CBC - BASO',  'NUMBER')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 3. Data elements included in the CBC program stage
-- ============================================================================
INSERT INTO dhis2_stage_data_element (stage_uid, data_element_uid, sort_order, compulsory) VALUES
    ('CC615299A29', 'plTBJagjGMi',  1, FALSE),
    ('CC615299A29', 'xpNOZE09SWL',  2, FALSE),
    ('CC615299A29', 'C6713153A46',  3, TRUE),
    ('CC615299A29', 'I0daj7N4Sih',  4, FALSE),
    ('CC615299A29', 'oCHnvII6iUQ',  5, FALSE),
    ('CC615299A29', 'zK6i0HDVTtr',  6, FALSE),
    ('CC615299A29', 'TMmFsuXnTR8',  7, FALSE),
    ('CC615299A29', 'C83F5365060',  8, FALSE),
    ('CC615299A29', 'C58561BD760',  9, FALSE),
    ('CC615299A29', 'CCF5EA60A2D', 10, FALSE),
    ('CC615299A29', 'C2508770869', 11, FALSE),
    ('CC615299A29', 'CBD9E45005E', 12, FALSE),
    ('CC615299A29', 'C0D65B04FAD', 13, FALSE),
    ('CC615299A29', 'C3E59478B91', 14, FALSE),
    ('CC615299A29', 'C4ADB8B437E', 15, FALSE),
    ('CC615299A29', 'C68FC2E78BE', 16, FALSE),
    ('CC615299A29', 'C44C79EE177', 17, FALSE),
    ('CC615299A29', 'C2969857371', 18, FALSE),
    ('CC615299A29', 'CE7E0C08FDC', 19, FALSE),
    ('CC615299A29', 'C906F7ABC83', 20, FALSE)
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 4. Program sections and their data elements
-- ============================================================================
INSERT INTO dhis2_program_stage_section (uid, stage_uid, name, sort_order) VALUES
    ('CBB6A37CDD3', 'CC615299A29', 'Analyzer',     1),
    ('C823F6F34C9', 'CC615299A29', 'Patient Info', 2),
    ('CAB7622B5AA', 'CC615299A29', 'Results',      3)
ON CONFLICT DO NOTHING;

INSERT INTO dhis2_section_data_element (section_uid, data_element_uid, sort_order) VALUES
    -- Analyzer
    ('CBB6A37CDD3', 'plTBJagjGMi',  1),
    ('CBB6A37CDD3', 'xpNOZE09SWL',  2),
    ('CBB6A37CDD3', 'C6713153A46',  3),
    -- Patient Info
    ('C823F6F34C9', 'I0daj7N4Sih',  1),
    ('C823F6F34C9', 'oCHnvII6iUQ',  2),
    ('C823F6F34C9', 'zK6i0HDVTtr',  3),
    ('C823F6F34C9', 'TMmFsuXnTR8',  4),
    -- Results
    ('CAB7622B5AA', 'C83F5365060',  1),
    ('CAB7622B5AA', 'C58561BD760',  2),
    ('CAB7622B5AA', 'CCF5EA60A2D',  3),
    ('CAB7622B5AA', 'C2508770869',  4),
    ('CAB7622B5AA', 'CBD9E45005E',  5),
    ('CAB7622B5AA', 'C0D65B04FAD',  6),
    ('CAB7622B5AA', 'C3E59478B91',  7),
    ('CAB7622B5AA', 'C4ADB8B437E',  8),
    ('CAB7622B5AA', 'C68FC2E78BE',  9),
    ('CAB7622B5AA', 'C44C79EE177', 10),
    ('CAB7622B5AA', 'C2969857371', 11),
    ('CAB7622B5AA', 'CE7E0C08FDC', 12),
    ('CAB7622B5AA', 'C906F7ABC83', 13)
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 5. CBC categories in the order returned to the .NET service
-- ============================================================================
INSERT INTO sample_category (id, code, name, sort_order) VALUES
    (101, 'WBC',  'White blood cell count',               1),
    (102, 'RBC',  'Red blood cell count',                 2),
    (103, 'HGB',  'Hemoglobin',                           3),
    (104, 'HCT',  'Hematocrit',                           4),
    (105, 'MCV',  'Mean corpuscular volume',             5),
    (106, 'MCH',  'Mean corpuscular hemoglobin',         6),
    (107, 'MCHC', 'Mean corpuscular hemoglobin concentration', 7),
    (108, 'PLT',  'Platelets',                            8),
    (109, 'NEUT', 'Neutrophils',                          9),
    (110, 'LYM',  'Lymphocytes',                         10),
    (111, 'MONO', 'Monocytes',                           11),
    (112, 'EOS',  'Eosinophils',                          12),
    (113, 'BASO', 'Basophils',                           13)
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 6. CBC category to DHIS2 result data element mapping
-- ============================================================================
INSERT INTO dhis2_result_mapping (sample_category_id, stage_uid, data_element_uid) VALUES
    (101, 'CC615299A29', 'C83F5365060'), -- WBC
    (102, 'CC615299A29', 'C58561BD760'), -- RBC
    (103, 'CC615299A29', 'CCF5EA60A2D'), -- HGB
    (104, 'CC615299A29', 'C2508770869'), -- HCT
    (105, 'CC615299A29', 'CBD9E45005E'), -- MCV
    (106, 'CC615299A29', 'C0D65B04FAD'), -- MCH
    (107, 'CC615299A29', 'C3E59478B91'), -- MCHC
    (108, 'CC615299A29', 'C4ADB8B437E'), -- PLT
    (109, 'CC615299A29', 'C68FC2E78BE'), -- NEUT
    (110, 'CC615299A29', 'C44C79EE177'), -- LYM
    (111, 'CC615299A29', 'C2969857371'), -- MONO
    (112, 'CC615299A29', 'CE7E0C08FDC'), -- EOS
    (113, 'CC615299A29', 'C906F7ABC83')  -- BASO
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 7. Site-specific values: add your real machines and branch mappings
-- ============================================================================
-- machine: id, name, brand, sample_type_id, active
-- analyzer_result_definition: machine_id, sample_category_id, result_order,
--   optional start_position/category_length, factor, active
-- dhis2_org_unit_mapping: branch_id, org_unit_uid
-- No machine or branch IDs are invented by this seed.
-- Replace the placeholders with real values, then remove the -- prefix:
-- INSERT INTO machine (id, name, brand, sample_type_id)
-- VALUES (<machine_id>, '<machine_name>', '<brand>', <sample_type_id>)
-- ON CONFLICT DO NOTHING;
-- INSERT INTO analyzer_result_definition
--     (machine_id, sample_category_id, result_order, factor)
-- SELECT <machine_id>, id, sort_order, 1 FROM sample_category
-- ON CONFLICT DO NOTHING;
-- INSERT INTO dhis2_org_unit_mapping (branch_id, org_unit_uid)
-- VALUES (<branch_id>, '<real_dhis2_org_unit_uid>')
-- ON CONFLICT DO NOTHING;
