# Costing API — Integration Contract

`GET https://costing.amanatshahfabrics.com/api/fabricsCost`

Params: `user`, `password`, `code` (the costing number).
Backend: Apache / PHP 8.1.34 (Laravel-style payload).

## Responses

- **Success:** HTTP 200 `{"status":"success","data":{...159 fields...}}`
- **Any failure:** HTTP 400 `{"status":"error","data":"Something is wrong"}`
  - Identical for unknown code, bad/missing credentials, and missing params — **callers cannot distinguish auth failure from not-found**.

## Verified sample codes

- `06082502667` — Vogue Sourcing Ltd. / 100% Cotton, construction 40X30/108X62, GSM 111.54, orderQty 13430, breakEven 1.9066902037847 /yd, PI quote 2.1872
- `17072502459` — Vogue Sourcing Ltd. / 100% Cotton, construction 40X30/108X62, GSM 111.54, orderQty 10120, breakEven 1.5513265674211 /yd, PI quote 1.85912
- `18032500890` — Good earth / 30% Linen 70% Viscose, construction 20X20/69X56, GSM 146.25, orderQty 39535, breakEven 2.1140454434617 /yd, PI quote 2.35124

## Field groups (159 fields)

### Identification & document

| field | sample value |
|---|---|
| `id` | 6090 |
| `code` | 18032500890 |
| `approval_tracking_id` | 3834 |
| `approval_engine` | mla |
| `amendment_no` | 0 |
| `amendment_flag` | 0 |
| `poDate` | 2025-10-04 |
| `poNo` | 00577 |
| `referenceNo` | D-2945 |
| `costingType` | Bulk |
| `costType` | Regular Cost |
| `team_id` | 3 |
| `note` | As per Honorable Director Sir, Pick Charge will be - 0.65 Tk/pick. Quality as per repeat o... |
| `created_at` | 2025-03-18T06:49:51.000000Z |
| `updated_at` | 2026-08-12T09:14:52.000000Z |
| `created_by` | 14 |
| `updated_by` | None |
| `status` | Process |

### Buyer / brand

