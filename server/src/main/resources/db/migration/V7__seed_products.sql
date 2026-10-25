-- Seed: the 29 products of the original standalone application (store.items, ids it_cp00..it_cp28).
-- Generated from docs/reference/IOTIQ_Partner_ERP_original.html; do not edit values by hand.
-- Source values: HSN blank, aliases empty, B2C price 0 (unset -> NULL here), landed cost 0 (unset -> NULL).
-- Idempotent: existing SKUs (case-insensitive) are left untouched.
INSERT INTO products (sku, category, unit, hsn, b2c_name, partner_name, partner_price, gst_percent) VALUES
    ('STI-2M', 'SMART_TOUCH_INTERFACES', 'nos', NULL, '2 Module With Edge', 'Smart Touch Interface - 2M (STI-2M)', 5749, 18), -- it_cp00
    ('STI-4M', 'SMART_TOUCH_INTERFACES', 'nos', NULL, '4 Module With Edge', 'Smart Touch Interface - 4M (STI-4M)', 8049, 18), -- it_cp01
    ('STI-6M', 'SMART_TOUCH_INTERFACES', 'nos', NULL, '6 Module With Edge', 'Smart Touch Interface - 6M (STI-6M)', 10349, 18), -- it_cp02
    ('STI-8M', 'SMART_TOUCH_INTERFACES', 'nos', NULL, '8 Module With Edge', 'Smart Touch Interface - 8M (STI-8M)', 13799, 18), -- it_cp03
    ('STI-12M', 'SMART_TOUCH_INTERFACES', 'nos', NULL, '12 Module With Edge', 'Smart Touch Interface - 12M (STI-12M)', 20699, 18), -- it_cp04
    ('STI-18M', 'SMART_TOUCH_INTERFACES', 'nos', NULL, '18 Module With No Edge', 'Smart Touch Interface - 18M (STI-18M)', 27599, 18), -- it_cp05
    ('SRC-6P1', 'SMART_RELAY_SENSING', 'nos', NULL, '6+1 Switch Controller', '6+1 Smart Relay controller', 8625, 18), -- it_cp06
    ('SRC-6', 'SMART_RELAY_SENSING', 'nos', NULL, '6 Switch Controller', '6 Smart Relay controller', 8099, 18), -- it_cp07
    ('SRC-5', 'SMART_RELAY_SENSING', 'nos', NULL, '5 Switch Controller', '5 Smart Relay controller', 7836, 18), -- it_cp08
    ('SRC-4P1', 'SMART_RELAY_SENSING', 'nos', NULL, '4+1 Switch Controller', '4+1 Smart Relay controller', 6785, 18), -- it_cp09
    ('SRC-4', 'SMART_RELAY_SENSING', 'nos', NULL, '4 Switch Controller', '4 Smart Relay controller', 6508, 18), -- it_cp10
    ('SRC-3', 'SMART_RELAY_SENSING', 'nos', NULL, '3 Switch Controller', '3 Smart Relay controller', 5180, 18), -- it_cp11
    ('SRC-2', 'SMART_RELAY_SENSING', 'nos', NULL, '2 Switch Controller', '2 Smart Relay controller', 3864, 18), -- it_cp12
    ('SRC-1P1', 'SMART_RELAY_SENSING', 'nos', NULL, '1+1 Switch Controller', '1+1 Smart Relay controller', 3501, 18), -- it_cp13
    ('SRH-15A', 'SMART_RELAY_SENSING', 'nos', NULL, 'HDR 15A', 'Smart Relay Heavy Module - 15A', 2625, 18), -- it_cp14
    ('SRH-25A', 'SMART_RELAY_SENSING', 'nos', NULL, 'HDR 25A', 'Smart Relay Heavy Module - 25A', 3289, 18), -- it_cp15
    ('PIR-01', 'SMART_RELAY_SENSING', 'nos', NULL, 'Motion Sensor', 'Occupancy Detection Module(PIR)', 2530, 18), -- it_cp16
    ('IRB-01', 'SMART_RELAY_SENSING', 'nos', NULL, 'IR Blaster', 'IR Bridge', 3322, 18), -- it_cp17
    ('TIK-01', 'SMART_RELAY_SENSING', 'nos', NULL, 'Automation Dongle', 'Touch Interface key', 1299, 18), -- it_cp18
    ('STI-RMT', 'SMART_RELAY_SENSING', 'nos', NULL, 'Touch Panel Remote', 'STI Remote', 920, 18), -- it_cp19
    ('SG-10F', 'SMART_GLIDE', 'nos', NULL, '3.2 Curtain Controller', 'Smart Glide 10F', 22960, 18), -- it_cp20
    ('SG-17F', 'SMART_GLIDE', 'nos', NULL, '5.2 Curtain Controller', 'Smart Glide 17F', 25300, 18), -- it_cp21
    ('SG-23F', 'SMART_GLIDE', 'nos', NULL, '7.2 Curtain Controller', 'Smart Glide 23F', 27720, 18), -- it_cp22
    ('SG-33F', 'SMART_GLIDE', 'nos', NULL, '10.2 Curtain Controller', 'Smart Glide 33F', 31900, 18), -- it_cp23
    ('LK-ZURI', 'LOCKS_ENTRY', 'nos', NULL, 'Yale Zuri Smart Lock', 'Yale Zuri Smart Lock', 14500, 18), -- it_cp24
    ('LK-KYRA', 'LOCKS_ENTRY', 'nos', NULL, 'Yale Kyra Pro Smart Lock', 'Yale Kyra Pro Smart Lock', 32999, 18), -- it_cp25
    ('VDB-01', 'LOCKS_ENTRY', 'nos', NULL, 'One Touch Video Doorbell with Screen', 'One Touch Video Doorbell with Screen', 27560, 18), -- it_cp26
    ('WIFI-BR', 'LOCKS_ENTRY', 'nos', NULL, 'Wi-Fi Bridge (Smart Lock Accessories)', 'Wi-Fi Bridge (Smart Lock Accessories)', 6473, 18), -- it_cp27
    ('BT-MOD', 'LOCKS_ENTRY', 'nos', NULL, 'Bluetooth Module (Smart Lock Accessories)', 'Bluetooth Module (Smart Lock Accessories)', 4055, 18) -- it_cp28
ON CONFLICT ((lower(sku))) DO NOTHING;
