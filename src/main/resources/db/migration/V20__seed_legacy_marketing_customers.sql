-- The legacy Marketing Customer list (asgdynamic "customer" screen), 130 rows, CAF000001 to
-- CAF000130, as the legacy grid showed them: Code, Name, Contact NO, Telephone NO, Email Address,
-- Contact Person, Contact Person's Mobile, Bond Licence, Brand, Garments, Buying House.
--
-- Each row becomes one party (see V15) in organization ASG:
--   * code          the legacy code, which is also its customer code
--   * roles         CUSTOMER (MARKETING) carrying the legacy code; BRAND, GARMENT_FACTORY and
--                   BUYING_HOUSE where the legacy isBrand / isGarments / isBuyingHouse was ticked -
--                   the flags the booking screen's Brand and Garments pickers filter on
--   * contact       one primary contact: Contact Person (or "Office" when only numbers were
--                   recorded), Contact NO as phone, the person's mobile, a well-formed email
--   * attributes    Bond licence, Telephone, and an email the editor would refuse as malformed
--                   ("xyz", "morrisons.com", "maarit.mikkonen(at)tokmanni.fi") kept verbatim under
--                   "Email (as recorded)" rather than dropped or blocking later edits
--
-- The rows are copied as recorded; only the legacy "nothing here" markers - blank, ".", "-" and
-- "N/A" in any case - become NULL. Test values ("1234567890", "xyz@gmail.com", "Mr.X") and the
-- legacy duplicates (PRIMARK / Primark, UNIQLO / Uniqlo, LC WAIKIKI / Lc Waikiki, ...) are kept:
-- both codes may be on documents; tidy them in Setup, Parties.
--
-- Customer codes continue the legacy series: the CUSTOMER scheme is set to CAF + six digits,
-- never restarting, its counter to 130, and every CAF code is recorded as issued - the next
-- customer is CAF000131. Idempotent: an existing code, role, contact or scheme is left alone.

