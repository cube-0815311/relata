-- Sample data for the requested store query model tables.
-- Row count: 12 rows per requested table.
-- Main store key: F_MP_MERCH_STORE_A0.STORE_NO = STQ20260601..STQ20260612.
-- Shared merchant key: CRM_MERCH_INFO.MERCH_NO = M20260601..M20260606.

-- Cleanup data created by the previous sample script.
DELETE FROM "STORE_TAG_RECORD" WHERE "STORE_NO" LIKE 'ST202606%';
DELETE FROM "STORE_DEFER_LOAN_CONFIG" WHERE "STORE_NO" LIKE 'ST202606%';
DELETE FROM "STORE_CHANNEL_PERIOD" WHERE "STORE_NO" LIKE 'ST202606%';
DELETE FROM "STORE_GOODS" WHERE "STORE_NO" LIKE 'ST202606%';
DELETE FROM "PRO_STORE_PRO" WHERE "STORE_NO" LIKE 'ST202606%';
DELETE FROM "KA_PERSONNEL_STORE" WHERE "STORE_NO" LIKE 'ST202606%';
DELETE FROM "F_MP_STORE_BUSINESS" WHERE "STORE_NO" LIKE 'ST202606%';
DELETE FROM "F_MP_MERCH_STORE_STATUS" WHERE "STORE_NO" LIKE 'ST202606%';
DELETE FROM "F_MP_MERCH_STORE_SIGN" WHERE "OBJECT_NUMBER" LIKE 'ST202606%';
DELETE FROM "F_MP_MERCH_STORE_ACCT" WHERE "STORE_NO" LIKE 'ST202606%';
DELETE FROM "F_MP_MERCH_STORE" WHERE "STORE_NO" LIKE 'ST202606%';

-- Cleanup this script's sample data, in child-to-parent order.
DELETE FROM "ACCT_TRANSFER_REPAY_RECORD" WHERE "STORE_NO" LIKE 'STQ202606%';
DELETE FROM "BF_RE_WITHDRAW_RECORDS" WHERE "STORE_NO" LIKE 'STQ202606%';
DELETE FROM "ACCT_WITHDRAWAL_BANK_CARD" WHERE "OBJ_NO" LIKE 'STQ202606%' OR "MERCH_NO" LIKE 'M202606%';
DELETE FROM "ACCT_CHECK_INFO" WHERE "APPL_CDE" LIKE 'APPLY202606%';
DELETE FROM "BF_MERCH_ACCT_INFO" WHERE "APPLY_NO" LIKE 'BFAPPLY202606%';
DELETE FROM "BF_MERCH_ACCT_APPLY" WHERE "APPLY_NO" LIKE 'BFAPPLY202606%';
DELETE FROM "COM_PRICE_RATE_INFO" WHERE "PRICING_ID" BETWEEN 2026067001 AND 2026067012;
DELETE FROM "COM_PRICE_PRICING" WHERE "ID" BETWEEN 2026067001 AND 2026067012;
DELETE FROM "COM_PRICE_PRICING_BATCH" WHERE "ID" BETWEEN 2026068001 AND 2026068012;
DELETE FROM "COM_PRICE_DETAIL_INFO" WHERE "ID" BETWEEN 2026066001 AND 2026066012;
DELETE FROM "COM_PRICE_GROUP_CH_PRODUCT" WHERE "ID" BETWEEN 2026065001 AND 2026065012;
DELETE FROM "COM_PRICE_GROUP_AFFECTE_STORE" WHERE "STORE_NO" LIKE 'STQ202606%';
DELETE FROM "COM_PRICE_GROUP_DETAIL" WHERE "ID" BETWEEN 2026064001 AND 2026064012;
DELETE FROM "COM_PRICE_GROUP_INFO" WHERE "ID" BETWEEN 2026063001 AND 2026063012;
DELETE FROM "COM_PRICE_PRODUCT" WHERE "STORE_NO" LIKE 'STQ202606%';
DELETE FROM "CREDIT_ACQ_APPLY" WHERE "STORE_NO" LIKE 'STQ202606%';
DELETE FROM "CREDIT_SPECIAL_USE" WHERE "SPECIAL_NO" LIKE 'SP202606%';
DELETE FROM "CREDIT_MERCH_USE" WHERE "MERCH_NO" LIKE 'M202606%';
DELETE FROM "CRM_STORE_INTEREST_PRODUCT" WHERE "STORE_NO" LIKE 'STQ202606%';
DELETE FROM "DEPA_AGENCY_MAPPING_CONFIG" WHERE "ID" BETWEEN 2026069001 AND 2026069012;
DELETE FROM "F_MP_MERCH_STORE_A0" WHERE "STORE_NO" LIKE 'STQ202606%';
DELETE FROM "CRM_MERCH_INFO" WHERE "MERCH_NO" LIKE 'M202606%';
DELETE FROM "CMIS_S_ACC_BANK_BCH" WHERE "ACC_BCH_CDE" LIKE 'BCH202606%';
DELETE FROM "CMIS_S_ACC_BANK" WHERE "ACC_BANK_CDE" LIKE 'BANK202606%';