| field | sample value |
|---|---|
| `buyer_id` | 367 |
| `brand_id` | 43 |
| `buyerName` | Good earth |
| `buyer_name` | {"id": 367, "name": "Good earth", "contact_person": "", "email": "", "phone": "", "address... |

### Composition & fabric definition

| field | sample value |
|---|---|
| `composition` | 30% Linen 70% Viscose  |
| `declaredComposition` | 70% Viscose 30% Linen |
| `compositionType` | none |
| `fabric_cpmposition` | [{"id":152867,"cost_id":null,"count":"warpCount_1","fiber":"Viscose","blend":"70","created... |
| `fabric` | 6 |
| `febric_process_id` | 3 |
| `fabricDerivative` | General |
| `weaveType` | 1 |
| `weaveStyle` | 5 |
| `fabricFinishType` | ["1"] |
| `fabricFinishTypeString` | ["Soft Finish"] |
| `fabricShade` | 7 |
| `fabricType` | In-house |
| `seersuckerUp` | None |
| `seersuckerLow` | None |
| `color` | None |
| `quantity` | None |

### Yarn counts, ratios & prices

| field | sample value |
|---|---|
| `warpCount_1` | 20 |
| `warpCount_2` | None |
| `warpCount_3` | None |
| `warpCountRatio_1` | 1 |
| `warpCountRatio_2` | None |
| `warpCountRatio_3` | None |
| `weftCount_1` | 20 |
| `weftCount_2` | None |
| `weftCount_3` | None |
| `weftCountRatio_1` | 1 |
| `weftCountRatio_2` | None |
| `weftCountRatio_3` | None |
| `warp_yarn_price_1` | 4.8 |
| `warp_yarn_price_2` | 0 |
| `warp_yarn_price_3` | 0 |
| `weft_yarn_price_1` | 4.8 |
| `weft_yarn_price_2` | 0 |
| `weft_yarn_price_3` | 0 |
| `warp_yarn_price_1_flag` | 0 |
| `warp_yarn_price_2_flag` | 0 |
| `warp_yarn_price_3_flag` | 0 |
| `weft_yarn_price_1_flag` | 0 |
| `weft_yarn_price_2_flag` | 0 |
| `weft_yarn_price_3_flag` | 0 |
| `yarn_details` | {"warpCountYarnId_1":278,"warpCountYarnId_2":null,"warpCountYarnId_3":null,"weftCountYarnI... |
| `readCount` | 56/2 |
| `updateReadCountFlag` | 1 |

### Construction & loom parameters

| field | sample value |
|---|---|
| `epi` | 69 |
| `ppi` | 56 |
| `construction` | 20X20/69X56 |
| `declaredConstruction` | 20X20/68X57 |
| `width` | 58 |
| `cuttableWidth` | 56 |
| `widthText` | Finish Width |
| `rpm` | 650 |
| `loom` | 90 |
| `crimpWastage` | 12 |
| `mechShrinkage` | 13 |
| `warp_shrinkage` | None |
| `weft_shrinkage` | None |
| `warpShrinkage` | 4 |
| `weftShrinkage` | 5 |
| `yarnDyeingAllowance` |  |
| `coverFactor` | 27.951 |
| `totalEnds` | 4002 |
| `reedSpace` | 71.464285714286 |
| `pickLength` | 75.464285714286 |
| `fabricProduction` | 417.85714285714 |

### Greige inputs

| field | sample value |
|---|---|
| `greigeEpi` | None |
| `greigePpi` | None |
| `greigeFabPrice` | None |
| `greigeAllowances` | 5 |
| `greigeMS` | None |
| `greigeLandingCost` | 0 |
| `TotalGreigePrice` | 0 |
| `greigeWidth` | None |

### Cost inputs / rates

| field | sample value |
|---|---|
| `conversionRate` | 110 |
| `pickCharge` | 0.75 |
| `processCostTaka` | 46.64 |
| `processAllCosDescription` | Finishing Process Cost (TK): Finishing Cost(46.640) + Finish Types(0) + BlackShade(0) Span... |
| `finishingProcessTaka` | None |
| `commercialCost` | 0.02 |
| `fabricMendingCost` | 0.02 |
| `LcType` | 4 |
| `mediatorCommission` | None |
| `piMarkup` | 2.15 |
| `totalSmsCost` | 0 |
| `fabricTestingBill` | 0 |
| `tcBill` | 0.0000 |
| `totalCommission` | 0 |
| `extraCostBellowQty` | 0 |
| `printScreenFlag` | 0 |
| `screenCostAverageTaka` | 0 |
| `screenCostAverageUsd` | 0 |

### CALCULATED OUTPUTS (consumption)

| field | sample value |
|---|---|
| `gsm` | 146.25 |
| `warpYarnConsumption` | 0.1352 |
| `weftYarnConsumption` | 0.129 |
| `totalYarnConsumption` | (Warp) 0.1352 Kg/Yd & (Weft) 0.129 Kg/Yd |
| `yarnWillConsumed` | 0.2642 |

### CALCULATED OUTPUTS (cost & price)

| field | sample value |
|---|---|
| `warpYarnCost` | 0.64884278354527 |
| `weftYarnCost` | 0.61938447809823 |
| `totalYarnCost` | 1.2682272616435 |
| `weaveCost` | 0.38181818181818 |
| `newWeaveCost` | None |
| `processLabel` | Solid Dyeing Cost |
| `processCostUsd` | 0.424 |
| `updateProcessCostFlag` | 0 |
| `finishingProcessLabel` |  |
| `finishingProcessUsd` | None |
| `breakEvenPriceYds` | 2.1140454434617 |
| `breakEvenPriceMtr` | 2.3119200969697 |
| `piQuotPrice` | 2.35124 |
| `companyLossProfitYD` | 0.035954556538317 |
| `companyLossProfitMT` | 0.039319903030303 |

### Order quantity & colours

| field | sample value |
|---|---|
| `moq` | 3000 |
| `orderQty` | 39535 |
| `dyeing_color_qty` | {"color":["White","Olive","Black","Blue","Chocolate","Stone"],"qty":["6960","9935","16625"... |
| `bpo_qty` | None |

### Approval workflow

| field | sample value |
|---|---|
| `owner_approval` | Submitted |
| `owner_approval_date` | 2025-11-11 04:42:20 |
| `rejected_by` | 12 |
| `rejected_date` | 2025-11-10 13:36:54 |
| `first_approval` | Approved |
| `first_approval_id` | 31 |
| `first_approval_date` | 2025-11-11 05:54:29 |
| `second_approval` | Approved |
| `second_approval_date` | 2025-11-11 07:58:40 |
| `second_approval_id` | 27 |
| `third_approval` | Approved |
| `third_approval_date` | 2025-11-11 09:02:19 |
| `third_approval_id` | 34 |
| `final_approval` | Approved |
| `final_approval_date` | 2025-11-20 19:08:52 |
| `final_approval_id` | 12 |

### Planning / BPO handoff

| field | sample value |
|---|---|
| `planningFlag` | 0 |
| `dispo` | None |
| `planning_create_date` | None |
| `planning_update_date` | None |
| `planning_user_id` | None |
| `bpoGraceDay` | None |

## Nested JSON-in-string fields

These arrive as **strings containing JSON** and must be parsed twice:

- `fabric_cpmposition` — array of {count, fiber, blend, resultant} (note: field name is misspelled in the API).
- `yarn_details` — warp/weft yarn ids + names per count slot.
- `dyeing_color_qty` — {color[], qty[], screen[]} parallel arrays.
- `fabricFinishType` — array of finish-type ids; `fabricFinishTypeString` is the resolved label.

`buyer_name` is a real nested object (not a string).