CREATE TEMP TABLE tmp_legacy_customers (
    code            VARCHAR(40)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    contact_no      VARCHAR(100),
    telephone       VARCHAR(100),
    email           VARCHAR(200),
    contact_person  VARCHAR(200),
    contact_mobile  VARCHAR(100),
    bond_licence    VARCHAR(100),
    is_brand        BOOLEAN      NOT NULL,
    is_garments     BOOLEAN      NOT NULL,
    is_buying_house BOOLEAN      NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_legacy_customers
    (code, name, contact_no, telephone, email, contact_person, contact_mobile, bond_licence,
     is_brand, is_garments, is_buying_house)
VALUES
    ('CAF000001', 'KRAYONS', '01611746020', 'N/a', 'emamul.sium@krayonsourcing.com', 'Mr.Siam', '01611746020', 'N/a', FALSE, FALSE, TRUE),
    ('CAF000002', 'HONEYWELL', 'N/a', 'N/a', 'N/a', 'N/a', 'N/a', NULL, FALSE, TRUE, FALSE),
    ('CAF000003', 'UNIQLO', 'N/a', NULL, 'xyz@gmail.com', 'Mr. x', 'N/a', NULL, TRUE, FALSE, FALSE),
    ('CAF000004', 'Green Textile Ltd.', '01713-003257', NULL, 'N/a', 'Mr.Faisal', '01713-003257', '123', FALSE, TRUE, FALSE),
    ('CAF000005', 'S.Oliver', 'N/a', NULL, 'xyz@gmail.com', 'N/a', 'N/a', NULL, TRUE, FALSE, TRUE),
    ('CAF000006', 'Continental Garments ind (PVT) Ltd.', 'N/a', 'N/a', 'xyz@gmail.com', 'Mr. x', 'N/a', 'N/a', FALSE, TRUE, FALSE),
    ('CAF000007', 'Purbachal Apparels Ltd', 'N/a', 'N/a', 'xyz@gmail.com', 'Mr. x', 'N/a', 'N/a', FALSE, TRUE, FALSE),
    ('CAF000008', 'Lc Waikiki', 'N/a', 'N/a', 'xyz@gmail.com', 'Mr. x', 'N/a', 'N/a', TRUE, FALSE, FALSE),
    ('CAF000009', 'US POlO', 'N/a', 'N/a', 'xyz@gmail.com', 'Mr. x', 'N/a', 'N/a', TRUE, FALSE, FALSE),
    ('CAF000010', 'Evitex Dress Shirt Ltd.', 'N/a', 'N/a', 'xyz@gmail.com', 'N/a', 'N/a', 'N/a', FALSE, TRUE, FALSE),
    ('CAF000011', 'Plug', 'N/a', 'N/a', 'xyz@gmail.com', 'Mr. x', 'N/a', 'N/a', TRUE, FALSE, FALSE),
    ('CAF000012', 'Valmont Fashion Ltd.', 'N/a', 'N/a', 'xyz@gmail.com', 'Mr. x', 'N/a', 'N/a', FALSE, TRUE, FALSE),
    ('CAF000013', 'Rezaul Apparels (Pvt) Ltd', 'N/a', 'N/a', 'xyz@gmail.com', 'N/a', 'N/a', 'N/a', FALSE, TRUE, FALSE),
    ('CAF000014', 'LPP (HOUSE)', '+48 58 76 96 900', NULL, NULL, 'Katarzyna Ellerik', '+48 58 76 96 900', '12345', TRUE, FALSE, FALSE),
    ('CAF000015', 'FOUR SEASONS', '01716649293', 'N/A', 'shagar@fourseasonsdhaka.com', 'Mr. Shagar', '01716649293', '12345', FALSE, TRUE, FALSE),
    ('CAF000016', 'Lefties', '01309015251', '01755596363', 'MohammadSH@itxtrading.com', 'Mr. Shahidul', '01755596363', '12345', TRUE, FALSE, FALSE),
    ('CAF000017', 'CARLOS LEATHER FASHION LTD.', '01309015251', '01755596363', 'MohammadSH@itxtrading.com', 'Mr. Shahidul', '01755596363', '12345', FALSE, TRUE, FALSE),
    ('CAF000018', 'PRIMARK', '12345', NULL, NULL, 'Mr. Crislo', '12345', '12345', TRUE, FALSE, FALSE),
    ('CAF000019', 'AEON', '019154325693', 'N/A', 'aeonstore.com.hk', 'Mr. Harry', '019154325693', '12345', TRUE, FALSE, FALSE),
    ('CAF000020', 'Spider Group', '01977049387', NULL, NULL, 'Mr. Mizan', '01776667191', '1', FALSE, FALSE, TRUE),
    ('CAF000021', 'Bottom Gallery (PVT) Ltd.', '01977049074', NULL, NULL, 'Mr. Ali', '01977049074', '2', FALSE, TRUE, FALSE),
    ('CAF000022', 'Azmat Apparels Ltd.', '01745604867', NULL, NULL, 'Mr. Sohel', '01745604867', '3', FALSE, TRUE, FALSE),
    ('CAF000023', 'LC WAIKIKI', '01730091661', 'N/A', 'NUSHERA.TAJRIN@lcwaikiki.com', 'Mrs. Nushera Tazrin', '01730091661', '12345', TRUE, FALSE, TRUE),
    ('CAF000024', 'EH Fabrics Ltd.', '01755596363', '01755596363', 'saifulislam@florencegroupbd.com', 'Mr. Saiful', '01755596363', '12345', FALSE, TRUE, FALSE),
    ('CAF000025', 'FIN BANGLA APPARELS LTD', '01643060511', 'N/A', 'woven32@pinakigroup.com', 'Mr. Subrata', '01884607436', '12345', FALSE, TRUE, FALSE),
    ('CAF000026', 'Renaissance', '02-58957475', '1', 'xyz@gmail.com', 'xyz', 'xyz', 'xyz', FALSE, TRUE, FALSE),
    ('CAF000027', 'Avantex Apparels ltd', 'xyz', '01245', 'xyz@gmail.com', 'xyz', 'xyz', 'xyz', FALSE, TRUE, FALSE),
    ('CAF000028', 'ALESTA', 'N/a', 'N/a', 'xyz@gmail.com', 'Haluk', 'N/a', 'N/a', FALSE, FALSE, TRUE),
    ('CAF000029', 'Reaz Export Apparel', 'N/a', 'N/a', 'xyz@gmail.com', 'N/A', 'N/a', 'N/a', FALSE, TRUE, FALSE),
    ('CAF000030', 'Nordstrom', 'N/A', '1', 'xyz@gmail.com', 'xyz', 'xyz', 'xyz', TRUE, FALSE, TRUE),
    ('CAF000031', 'Norp Knit Industries Ltd', '01245', '1', 'xyz@gmail.com', 'xyz', 'xyz', '1234', TRUE, TRUE, TRUE),
    ('CAF000032', 'Hellenic Group', '01321118222', NULL, NULL, 'Topu', 'Topu', '1234', FALSE, FALSE, TRUE),
    ('CAF000033', 'LAGER 157', '123', '123', NULL, '123', '123', '123', TRUE, FALSE, FALSE),
    ('CAF000034', 'Eastern Apparels Limited', '123', NULL, NULL, '123', '123', '123', FALSE, TRUE, FALSE),
    ('CAF000035', 'Shinest Apparels Limited', '123', NULL, NULL, '123', '123', '123', FALSE, TRUE, FALSE),
    ('CAF000036', 'Quality Apparels/W Apparels Ltd.', '123', NULL, NULL, '123', '123', '123', FALSE, TRUE, FALSE),
    ('CAF000037', 'Trouser World (PVT) Ltd.', '123456', NULL, NULL, 'Mr. Younus', '123456', '123.456', FALSE, TRUE, FALSE),
    ('CAF000038', 'NEXT', '123456', '123456', '123@456', 'Mr. ABC', '123456', '123.456', TRUE, FALSE, FALSE),
    ('CAF000039', 'NEXT Sourcing BD', '123456', '123456', '123@456', 'Mr. ABC', '123456', '123.456', FALSE, FALSE, TRUE),
    ('CAF000040', 'Sterling Styles Ltd.', '123456', '123456', '123@sterlingstyles.com', 'Mr. ABC', '123456', '123.456', FALSE, TRUE, FALSE),
    ('CAF000041', 'DISTRICENTER', '123456', '123456', '123@districenter.com', 'Mr. XYZ', '123456', 'N/A', TRUE, FALSE, FALSE),
    ('CAF000042', 'Onus Design Ltd.', '123456', '123456', '123@onusdesign.com', 'Mr. CDF', '123456', '123.456', FALSE, TRUE, FALSE),
    ('CAF000043', 'MASTER TEX INT''L LTD.', '123456', '123456', '123@456', 'Mr. OPQ', '123456', 'N/A', FALSE, FALSE, TRUE),
    ('CAF000044', 'Sepal Garments Ltd.', '123456', '123456', '123@sepalgarments.com', 'Mr. Geet', '123456', '123.456', FALSE, TRUE, FALSE),
    ('CAF000045', 'DEWHIRST GROUP', '123456', '123456', '123@dewhirst.com', 'Mr. STW', '123456', 'N/A', FALSE, FALSE, TRUE),
    ('CAF000046', 'VARNER (CARLINGS)', '+47 66773100', NULL, 'contact@varner.com', 'ABC', '+47 66773100', 'N/A', TRUE, FALSE, FALSE),
    ('CAF000047', 'FOUR H GROUP', '09619200400', '09619200400', 'jamil@fourhgroup.com', 'XYZ', '09619200400', 'N/A', FALSE, FALSE, TRUE),
    ('CAF000048', 'FOUR H LINGERIE LIMITED', '09619200400', NULL, 'contact@fourhligerie.com', 'LMN', '09619200400', '123.456', FALSE, TRUE, FALSE),
    ('CAF000049', 'Shanta Denims Ltd.', '123456', 'N/A', 'contact@shanta.com', 'STW', '123456', '123.456', FALSE, TRUE, FALSE),
    ('CAF000050', 'Stella Parker', '123456', '123456', 'contact@levygroup.com', 'STU', '123456', 'N/A', TRUE, FALSE, FALSE),
    ('CAF000051', 'MN7 Sourcing Ltd.', '123456', '123456', 'contact@mn7sourcing.com', 'MNO', '123456', 'N/A', FALSE, FALSE, TRUE),
    ('CAF000052', 'Pakiza Woven Fashion Ltd.', '123456', '123456', 'contact@pakizawoven.com', 'PQR', '123456', '123.456', FALSE, TRUE, FALSE),
    ('CAF000053', 'Li & Fung', '1234567890', NULL, 'xyz@gmail.com', 'Mr.X', '1234567890', NULL, TRUE, FALSE, TRUE),
    ('CAF000054', 'Tesco', '1234567890', '1234567890', 'xyz@gmail.com', 'Mr.X', '1234567890', NULL, TRUE, FALSE, FALSE),
    ('CAF000055', 'Hollywood Garments', '1234567890', '1234567890', 'xyz@gmail.com', 'Mr.X', '1234567890', '12345', FALSE, TRUE, FALSE),
    ('CAF000056', 'C&A', '1234567890', NULL, NULL, 'Mr. XYZ', '1234567890', NULL, TRUE, FALSE, TRUE),
    ('CAF000057', 'Islam Garments Ltd. (Unit-2)', '01713331137', NULL, NULL, 'Mr. Rony', '01713331136', '12345', FALSE, TRUE, TRUE),
    ('CAF000058', 'SF FASHION WEAR LTD.', '1234567890', NULL, NULL, 'X', '1234567890', '12345', FALSE, TRUE, TRUE),
    ('CAF000059', 'AUS BANGLA JUTEX LTD.', '1234567890', NULL, NULL, 'X', '1234567890', '12345', FALSE, TRUE, FALSE),
    ('CAF000060', 'Aman Graphics & Design Ltd.', '01711-154959', '-', 'thowker.hassan@amanknittings.com', 'Mr. Thowker', '01844-001370', '123.456', FALSE, TRUE, FALSE),
    ('CAF000061', 'Dekko Ready Wears', '1234567890', '1234567890', 'xyz@gmail.com', 'Mr.X', '1234567890', '12345', FALSE, TRUE, TRUE),
    ('CAF000062', 'Tokmanni', '+358(0)300 472 220', NULL, 'maarit.mikkonen(at)tokmanni.fi', 'Maarit Mikkonen', '+358 40 562 2282', NULL, TRUE, FALSE, FALSE),
    ('CAF000063', 'Harry Fashion Limited', '01682-691279', NULL, 'merch14@experience-bd.com', 'Mr. Lenin', '01682-691279', '12345', FALSE, TRUE, FALSE),
    ('CAF000064', 'Vogue Sourcing Limited', '01919-693529', NULL, NULL, 'Ms. Diba', '01919-693529', NULL, TRUE, FALSE, TRUE),
    ('CAF000065', 'MG Niche Stitch Limited', '09612-222000', NULL, NULL, 'Mr. Ganapati', '01915300380', '1984/CUS-SBW/2019', FALSE, TRUE, FALSE),
    ('CAF000066', 'Nutmeg', '0345 611 5000', NULL, 'morrisons.com', 'Morissons', '0345 611 5000', NULL, TRUE, FALSE, FALSE),
    ('CAF000067', 'Jeans Plus Ltd', '01777762327', NULL, NULL, 'Mr. Delwar', '01777762327', '12345', FALSE, TRUE, TRUE),
    ('CAF000068', 'Dongyi Sourcing Ltd', '09610-980098', NULL, NULL, 'Mr. Pavel', '09610-980098', NULL, FALSE, FALSE, TRUE),
    ('CAF000069', 'Shin Shin Apparels Ltd.', '1234567890', NULL, NULL, 'X', '1234567890', NULL, FALSE, TRUE, TRUE),
    ('CAF000070', 'Organic Jeans Ltd.', '1234567890', NULL, NULL, 'X', '1234567890', NULL, FALSE, TRUE, TRUE),
    ('CAF000071', 'Vancot Limited.', '1234567890', NULL, NULL, 'X', '1234567890', NULL, FALSE, TRUE, TRUE),
    ('CAF000072', 'Octalink', '123456789', NULL, NULL, 'MR. Rabi', '123456789', '1518545', TRUE, TRUE, TRUE),
    ('CAF000073', 'Spark', '123456789', NULL, NULL, 'Mr. X', '123456789', NULL, TRUE, TRUE, TRUE),
    ('CAF000074', 'RBL', '+447550758618', '+447550758618', 'adriana@lyfcycle.co.uk', 'GIANNE ROMANO', '+447550758618', NULL, TRUE, FALSE, FALSE),
    ('CAF000075', 'UNI GEARS LTD.', '+880 1916-582028', NULL, NULL, 'Mr. Monir', '+880 1916-582028', '12345', FALSE, TRUE, FALSE),
    ('CAF000076', 'AM London', '12345', NULL, 'contact@amlondonltd.com', 'Anushka Mehen', '12345', NULL, TRUE, FALSE, FALSE),
    ('CAF000077', 'Goodearth Apparels Ltd', '+88 09601 121 600', NULL, NULL, 'Abhishekh Kanoi', 'Goodearth Apparels Ltd', NULL, FALSE, FALSE, TRUE),
    ('CAF000078', 'MG Niche Flair Limited (Unit-2)', '+8809612 222000', NULL, 'MGNFL@mohammadigroup.com', 'Mr. Zilani', '+8809612 222000', '12345', FALSE, TRUE, FALSE),
    ('CAF000079', 'Celio', '1234', '12345', 'xyz@gmail.com', 'Mr.X', '1234567890', '12345ui', TRUE, FALSE, TRUE),
    ('CAF000080', 'AKH Group', '1234567890', '12345', 'xyz@gmail.com', 'Mr.X', '1234567890', '12345ui', FALSE, TRUE, FALSE),
    ('CAF000081', 'Tex-Ebo', '1234567890', '12345', 'xyz@gmail.com', 'Mr.X', '1234567890', '12345ui', FALSE, FALSE, TRUE),
    ('CAF000082', 'Riachuelo', '1234567890', '12345', 'xyz@gmail.com', 'Mr.X', '1234567890', '12345ui', TRUE, FALSE, FALSE),
    ('CAF000083', 'Tivoli Apparels', '1234567890', '12345', 'xyz@gmail.com', 'Mr.X', '1234567890', '12345', FALSE, TRUE, FALSE),
    ('CAF000084', 'Dekko Ready Wears Ltd.', '1234567890', '12345', 'xyz@gmail.com', 'Mr.X', '1234567890', '12345ui', FALSE, TRUE, TRUE),
    ('CAF000085', 'ZARA', '1234567890', '12345', 'xyz@gmail.com', 'Mr.X', '1234567890', '12345ui', TRUE, FALSE, FALSE),
    ('CAF000086', 'KIABI', '1234567890', '12345', 'xyz@gmail.com', 'Mr.X', '1234567890', '12345ui', TRUE, FALSE, FALSE),
    ('CAF000087', 'LPP', '1234567890', '1234567890', 'xyz@gmail.com', 'Mr.X', '1234567890', '12345ui', TRUE, FALSE, FALSE),
    ('CAF000088', 'GMS (McNeal)', '123456', '12345', 'xyz@gmail.com', 'Mr.X', '1234567890', '12345ui', TRUE, FALSE, FALSE),
    ('CAF000089', 'Globus Garments Ltd', '1234567890', '12345', 'xyz@gmail.com', 'Mr.X', '1234567890', '12345ui', FALSE, TRUE, FALSE),
    ('CAF000090', 'GDS Int Ltd', '01847099377', '1234', 'denise.gilissen@gmail.com', 'Denise', '01847099377', '1234', FALSE, FALSE, TRUE),
    ('CAF000091', 'Kubenz', '1234', '1234', 'xyz@gamil.com', 'Geteno', '1234', '1234', TRUE, FALSE, FALSE),
    ('CAF000092', 'Sinha Knit & Denim Ltd', '1234', '1234', NULL, 'Bashar', '1234', '1234', FALSE, TRUE, FALSE),
    ('CAF000093', 'Shangu Tex-2', '1234567890', '12345', 'xyz@gmail.com', 'Mr.X', '1234567890', '12345ui', FALSE, TRUE, FALSE),
    ('CAF000094', 'Mango', '1234', NULL, NULL, 'X', '123456', NULL, TRUE, FALSE, TRUE),
    ('CAF000095', 'AKH Fashions Limited.', 'N/A', NULL, NULL, 'X', '1234567890', '12345', FALSE, TRUE, TRUE),
    ('CAF000096', 'Aboni Fashions Ltd', '12345', NULL, NULL, 'N/A', '12345', '12345', FALSE, TRUE, FALSE),
    ('CAF000097', 'Uniqlo', '123456', NULL, NULL, 'X', '123456', NULL, TRUE, FALSE, FALSE),
    ('CAF000098', 'NHT Fashions Ltd', '1234567', NULL, NULL, 'N/A', '1234567', NULL, FALSE, TRUE, FALSE),
    ('CAF000099', 'Brandix Apparel Bangladesh Ltd.', '+(94) 11 4727222', NULL, NULL, 'Rejuan', '01876487878', '.', FALSE, TRUE, FALSE),
    ('CAF000100', 'THE ROSE DRESSES LIMITED.', '29863004', NULL, 'atiq@islam-garments.com', 'X', '1234567890', '12345', FALSE, TRUE, TRUE),
    ('CAF000101', 'Primark', '+880 1718-682064', NULL, NULL, 'Mr. Mostafiz', '+880 1718-682064', NULL, TRUE, FALSE, FALSE),
    ('CAF000102', 'ABA Fashion Ltd.', '+880 1718-682064', NULL, NULL, 'Mr. Mostafiz', '+880 1718-682064', '12345', FALSE, TRUE, TRUE),
    ('CAF000103', 'Lefties( Inditex)', '+880 1829-337346', NULL, 'saifulislam@florencegroupbd.com', 'Mr. Saiful Forhad', '+880 1829-337346', NULL, TRUE, FALSE, TRUE),
    ('CAF000104', 'Florence Fabrics Ltd.', '+880 1829-337346', NULL, NULL, 'Mr. Saiful Forhad', '+880 1829-337346', NULL, FALSE, TRUE, FALSE),
    ('CAF000105', 'Techno Design', '1234', NULL, NULL, 'Mr. Mahdi', '1', NULL, FALSE, FALSE, TRUE),
    ('CAF000106', 'Greenlife Knit Composite Ltd.', '01313767638', NULL, NULL, 'Mr. Mahdi', '123456', '12345', FALSE, TRUE, FALSE),
    ('CAF000107', 'Green Textile Ltd', '01944-634814', NULL, NULL, 'Mr.Ataur', 'N/A', NULL, FALSE, TRUE, FALSE),
    ('CAF000108', 'Skope Apparels Ltd', '01772414137', '12345', 'wridita@Skope apparels.com', 'Wriditta', '01772414137', 'N/A', FALSE, FALSE, TRUE),
    ('CAF000109', 'Aleya Apparels Ltd', '01935693971', '12345', 'merchandising4@aleyabd.com', 'Mr Jahangir', '01935693971', '12345', FALSE, TRUE, FALSE),
    ('CAF000110', 'JB Tex', '01321118227', NULL, 'Hasem@JBtex.com', 'Hasem', '01772414137', NULL, TRUE, TRUE, TRUE),
    ('CAF000111', 'EVE DRESS-SHIRTS LIMITED', 'N/A', '1234567', '345678', 'N/A', '6789', '12345', FALSE, TRUE, FALSE),
    ('CAF000112', 'Crystal Vestiti Ltd.', '1234567890', NULL, NULL, 'X', '1234567890', '12345', FALSE, TRUE, TRUE),
    ('CAF000113', 'Babyshop', 'N/A', NULL, NULL, 'X', '1234567890', NULL, TRUE, FALSE, TRUE),
    ('CAF000114', 'NEO FASHION LTD.', 'N/A', '88027743312', NULL, 'X', '1234567890', '12345', FALSE, TRUE, TRUE),
    ('CAF000115', 'Pepe Jeans', '01872085157', NULL, NULL, 'Mr. Shahjahan', '01872085157', NULL, TRUE, FALSE, FALSE),
    ('CAF000116', 'AB Apparels Ltd.', '123456', '123456', 'razon@ab-group.co', 'Mr. Razon', '123456', '123.456', FALSE, TRUE, FALSE),
    ('CAF000117', 'Silken Sewing Ltd.', '123456', '123456', '123@456', 'Mr. Rafi', '123456', '123.456', FALSE, TRUE, FALSE),
    ('CAF000118', 'DIVERSE', '123456', '123456', '123@456', 'Mr. ABC', '123456', '123.456', TRUE, FALSE, FALSE),
    ('CAF000119', 'Tex Design Ltd.', '123456', NULL, NULL, 'Mr. Mahbubur', '123456', '123.456', FALSE, FALSE, TRUE),
    ('CAF000120', 'Design Hub', '01325-067955', NULL, 'Kabir@design-hub.org', 'Kabir', '01325-067955', NULL, FALSE, FALSE, TRUE),
    ('CAF000121', 'Adiba Apparel Ltd', '12345', NULL, 'xyz', 'Kalam', 'kalam', NULL, FALSE, TRUE, FALSE),
    ('CAF000122', 'Orbital Int Ltd', 'N/A', NULL, 'xyz', '123', '123', '12345', TRUE, FALSE, FALSE),
    ('CAF000123', 'Primordial Ltd.', '+880 1916-224563', NULL, NULL, 'Mr. Rashel', '+880 1916-224563', '1036/CUS-SBW/2009', FALSE, TRUE, TRUE),
    ('CAF000124', 'Orchestra', '+880 1916-224563', NULL, NULL, 'Mr. Rashel', '+880 1916-224563', NULL, TRUE, FALSE, FALSE),
    ('CAF000125', 'Mango (Fareast)', '01321118208', NULL, NULL, 'Mr. Mustafiz', '01872085157', '123', TRUE, FALSE, FALSE),
    ('CAF000126', 'ZARA BOYS', '12345', '1234', 'Mr.X@gmail.com', 'Mr.X', '1234567', NULL, TRUE, FALSE, FALSE),
    ('CAF000127', 'Alif Industries Ltd', '01911667751', '1234', 'xyz', 'Salauddin', '01911667751', NULL, FALSE, TRUE, FALSE),
    ('CAF000128', 'Transworld Ltd', '01321118208', NULL, NULL, 'Mr. Shahjahan', '01872085157', '123', FALSE, TRUE, FALSE),
    ('CAF000129', 'Far East Knitting & Dyeing', '01713185157', NULL, NULL, 'Mr. Mustafiz', '01872085157', '321', FALSE, TRUE, FALSE),
    ('CAF000130', 'Hazrat Amanat Shah Spinning Mills Ltd.', '.', NULL, NULL, '.', '.', '.', FALSE, FALSE, FALSE);

-- The legacy "nothing recorded" markers.
UPDATE tmp_legacy_customers SET
    contact_no     = CASE WHEN lower(btrim(contact_no))     IN ('', '.', '-', 'n/a') THEN NULL ELSE btrim(contact_no) END,
    telephone      = CASE WHEN lower(btrim(telephone))      IN ('', '.', '-', 'n/a') THEN NULL ELSE btrim(telephone) END,
    email          = CASE WHEN lower(btrim(email))          IN ('', '.', '-', 'n/a') THEN NULL ELSE btrim(email) END,
    contact_person = CASE WHEN lower(btrim(contact_person)) IN ('', '.', '-', 'n/a') THEN NULL ELSE btrim(contact_person) END,
    contact_mobile = CASE WHEN lower(btrim(contact_mobile)) IN ('', '.', '-', 'n/a') THEN NULL ELSE btrim(contact_mobile) END,
    bond_licence   = CASE WHEN lower(btrim(bond_licence))   IN ('', '.', '-', 'n/a') THEN NULL ELSE btrim(bond_licence) END,
    name           = btrim(name);

-- ---------------------------------------------------------------------------------------------
-- Parties
-- ---------------------------------------------------------------------------------------------
-- The email rule is PartyAdminService.EMAIL's.
INSERT INTO pty_parties (organization_id, code, name, party_type, attributes, active, deleted, version, created_by, created_at)
SELECT o.id, c.code, c.name, 'ORGANISATION',
       jsonb_strip_nulls(jsonb_build_object(
           'Bond licence',        c.bond_licence,
           'Telephone',           c.telephone,
           'Email (as recorded)', CASE WHEN c.email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' THEN c.email END)),
       TRUE, FALSE, 0, 'V20', now()
FROM org_organizations o
CROSS JOIN tmp_legacy_customers c
WHERE o.code = 'ASG'
ON CONFLICT (organization_id, code) DO NOTHING;

-- Roles and contacts only on the parties this migration created: a party that already held one
-- of these codes is someone's own record and is not touched.
INSERT INTO pty_party_roles (organization_id, party_id, role_type, qualifier, role_code, granted_on, is_current,
                             version, created_by, created_at)
SELECT p.organization_id, p.id, r.role_type, r.qualifier, r.role_code, CURRENT_DATE, TRUE, 0, 'V20', now()
FROM tmp_legacy_customers c
JOIN org_organizations o ON o.code = 'ASG'
JOIN pty_parties p ON p.organization_id = o.id AND p.code = c.code AND p.created_by = 'V20'
CROSS JOIN LATERAL (VALUES
    ('CUSTOMER',        'MARKETING', c.code, TRUE),
    ('BRAND',           NULL,        NULL,   c.is_brand),
    ('GARMENT_FACTORY', NULL,        NULL,   c.is_garments),
    ('BUYING_HOUSE',    NULL,        NULL,   c.is_buying_house)
) AS r(role_type, qualifier, role_code, wanted)
WHERE r.wanted
ON CONFLICT DO NOTHING;

INSERT INTO pty_party_contacts (organization_id, party_id, name, phone, mobile, email, is_primary,
                                version, created_by, created_at)
SELECT p.organization_id, p.id, COALESCE(c.contact_person, 'Office'), c.contact_no, c.contact_mobile,
       CASE WHEN c.email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' THEN c.email END,
       TRUE, 0, 'V20', now()
FROM tmp_legacy_customers c
JOIN org_organizations o ON o.code = 'ASG'
JOIN pty_parties p ON p.organization_id = o.id AND p.code = c.code AND p.created_by = 'V20'
WHERE COALESCE(c.contact_person, c.contact_no, c.contact_mobile, c.email) IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM pty_party_contacts x WHERE x.party_id = p.id);

-- ---------------------------------------------------------------------------------------------
-- Customer numbering continues the legacy series
-- ---------------------------------------------------------------------------------------------
INSERT INTO gbl_numbering_schemes (organization_id, series_code, prefix, pattern, sequence_width, reset_policy,
                                   counter_scope, version, created_by, created_at)
SELECT o.id, 'CUSTOMER', 'CAF', '{PREFIX}{SEQ}', 6, 'NEVER', 'ORGANIZATION', 0, 'V20', now()
FROM org_organizations o
WHERE o.code = 'ASG'
ON CONFLICT DO NOTHING;

INSERT INTO gbl_number_counters (organization_id, series_code, business_unit_id, period_key, last_value, updated_at)
SELECT o.id, 'CUSTOMER', 0, '', 130, now()
FROM org_organizations o
WHERE o.code = 'ASG'
ON CONFLICT (organization_id, series_code, business_unit_id, period_key)
    DO UPDATE SET last_value = GREATEST(gbl_number_counters.last_value, EXCLUDED.last_value), updated_at = now();

INSERT INTO gbl_issued_numbers (organization_id, number, series_code, business_unit_id, issued_by, issued_at)
SELECT o.id, c.code, 'CUSTOMER', NULL, 'V20 legacy customers', now()
FROM org_organizations o
CROSS JOIN tmp_legacy_customers c
WHERE o.code = 'ASG'
ON CONFLICT (organization_id, number) DO NOTHING;