INSERT INTO "CMIS_S_ACC_BANK" (
    "ACC_BANK_CDE",
    "ACC_BANK_NAME",
    "BANK_STS",
    "LAST_CHG_DT",
    "LAST_CHG_USR"
)
SELECT
    'BANK202606' || LPAD("X", 2, '0'),
    CASE MOD("X", 4) WHEN 0 THEN '招商银行' WHEN 1 THEN '工商银行' WHEN 2 THEN '建设银行' ELSE '农业银行' END || '样例' || LPAD("X", 2, '0'),
    'normal',
    '2026-06-30',
    'sample_user'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "CMIS_S_ACC_BANK_BCH" (
    "ACC_BCH_CDE",
    "ACC_BANK_CDE",
    "ACC_BCH_NAME",
    "ACC_BANK_NAME",
    "ACC_BCH_STS",
    "LAST_CHG_USR",
    "LAST_CHG_DT"
)
SELECT
    'BCH202606' || LPAD("X", 2, '0'),
    'BANK202606' || LPAD("X", 2, '0'),
    '样例开户支行' || LPAD("X", 2, '0'),
    CASE MOD("X", 4) WHEN 0 THEN '招商银行' WHEN 1 THEN '工商银行' WHEN 2 THEN '建设银行' ELSE '农业银行' END || '样例' || LPAD("X", 2, '0'),
    'normal',
    'sample_user',
    '2026-06-30'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "CRM_MERCH_INFO" (
    "MERCH_NO",
    "MERCH_CH_NAME",
    "BUSI_LIC_NO",
    "STATUS_CD",
    "ACCT_NAME",
    "ACCT_NO",
    "BCH_CDE",
    "BCH_NAME",
    "BANK_CDE",
    "BANK_NAME",
    "APPROVE_STAT",
    "UPDATE_DT",
    "UPDATE_USER"
)
SELECT
    'M202606' || LPAD("X", 2, '0'),
    '样例商户' || LPAD("X", 2, '0'),
    '91310000MA202606' || LPAD("X", 2, '0'),
    CASE WHEN MOD("X", 5) = 0 THEN '03' ELSE '01' END,
    '样例商户' || LPAD("X", 2, '0') || '结算户',
    '622288' || LPAD("X", 13, '0'),
    'BCH202606' || LPAD("X", 2, '0'),
    '样例开户支行' || LPAD("X", 2, '0'),
    'BANK202606' || LPAD("X", 2, '0'),
    CASE MOD("X", 4) WHEN 0 THEN '招商银行' WHEN 1 THEN '工商银行' WHEN 2 THEN '建设银行' ELSE '农业银行' END || '样例' || LPAD("X", 2, '0'),
    'PASS',
    '2026-06-30 10:00:00',
    'sample_user'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "F_MP_MERCH_STORE_A0" (
    "STORE_NO",
    "UPDATE_USER",
    "UPDATE_DT",
    "REF_STORE_NO",
    "IS_DEFAULT_STORE",
    "FIRST_SPELL",
    "APPROVE_STAT",
    "ALTE_FLAG",
    "ALTE_DATE",
    "ADDRESS",
    "GEOG_COORDINATES",
    "IS_USE_TEMP",
    "MERCH_NO",
    "STORE_NAME",
    "STORE_TYPE",
    "ORG_NO",
    "STORE_KIND",
    "STORE_AREA",
    "STATUS_CD",
    "CUM_NO",
    "BUSINESS_DESC",
    "UNION_PAY_MERCH_NO",
    "CUSTOMER_PRICE",
    "STORE_SPECIES",
    "BRANCH_NUM",
    "STAFF_NUM",
    "STORE_LOCATION_TYPE",
    "SOLD_FLAG",
    "POS_FLAG"
)
SELECT
    'STQ202606' || LPAD("X", 2, '0'),
    'sample_user',
    '2026-06-' || LPAD(10 + "X", 2, '0') || ' 10:00:00',
    'REF-STQ-' || LPAD("X", 2, '0'),
    CASE WHEN MOD("X", 4) = 1 THEN 'Y' ELSE 'N' END,
    'MD' || LPAD("X", 2, '0'),
    CASE WHEN MOD("X", 5) = 0 THEN 'PENDING' ELSE 'PASS' END,
    CASE MOD("X", 3) WHEN 0 THEN 'comp' WHEN 1 THEN 'add' ELSE 'cha' END,
    '2026-06-' || LPAD(10 + "X", 2, '0') || ' 11:00:00',
    CASE MOD("X", 4)
        WHEN 0 THEN '上海市浦东新区样例路' || "X" || '号'
        WHEN 1 THEN '北京市朝阳区样例街' || "X" || '号'
        WHEN 2 THEN '杭州市西湖区样例大道' || "X" || '号'
        ELSE '深圳市南山区样例路' || "X" || '号'
    END,
    CAST(121.40 + "X" / 100.0 AS VARCHAR) || ',' || CAST(31.10 + "X" / 100.0 AS VARCHAR),
    CASE WHEN MOD("X", 3) = 0 THEN 'Y' ELSE 'N' END,
    'M202606' || LPAD(MOD("X" - 1, 6) + 1, 2, '0'),
    '门店查询样例' || LPAD("X", 2, '0'),
    CASE WHEN MOD("X", 2) = 0 THEN '02' ELSE '01' END,
    'ORG' || LPAD(MOD("X" - 1, 4) + 1, 2, '0'),
    CASE WHEN MOD("X", 2) = 0 THEN '直营' ELSE '加盟' END,
    CAST(80 + "X" * 15 AS VARCHAR),
    CASE WHEN MOD("X", 6) = 0 THEN '03' ELSE '01' END,
    'CU' || LPAD(MOD("X" - 1, 5) + 1, 2, '0'),
    '门店查询模型样例数据-' || LPAD("X", 2, '0'),
    'UP' || LPAD("X", 8, '0'),
    300 + "X" * 25,
    CASE MOD("X", 3) WHEN 0 THEN 'medical' WHEN 1 THEN 'education' ELSE 'retail' END,
    CAST(MOD("X", 4) + 1 AS VARCHAR),
    CAST(10 + "X" * 2 AS VARCHAR),
    CASE MOD("X", 3) WHEN 0 THEN 'mall' WHEN 1 THEN 'street' ELSE 'office' END,
    CASE WHEN MOD("X", 5) = 0 THEN 'Y' ELSE 'N' END,
    CASE WHEN MOD("X", 4) = 0 THEN 'N' ELSE 'Y' END
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "BF_MERCH_ACCT_APPLY" (
    "ID",
    "APPLY_NO",
    "OBJ_NO",
    "OBJ_TYPE",
    "APPLY_STEP",
    "ORG_NAME",
    "BUSINESS_LICENSE_NO",
    "STATUS",
    "IS_DEL",
    "UPDATE_DT",
    "UPDATE_USER",
    "CREATE_DT",
    "CREATE_USER",
    "REAPPLY_STATUS"
)
SELECT
    'BFMAA202606' || LPAD("X", 4, '0'),
    'BFAPPLY202606' || LPAD("X", 2, '0'),
    'M202606' || LPAD(MOD("X" - 1, 6) + 1, 2, '0'),
    'merch',
    CASE WHEN MOD("X", 4) = 0 THEN 'audit' ELSE 'open' END,
    '样例商户' || LPAD(MOD("X" - 1, 6) + 1, 2, '0'),
    '91310000MA202606' || LPAD(MOD("X" - 1, 6) + 1, 2, '0'),
    CASE WHEN MOD("X", 5) = 0 THEN 'processing' ELSE 'success' END,
    'N',
    TIMESTAMP '2026-06-30 10:00:00',
    'sample_user',
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user',
    'N'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "BF_MERCH_ACCT_INFO" (
    "ID",
    "WALLET_NO",
    "ORG_NAME",
    "BUSINESS_LICENSE_NO",
    "ACCOUNT_NO",
    "HT_ACCOUNT_NO",
    "APPROVE_STAT",
    "APPLY_NO",
    "IS_DEL",
    "UPDATE_DT",
    "UPDATE_USER",
    "CREATE_DT",
    "CREATE_USER"
)
SELECT
    'BFMAI202606' || LPAD("X", 4, '0'),
    'WALLET202606' || LPAD("X", 2, '0'),
    '样例商户' || LPAD(MOD("X" - 1, 6) + 1, 2, '0'),
    '91310000MA202606' || LPAD(MOD("X" - 1, 6) + 1, 2, '0'),
    'ACCT202606' || LPAD("X", 6, '0'),
    'HTACCT202606' || LPAD("X", 6, '0'),
    'PASS',
    'BFAPPLY202606' || LPAD("X", 2, '0'),
    'N',
    TIMESTAMP '2026-06-30 10:00:00',
    'sample_user',
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "ACCT_WITHDRAWAL_BANK_CARD" (
    "ID",
    "OBJ_NO",
    "OBJ_TYPE",
    "CARD_NO",
    "CARD_NAME",
    "BCH_CDE",
    "BANK_CDE",
    "BANK_NAME",
    "BCH_NAME",
    "CARD_TYPE",
    "MD5",
    "IS_BF",
    "IS_COMMON",
    "STATUS",
    "APPROVE_STAT",
    "IS_DEL",
    "UPDATE_DT",
    "UPDATE_USER",
    "CREATE_DT",
    "CREATE_USER",
    "MERCH_NO"
)
SELECT
    'AWBC202606' || LPAD("X", 4, '0'),
    'STQ202606' || LPAD("X", 2, '0'),
    'store',
    '622299' || LPAD("X", 13, '0'),
    '门店查询样例' || LPAD("X", 2, '0') || '提现卡',
    'BCH202606' || LPAD("X", 2, '0'),
    'BANK202606' || LPAD("X", 2, '0'),
    CASE MOD("X", 4) WHEN 0 THEN '招商银行' WHEN 1 THEN '工商银行' WHEN 2 THEN '建设银行' ELSE '农业银行' END || '样例' || LPAD("X", 2, '0'),
    '样例开户支行' || LPAD("X", 2, '0'),
    'debit',
    'md5card' || LPAD("X", 2, '0'),
    CASE WHEN MOD("X", 3) = 0 THEN 'Y' ELSE 'N' END,
    CASE WHEN MOD("X", 2) = 0 THEN 'Y' ELSE 'N' END,
    'active',
    'PASS',
    'N',
    TIMESTAMP '2026-06-30 10:00:00',
    'sample_user',
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user',
    'M202606' || LPAD(MOD("X" - 1, 6) + 1, 2, '0')
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "ACCT_CHECK_INFO" (
    "ID",
    "BCH_CDE",
    "ACCT_NO",
    "ACCT_NAME",
    "CHECK_STATUS",
    "CHECK_TIME",
    "CHECK_AMT",
    "APPL_CDE",
    "MD5",
    "ORG_ID",
    "IS_DEL",
    "UPDATE_DT",
    "UPDATE_USER",
    "CREATE_DT",
    "CREATE_USER",
    "BCH_NAME"
)
SELECT
    2026061000 + "X",
    'BCH202606' || LPAD("X", 2, '0'),
    '622299' || LPAD("X", 13, '0'),
    '门店查询样例' || LPAD("X", 2, '0') || '提现卡',
    CASE WHEN MOD("X", 5) = 0 THEN 'fail' ELSE 'success' END,
    TIMESTAMP '2026-06-30 10:00:00',
    100 + "X",
    'APPLY202606' || LPAD("X", 2, '0'),
    'md5check' || LPAD("X", 2, '0'),
    'ORG' || LPAD(MOD("X" - 1, 4) + 1, 2, '0'),
    'N',
    TIMESTAMP '2026-06-30 10:00:00',
    'sample_user',
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user',
    '样例开户支行' || LPAD("X", 2, '0')
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "BF_RE_WITHDRAW_RECORDS" (
    "ID",
    "CONT_NO",
    "APPL_SEQ",
    "MERCH_NO",
    "STORE_NO",
    "WALLET_NO",
    "CARD_NO",
    "CARD_NAME",
    "BCH_CDE",
    "CARD_TYPE",
    "AMOUNT",
    "ORIGINAL_CARD_NO",
    "ORIGINAL_CARD_NAME",
    "ORIGINAL_BCH_CDE",
    "ORIGINAL_CARD_TYPE",
    "STATUS",
    "REMARK",
    "IS_DEL",
    "UPDATE_DT",
    "UPDATE_USER",
    "CREATE_DT",
    "CREATE_USER"
)
SELECT
    'BFWD202606' || LPAD("X", 4, '0'),
    'CONT202606' || LPAD("X", 2, '0'),
    'APPLY202606' || LPAD("X", 2, '0'),
    'M202606' || LPAD(MOD("X" - 1, 6) + 1, 2, '0'),
    'STQ202606' || LPAD("X", 2, '0'),
    'WALLET202606' || LPAD("X", 2, '0'),
    '622299' || LPAD("X", 13, '0'),
    '门店查询样例' || LPAD("X", 2, '0') || '提现卡',
    'BCH202606' || LPAD("X", 2, '0'),
    'debit',
    1000 + "X" * 100,
    '622288' || LPAD(MOD("X" - 1, 6) + 1, 13, '0'),
    '样例商户' || LPAD(MOD("X" - 1, 6) + 1, 2, '0') || '结算户',
    'BCH202606' || LPAD("X", 2, '0'),
    'debit',
    CASE WHEN MOD("X", 6) = 0 THEN 'processing' ELSE 'success' END,
    '提现记录样例-' || LPAD("X", 2, '0'),
    'N',
    TIMESTAMP '2026-06-30 10:00:00',
    'sample_user',
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "ACCT_TRANSFER_REPAY_RECORD" (
    "ID",
    "CONT_NO",
    "MERCH_NO",
    "STORE_NO",
    "BANK_UNION_CODE",
    "BANK_BRANCH",
    "ACCT_NAME",
    "PRE_BANK_CODE",
    "PRE_BANK_NAME",
    "PRE_BANK_UNION_CODE",
    "PRE_ACCT_NO",
    "PRE_ACCT_NAME",
    "PRE_BANK_BRANCH",
    "STATUS",
    "IS_DEL",
    "UPDATE_DT",
    "UPDATE_USER",
    "CREATE_DT",
    "CREATE_USER",
    "APPL_SEQ"
)
SELECT
    'ATRR202606' || LPAD("X", 4, '0'),
    'CONT202606' || LPAD("X", 2, '0'),
    'M202606' || LPAD(MOD("X" - 1, 6) + 1, 2, '0'),
    'STQ202606' || LPAD("X", 2, '0'),
    'BCH202606' || LPAD("X", 2, '0'),
    '样例开户支行' || LPAD("X", 2, '0'),
    '门店查询样例' || LPAD("X", 2, '0') || '提现卡',
    'BANK202606' || LPAD("X", 2, '0'),
    CASE MOD("X", 4) WHEN 0 THEN '招商银行' WHEN 1 THEN '工商银行' WHEN 2 THEN '建设银行' ELSE '农业银行' END || '样例' || LPAD("X", 2, '0'),
    'BCH202606' || LPAD("X", 2, '0'),
    '622299' || LPAD("X", 13, '0'),
    '门店查询样例' || LPAD("X", 2, '0') || '提现卡',
    '样例开户支行' || LPAD("X", 2, '0'),
    CASE WHEN MOD("X", 6) = 0 THEN 'processing' ELSE 'success' END,
    'N',
    TIMESTAMP '2026-06-30 10:00:00',
    'sample_user',
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user',
    'APPLY202606' || LPAD("X", 2, '0')
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "COM_PRICE_PRODUCT" (
    "ID",
    "PRODUCT_NO",
    "PRODUCT_NAME",
    "CHANNEL_NO",
    "CHANNEL_NAME",
    "STORE_NO",
    "MERCH_NO",
    "CREATE_USER",
    "CREATE_DT",
    "UPDATE_USER",
    "UPDATE_DT",
    "IS_DELETED",
    "RECORD_SOURCE",
    "GOODS_NO",
    "GENERATION_MODE"
)
SELECT
    2026062000 + "X",
    'PRD202606' || LPAD("X", 2, '0'),
    CASE MOD("X", 4) WHEN 0 THEN '教育分期' WHEN 1 THEN '医美分期' WHEN 2 THEN '消费分期' ELSE '零售分期' END,
    'CH' || LPAD(MOD("X" - 1, 4) + 1, 2, '0'),
    '样例渠道' || LPAD(MOD("X" - 1, 4) + 1, 2, '0'),
    'STQ202606' || LPAD("X", 2, '0'),
    'M202606' || LPAD(MOD("X" - 1, 6) + 1, 2, '0'),
    'sample_user',
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user',
    TIMESTAMP '2026-06-30 10:00:00',
    'N',
    'store',
    880000 + "X",
    CASE WHEN MOD("X", 2) = 0 THEN 'auto' ELSE 'manual' END
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "COM_PRICE_GROUP_INFO" (
    "ID",
    "PRICING_RULE",
    "RULE_DATA_ID",
    "GROUP_NAME",
    "GROUP_DESCRIBE",
    "CREATE_USER",
    "CREATE_DT",
    "UPDATE_USER",
    "UPDATE_DT",
    "IS_DELETED",
    "VALID",
    "APPROVE_STAT",
    "RULE_DATA_NAME",
    "AFFECTED_STORE_STATUS",
    "VERSION",
    "EFFECTED_TIME",
    "REMARK"
)
SELECT
    2026063000 + "X",
    CASE WHEN MOD("X", 2) = 0 THEN 'store' ELSE 'product' END,
    2026062000 + "X",
    '样例价格组' || LPAD("X", 2, '0'),
    '门店查询模型价格组样例-' || LPAD("X", 2, '0'),
    'sample_user',
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user',
    TIMESTAMP '2026-06-30 10:00:00',
    'N',
    'Y',
    'PASS',
    'PRD202606' || LPAD("X", 2, '0'),
    'generated',
    'v1',
    TIMESTAMP '2026-07-01 00:00:00',
    'sample'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "COM_PRICE_GROUP_DETAIL" (
    "ID",
    "GROUP_ID",
    "DETAIL_TYPE",
    "DETAIL_DATA",
    "DETAIL_DESC",
    "APPROVE_STAT",
    "CREATE_USER",
    "CREATE_DT",
    "UPDATE_USER",
    "UPDATE_DT",
    "IS_DELETED"
)
SELECT
    2026064000 + "X",
    2026063000 + "X",
    'product',
    'PRD' || LPAD("X", 2, '0'),
    '样例价格组明细' || LPAD("X", 2, '0'),
    'PA',
    'sample_user',
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user',
    TIMESTAMP '2026-06-30 10:00:00',
    'N'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "COM_PRICE_GROUP_AFFECTE_STORE" (
    "ID",
    "GROUP_ID",
    "VERSION",
    "STORE_NO",
    "CHANNEL_NO",
    "STATUS",
    "CREATE_DT",
    "UPDATE_USER",
    "UPDATE_DT",
    "IS_DELETED"
)
SELECT
    2026064500 + "X",
    2026063000 + "X",
    'v1',
    'STQ202606' || LPAD("X", 2, '0'),
    'CH' || LPAD(MOD("X" - 1, 4) + 1, 2, '0'),
    CASE WHEN MOD("X", 6) = 0 THEN 'paused' ELSE 'active' END,
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user',
    TIMESTAMP '2026-06-30 10:00:00',
    'N'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "COM_PRICE_GROUP_CH_PRODUCT" (
    "ID",
    "GROUP_DETAIL_ID",
    "PRODUCT_NO",
    "PRODUCT_NAME",
    "CHANNEL_NO",
    "CHANNEL_NAME",
    "APPROVE_STAT",
    "CREATE_USER",
    "CREATE_DT",
    "UPDATE_USER",
    "UPDATE_DT",
    "IS_DELETED"
)
SELECT
    2026065000 + "X",
    2026064000 + "X",
    'PRD202606' || LPAD("X", 2, '0'),
    CASE MOD("X", 4) WHEN 0 THEN '教育分期' WHEN 1 THEN '医美分期' WHEN 2 THEN '消费分期' ELSE '零售分期' END,
    'CH' || LPAD(MOD("X" - 1, 4) + 1, 2, '0'),
    '样例渠道' || LPAD(MOD("X" - 1, 4) + 1, 2, '0'),
    'PA',
    'sample_user',
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user',
    TIMESTAMP '2026-06-30 10:00:00',
    'N'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "COM_PRICE_DETAIL_INFO" (
    "ID",
    "INSTALMENT",
    "INSTALMENT_TYPE",
    "MERCH_SUBSIDY_TYPE",
    "RATE_YEAR",
    "MAX_RATE_YEAR",
    "MIN_RATE_YEAR",
    "RATE_MERCH",
    "MAX_RATE_MERCH",
    "MIN_RATE_MERCH",
    "STOCK_RECORD",
    "SECOND_CONFIRM",
    "CREATE_USER",
    "CREATE_DT",
    "UPDATE_USER",
    "UPDATE_DT",
    "IS_DELETED",
    "APPROVE_STAT",
    "GROUP_DETAIL_ID",
    "IRR"
)
SELECT
    2026066000 + "X",
    CASE MOD("X", 4) WHEN 0 THEN '3' WHEN 1 THEN '6' WHEN 2 THEN '12' ELSE '24' END,
    'month',
    CASE WHEN MOD("X", 2) = 0 THEN 'merchant' ELSE 'platform' END,
    4 + "X" / 10.0,
    8 + "X" / 10.0,
    2 + "X" / 10.0,
    1 + "X" / 10.0,
    3 + "X" / 10.0,
    0.5 + "X" / 10.0,
    'Y',
    CASE WHEN MOD("X", 3) = 0 THEN 'Y' ELSE 'N' END,
    'sample_user',
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user',
    TIMESTAMP '2026-06-30 10:00:00',
    'N',
    'PASS',
    2026064000 + "X",
    5 + "X" / 10.0
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "COM_PRICE_PRICING" (
    "ID",
    "PRICE_PRO_ID",
    "INSTALMENT",
    "INSTALMENT_TYPE",
    "MERCH_SUBSIDY_TYPE",
    "STOCK_RECORD",
    "SECOND_CONFIRM",
    "CREATE_USER",
    "CREATE_DT",
    "UPDATE_USER",
    "UPDATE_DT",
    "IS_DELETED"
)
SELECT
    2026067000 + "X",
    2026062000 + "X",
    CASE MOD("X", 4) WHEN 0 THEN '3' WHEN 1 THEN '6' WHEN 2 THEN '12' ELSE '24' END,
    'month',
    CASE WHEN MOD("X", 2) = 0 THEN 'merchant' ELSE 'platform' END,
    'Y',
    CASE WHEN MOD("X", 3) = 0 THEN 'Y' ELSE 'N' END,
    'sample_user',
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user',
    TIMESTAMP '2026-06-30 10:00:00',
    'N'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "COM_PRICE_RATE_INFO" (
    "ID",
    "PRICING_ID",
    "RATE_TYPE",
    "MAX_RATE",
    "MIN_RATE",
    "FIXED_RATE",
    "ACTIVITY_RATE",
    "ACTIVITY_START_TIME",
    "ACTIVITY_END_TIME",
    "REAL_RATE",
    "CREATE_DT",
    "CREATE_USER",
    "UPDATE_DT",
    "UPDATE_USER",
    "IS_DELETED"
)
SELECT
    2026067500 + "X",
    2026067000 + "X",
    CASE WHEN MOD("X", 2) = 0 THEN 'year' ELSE 'month' END,
    9 + "X" / 10.0,
    2 + "X" / 10.0,
    4 + "X" / 10.0,
    3 + "X" / 10.0,
    TIMESTAMP '2026-07-01 00:00:00',
    TIMESTAMP '2026-12-31 23:59:59',
    5 + "X" / 10.0,
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user',
    TIMESTAMP '2026-06-30 10:00:00',
    'sample_user',
    'N'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "COM_PRICE_PRICING_BATCH" (
    "ID",
    "PRICE_PRO_ID",
    "STATUS",
    "ACTIVITY_START_TIME",
    "ACTIVITY_END_TIME",
    "CREATE_USER",
    "CREATE_DT",
    "UPDATE_USER",
    "UPDATE_DT",
    "IS_DELETED",
    "STORE_NO",
    "GROUP_ID",
    "VERSION"
)
SELECT
    2026068000 + "X",
    2026062000 + "X",
    CASE WHEN MOD("X", 6) = 0 THEN 'pending' ELSE 'valid' END,
    TIMESTAMP '2026-07-01 00:00:00',
    TIMESTAMP '2026-12-31 23:59:59',
    'sample_user',
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user',
    TIMESTAMP '2026-06-30 10:00:00',
    'N',
    'STQ202606' || LPAD("X", 2, '0'),
    2026063000 + "X",
    'v1'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "CREDIT_MERCH_USE" (
    "MERCH_NO",
    "CREDIT_AMT",
    "CREDIT_AMT_HIS",
    "CREDIT_AMT_CUR",
    "CREDIT_AMT_DAY",
    "CREDIT_AMT_DT",
    "CREDIT_AMT_HIS_DT",
    "CREDIT_AMT_CUR_DT",
    "CREDIT_AMT_DAY_DT"
)
SELECT
    'M202606' || LPAD("X", 2, '0'),
    100000 + "X" * 10000,
    500000 + "X" * 10000,
    80000 + "X" * 5000,
    20000 + "X" * 1000,
    TIMESTAMP '2026-06-30 10:00:00',
    TIMESTAMP '2026-06-30 10:00:00',
    TIMESTAMP '2026-06-30 10:00:00',
    TIMESTAMP '2026-06-30 10:00:00'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "CREDIT_SPECIAL_USE" (
    "SPECIAL_NO",
    "MERCH_NO",
    "CREDIT_AMT",
    "CREDIT_AMT_HIS",
    "CREDIT_AMT_CUR",
    "CREDIT_AMT_DAY",
    "CREDIT_AMT_DT",
    "CREDIT_AMT_DAY_DT",
    "CREDIT_AMT_CUR_DT",
    "CREDIT_AMT_HIS_DT"
)
SELECT
    'SP202606' || LPAD("X", 4, '0'),
    'M202606' || LPAD(MOD("X" - 1, 6) + 1, 2, '0'),
    50000 + "X" * 5000,
    200000 + "X" * 10000,
    40000 + "X" * 3000,
    10000 + "X" * 500,
    TIMESTAMP '2026-06-30 10:00:00',
    TIMESTAMP '2026-06-30 10:00:00',
    TIMESTAMP '2026-06-30 10:00:00',
    TIMESTAMP '2026-06-30 10:00:00'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "CREDIT_ACQ_APPLY" (
    "APPL_CODE",
    "BIG_APPL_CODE",
    "TYP_CDE",
    "TYP_SEQ",
    "CHANNEL_NO",
    "APPRV_AMT",
    "APPROVE_STAT",
    "USE_FLAG",
    "USE_AMT",
    "STORE_NO",
    "MERCH_NO",
    "CREATE_DT",
    "UPDATE_DT"
)
SELECT
    'CRAP202606' || LPAD("X", 4, '0'),
    'BIGCR202606' || LPAD(MOD("X" - 1, 4) + 1, 2, '0'),
    'store',
    'SEQ' || LPAD("X", 4, '0'),
    'CH' || LPAD(MOD("X" - 1, 4) + 1, 2, '0'),
    10000 + "X" * 1000,
    CASE WHEN MOD("X", 5) = 0 THEN 'PENDING' ELSE 'PASS' END,
    CASE WHEN MOD("X", 3) = 0 THEN 'Y' ELSE 'N' END,
    1000 + "X" * 100,
    'STQ202606' || LPAD("X", 2, '0'),
    'M202606' || LPAD(MOD("X" - 1, 6) + 1, 2, '0'),
    TIMESTAMP '2026-06-01 09:00:00',
    TIMESTAMP '2026-06-30 10:00:00'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "CRM_STORE_INTEREST_PRODUCT" (
    "STORE_NO",
    "PRODUCT_NO",
    "MERCH_NO",
    "REFUSE_CODE",
    "INTEREST_FREE",
    "APPROVE_STAT",
    "CMIS_EXIST",
    "CHARGE_UNIT",
    "PRODUCT_NAME",
    "CREATE_USER",
    "CREATE_DT",
    "UPDATE_USER",
    "UPDATE_DT",
    "STATE"
)
SELECT
    'STQ202606' || LPAD("X", 2, '0'),
    'PRD202606' || LPAD("X", 2, '0'),
    'M202606' || LPAD(MOD("X" - 1, 6) + 1, 2, '0'),
    CASE WHEN MOD("X", 6) = 0 THEN 'R001' ELSE NULL END,
    12 + "X",
    CASE WHEN MOD("X", 5) = 0 THEN 'PD' ELSE 'PA' END,
    'Y',
    CASE WHEN MOD("X", 2) = 0 THEN 'M' ELSE 'P' END,
    CASE MOD("X", 4) WHEN 0 THEN '教育分期' WHEN 1 THEN '医美分期' WHEN 2 THEN '消费分期' ELSE '零售分期' END,
    'sample_user',
    '2026-06-01 09:00:00',
    'sample_user',
    '2026-06-30 10:00:00',
    CASE WHEN MOD("X", 6) = 0 THEN 'N' ELSE 'Y' END
FROM SYSTEM_RANGE(1, 12);

INSERT INTO "DEPA_AGENCY_MAPPING_CONFIG" (
    "ID",
    "DEPA_ID",
    "COOP_AGENCY_ID",
    "UPDATE_TIME",
    "UPDATE_BY",
    "CREATE_TIME",
    "CREATE_BY",
    "DELETE_FLAG"
)
SELECT
    2026069000 + "X",
    'ORG' || LPAD(MOD("X" - 1, 4) + 1, 2, '0'),
    'AGENCY' || LPAD("X", 4, '0'),
    TIMESTAMP '2026-06-30 10:00:00',
    'sample_user',
    TIMESTAMP '2026-06-01 09:00:00',
    'sample_user',
    'N'
FROM SYSTEM_RANGE(1, 12);
