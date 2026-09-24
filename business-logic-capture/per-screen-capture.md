# ASG Dynamic — Per-Screen Business Logic Capture

Auto-generated from an authenticated crawl of asgdynamic.com. One section per screen.
Fields: t=type,n=name. Events: DOM event → JS handler. Actions: server endpoints hit by the screen's JS.

## accDashboard — Grails

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)
- **Grid columns:** Product, Target, Production, Downtime(min), Waste Percentage, Efficiency(OEE), Material, Current Stock (kg/L), Reorder Level (kg/L), Last Updated, Material, Current Stock (yard), Reorder Level (yard), Last Updated, Sales by region, In Production, Pending, dispatched, Buyer, product type

## accounts — Chart OF Accounts

- **Endpoints:** /accounts/edit, /accounts/index, /accounts/remove, /accounts/write
- **Master-detail:** no | fields: 11 | events: 6 | functions: 6
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `$(`
    - `onclick` on `?` → `$(`
    - `onclick` on `?` → `accountsReset()`
- **JS functions:** `accountsReset`, `addAccounts`, `deleteAccounts`, `editAccounts`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), accountsTree_q(text), parentCategory(text), caption(text)

## accountsPeriod — Period

- **Endpoints:** /accountsPeriod/edit, /accountsPeriod/index, /accountsPeriod/remove, /accountsPeriod/write
- **Master-detail:** no | fields: 8 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `periodCreateModalClick();`
    - `onclick` on `?` → `periodReset()`
- **JS functions:** `periodApprove`, `periodChangeStatus`, `periodClose`, `periodConfirm`, `periodCreateModalClick`, `periodDelete`, `periodEdit`, `periodOpen`, `periodPopulate`, `periodReport`, `periodReset`, `periodResetMasterForm`, `periodScriptInit`, `periodShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), periodMonth(select)
- **Grid columns:** SL, Code, Name, Start Date, Close Date, Primary Open, Open, Primary Close, Close, Open By, Close By, Action

## accountsPolicy — Production Delivery Schedule

- **Endpoints:** (none in script)
- **Master-detail:** YES | fields: 67 | events: 15 | functions: 38
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `?` → `unitChangeFunction(10223)`
    - `onchange` on `?` → `unitChangeFunction(10224)`
    - `onchange` on `?` → `unitChangeFunction(10225)`
    - `onchange` on `?` → `storeChangeFunction(10226)`
    - `onchange` on `?` → `storeChangeFunction(10227)`
    - `onchange` on `?` → `storeChangeFunction(10228)`
    - `onchange` on `?` → `storeChangeFunction(10229)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `pds_dtlSetAddBtn` → `pds_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `pdsReset()`
    - `onclick` on `po_dtlSetAddBtn` → `po_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `poReset()`
- **JS functions:** `hs_pds_dtlSetDeleteEvent`, `hs_pds_dtlSetEditEvent`, `hs_po_dtlSetDeleteEvent`, `hs_po_dtlSetEditEvent`, `pdsApprove`, `pdsChangeStatus`, `pdsClose`, `pdsConfirm`, `pdsCreateModalClick`, `pdsDelete`, `pdsEdit`, `pdsOpen`, `pdsPopulate`, `pdsReport`, `pdsReset`, `pdsResetMasterForm`, `pdsScriptInit`, `pdsShow`, `pds_dtlSetCreateUpdateEvent`, `poApprove`, `poChangeStatus`, `poClose`, `poConfirm`, `poCreateModalClick`, `poDelete`, `poEdit`, `poOpen`, `poPopulate`, `poReport`, `poReset`, `poResetMasterForm`, `poScriptInit`, `poShow`, `po_dtlSetCreateUpdateEvent`, `reset_pds_dtlSet_form`, `reset_po_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), app-theme-dark-mode(checkbox), businessUnitSession(radio), businessUnitSession(radio), businessUnitSession(radio), storeSession(radio), storeSession(radio), storeSession(radio), storeSession(radio), code(text), productionOrderCode(text), salesOrderCode(text), transactionDate(text), transactionDatePO(text), transactionDateSO(text), stakeholder(text), customerBrand(text), customerGarments(text), code(text), productionOrderCode(text), salesOrderCode(text), transactionDate(text), transactionDatePO(text), transactionDateSO(text), stakeholder(text), customerBrand(text), customerGarments(text)
- **Grid columns:** SL, Schedule No, BPO No, Booking No, Date, Booking Date, BPO Date, Buyer, Brand, Garments, Action, SL, Garments, Style, Color code, Color name, Strike Off Reference, Color Reference, Lab Dip Reference, PI Number

## allBookingReport — All Booking

- **Endpoints:** /allBookingReport/edit, /allBookingReport/index, /allBookingReport/remove, /allBookingReport/write
- **Master-detail:** no | fields: 17 | events: 6 | functions: 7
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `soResetBtn` → `soSearch()`
    - `onclick` on `?` → `sobtnOne()`
- **JS functions:** `soReport`, `soReset`, `soSearch`, `soShow`, `sobtnOne`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), bookingType(select), specialType(select), currency(select), fabricType(select), stakeholder(select), customerBrand(select), customerGarments(select), marketingPerson(select), swatchNumber(text), transaction_date_start(text), transaction_date_end(text)
- **Grid columns:** SL, Booking NO, Buyer, Garments, Team, Transaction date, Currency, BK Qty, AVG Price, Total Amount, BPO Code, BPO Qty, BPO Amount, Status, Operation Status, Action

## allProductionOrderReport — All Production Order

- **Endpoints:** /allProductionOrderReport/edit, /allProductionOrderReport/index, /allProductionOrderReport/remove, /allProductionOrderReport/write
- **Master-detail:** no | fields: 17 | events: 6 | functions: 7
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `proReset()`
    - `onclick` on `proResetBtn` → `proSearch()`
    - `onclick` on `?` → `probtnOne()`
- **JS functions:** `proReport`, `proReset`, `proSearch`, `proShow`, `probtnOne`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), bookingType(select), specialType(select), currency(select), fabricType(select), stakeholder(select), customerBrand(select), customerGarments(select), marketingPerson(select), swatchNumber(text), transaction_date_start(text), transaction_date_end(text)
- **Grid columns:** SL, BPO Number, Buyer, Garments, Team, Transaction date, Currency, BK Qty, AVG Price, Total Amount, Booking Number, BPO Qty, Total Amount, Status, Action

## analysisDISPO — DISPO Analysis Update

- **Endpoints:** /analysisDISPO/edit, /analysisDISPO/index, /analysisDISPO/remove, /analysisDISPO/write
- **Master-detail:** YES | fields: 64 | events: 8 | functions: 26
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `warpDetailAdd(this);`
    - `onclick` on `?` → `weftDetailAdd(this);`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `soExtraOneBtn()`
    - `onclick` on `dispo_dtlSetWarpResetBtn` → `dispo_dtlSetWarpCustomCreateUpdateEvent()`
- **JS functions:** `alertWithReturn`, `dispoApprove`, `dispoChangeStatus`, `dispoConfirm`, `dispoDelete`, `dispoEdit`, `dispoReport`, `dispoReset`, `dispoShow`, `dispo_dtlSetCustomCreateUpdateEvent`, `dispo_dtlSetWarpCreateUpdateEvent`, `dispo_dtlSetWarpCustomCreateUpdateEvent`, `dispo_dtlSetWeftCreateUpdateEvent`, `hs_dispo_dtlSetWarpDeleteEvent`, `hs_dispo_dtlSetWarpEditEvent`, `hs_dispo_dtlSetWarpViewTableEvent`, `hs_dispo_dtlSetWeftDeleteEvent`, `hs_dispo_dtlSetWeftEditEvent`, `hs_dispo_dtlSetWeftViewTableEvent`, `reset_dispo_dtlSetWarp_form`, `reset_dispo_dtlSetWeft_form`, `soExtraOneBtn`, `warpDetailAdd`, `weftDetailAdd`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), fabricType(select), construction(text), epi(number), ppi(number), finishedFabricWidth(number), reedCount(number), endsPerDent(number), selvedgeEnum(select), selvedgeEndsPerDent(number), buyerName(text), referenceStyle(text), colorCode(text), weaveType(select), fabricsFinishType(text), fabricsComposition(select), bookingType(select), processLossPercentage(number), contractionPercentage(number), crimpPercentage(number), mechanical_shrinkage(number), yarn_dyeing_allowance(number), pickLength(number), finishedFabricLengthYard(number), buyerGsmBeforeWash(number), buyerGsmAfterWash(number), cuttableWidth(text), warpShrinkagePercent(number), weftShrinkagePercent(number), washType(text), greigQuantityMeter(number), loomProductionMeter(number), warpLengthMeter(number), warpConsumption(number), weftConsumption(number), consumptionPerYards(text), totalCf(number), calculativeGsm(number), greigEnds(number), greigPick(number), greigWidth(number), reedWidth(number), groundEnds(number), finishedFabricLengthMeter(number), creelRepeat(number), noOfBeam(number), extraConeLength(number), totalConsumption(number), dispoNo(text), trackingNumber(text), actualDeliveryDate(text), deliveryDate(text), warpYarnType(select), weftYarnType(select), input_count(text), yarn_repeat(number), color_name(text)
- **Grid columns:** SL, Item, Dispo NO, Color Name, EPI, PPI, Construction, Composition, Fabrics Finish Type, Action, SL, Count, Yarn Repeat, Color Name, Consumption (KG), No Of Cone, Cone Length, Action, SL, Count

## approvalReport — Approval Report

- **Endpoints:** /approvalReport/edit, /approvalReport/index, /approvalReport/remove, /approvalReport/write
- **Master-detail:** no | fields: 9 | events: 6 | functions: 7
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `apvReset()`
    - `onclick` on `apvResetBtn` → `apvSearch()`
    - `onclick` on `?` → `apvbtnOne()`
- **JS functions:** `apvReport`, `apvReset`, `apvSearch`, `apvShow`, `apvbtnOne`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), documentUrl(select), marketingGroup(select), approveStatus(select)
- **Grid columns:** SL, Approval No, Document Name, Team, BK/PRO/DO Number, Submission date, Created By, Waiting For, Waiting Approval Users, Approval Status, Action

## approvalSetup — Approval Setup

- **Endpoints:** /approvalSetup/edit, /approvalSetup/index, /approvalSetup/remove, /approvalSetup/write
- **Master-detail:** YES | fields: 16 | events: 6 | functions: 20
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `stpCreateModalClick();`
    - `onclick` on `stp_dtlSetAddBtn` → `stp_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `stpReset()`
- **JS functions:** `hs_stp_dtlSetDeleteEvent`, `hs_stp_dtlSetEditEvent`, `reset_stp_dtlSet_form`, `stpApprove`, `stpChangeStatus`, `stpClose`, `stpConfirm`, `stpCreateModalClick`, `stpDelete`, `stpEdit`, `stpOpen`, `stpPopulate`, `stpReport`, `stpReset`, `stpResetMasterForm`, `stpScriptInit`, `stpShow`, `stp_dtlSetCreateUpdateEvent`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), documentUrlsSet(select), marketingGroup(select)
- **Grid columns:** SL, Code, Name, Document Path, Approval Group, Action, SL, Type, Users, Serial, Action, SL, Type, Users, Serial

## bank — Bank

- **Endpoints:** /bank/edit, /bank/index, /bank/remove, /bank/write
- **Master-detail:** no | fields: 12 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `bankCreateModalClick();`
    - `onclick` on `?` → `bankReset()`
- **JS functions:** `bankApprove`, `bankChangeStatus`, `bankClose`, `bankConfirm`, `bankCreateModalClick`, `bankDelete`, `bankEdit`, `bankOpen`, `bankPopulate`, `bankReport`, `bankReset`, `bankResetMasterForm`, `bankScriptInit`, `bankShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), binNumber(text), shortName(text), sortOrder(number)
- **Grid columns:** SL, Code, Name, Bin number, Short name, Sort Order, Action

## bnkAccounts — Bnk Accounts

- **Endpoints:** /bnkAccounts/edit, /bnkAccounts/index, /bnkAccounts/remove, /bnkAccounts/write
- **Master-detail:** no | fields: 16 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `bnkAccountsCreateModalClick();`
    - `onclick` on `?` → `bnkAccountsReset()`
- **JS functions:** `bnkAccountsApprove`, `bnkAccountsChangeStatus`, `bnkAccountsClose`, `bnkAccountsConfirm`, `bnkAccountsCreateModalClick`, `bnkAccountsDelete`, `bnkAccountsEdit`, `bnkAccountsOpen`, `bnkAccountsPopulate`, `bnkAccountsReport`, `bnkAccountsReset`, `bnkAccountsResetMasterForm`, `bnkAccountsScriptInit`, `bnkAccountsShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), bank(select), bin_number(text), branch_name(text), branch_address(text), bank_routing_number(text), swift_code(text), area_code(text)
- **Grid columns:** SL, Code, Account NO, Bank name, BIN number, Branch name, Branch address, Routing number, Swift code, Area code, Action

## booking — Booking

- **Endpoints:** /booking/edit, /booking/index, /booking/remove, /booking/write
- **Master-detail:** YES | fields: 95 | events: 10 | functions: 31
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `create_so_factoryAddress` → `onclick_so_factoryAddress(this)`
    - `onclick` on `?` → `onclick_so_dtlSet_fabricsCost(this)`
    - `onclick` on `?` → `onclick_so_dtlSet_gsmCalculated(this)`
    - `onclick` on `?` → `onclick_so_dtlSet_leadTime(this)`
    - `onclick` on `so_dtlSetAddBtn` → `so_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `so_dtlTcSetAddBtn` → `so_dtlTcSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `so_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `docFilePathFileDownloadEvent`, `hs_so_dtlSetAddEvent`, `hs_so_dtlSetDeleteEvent`, `hs_so_dtlSetEditEvent`, `hs_so_dtlSetViewTableEvent`, `hs_so_dtlSet_dtlLineDeleteEvent`, `hs_so_dtlSet_dtlLineEditEvent`, `hs_so_dtlSet_dtlLineResetEvent`, `hs_so_dtlSet_dtlLineViewTableEvent`, `hs_so_dtlTcSetDeleteEvent`, `hs_so_dtlTcSetEditEvent`, `hs_so_dtlTcSetViewTableEvent`, `onclick_so_dtlSet_fabricsCost`, `onclick_so_dtlSet_gsmCalculated`, `onclick_so_dtlSet_leadTime`, `reset_so_dtlSet_form`, `reset_so_dtlTcSet_form`, `soApprove`, `soChangeStatus`, `soConfirm`, `soDelete`, `soEdit`, `soReport`, `soReset`, `soSaveAlert`, `soShow`, `so_dtlSetCreateUpdateEvent`, `so_dtlSet_dtlLineCrUpEvent`, `so_dtlTcSetCreateUpdateEvent`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), bookingType(select), currency(select), specialType(select), transactionDate(text), deliveryRequiredDate(text), stakeholder(select), customerBrand(select), customerGarments(select), customerCosting(text), priceInMeter(checkbox), factoryAddress(textarea), marketingPerson(select), remarks(textarea), colorCode(text), colorName(text), fabricsStyle(text), colorReference(text), strikeOffReference(text), labDipReference(text), loomReference(text), transactionQty(number), unitPrice(number), remarks(textarea)
- **Grid columns:** SL, Booking No, Booking Date, Buyer, Garments, Booking Qty, BPO No, BPO Qty, Booking Status, BPO Status, Action, SL, Item, Construction, Composition, Weave Type, Weave Styles, Finished Width (Inch), Cuttable Width (Inch), Color breakdown

## bookingReport — Booking Report

- **Endpoints:** /bookingReport/edit, /bookingReport/index, /bookingReport/remove, /bookingReport/write
- **Master-detail:** no | fields: 17 | events: 6 | functions: 7
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `soResetBtn` → `soSearch()`
    - `onclick` on `?` → `sobtnOne()`
- **JS functions:** `soReport`, `soReset`, `soSearch`, `soShow`, `sobtnOne`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), bookingType(select), specialType(select), currency(select), fabricType(select), stakeholder(select), customerBrand(select), customerGarments(select), marketingPerson(select), swatchNumber(text), transaction_date_start(text), transaction_date_end(text)
- **Grid columns:** SL, Booking NO, Buyer, Garments, Team, Transaction date, Currency, BK Qty, AVG Price, Total Amount, BPO Code, BPO Qty, BPO Amount, Status, Operation Status, Action

## bookingRevision — Booking Revision

- **Endpoints:** /bookingRevision/edit, /bookingRevision/index, /bookingRevision/remove, /bookingRevision/write
- **Master-detail:** YES | fields: 91 | events: 9 | functions: 27
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `create_so_factoryAddress` → `onclick_so_factoryAddress(this)`
    - `onclick` on `?` → `onclick_so_dtlSet_fabricsCost(this)`
    - `onclick` on `?` → `onclick_so_dtlSet_gsmCalculated(this)`
    - `onclick` on `?` → `onclick_so_dtlSet_leadTime(this)`
    - `onclick` on `so_dtlSetAddBtn` → `so_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `so_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `docFilePathFileDownloadEvent`, `hs_so_dtlSetAddEvent`, `hs_so_dtlSetDeleteEvent`, `hs_so_dtlSetEditEvent`, `hs_so_dtlSetViewTableEvent`, `hs_so_dtlSet_dtlLineDeleteEvent`, `hs_so_dtlSet_dtlLineEditEvent`, `hs_so_dtlSet_dtlLineResetEvent`, `hs_so_dtlSet_dtlLineViewTableEvent`, `onclick_so_dtlSet_fabricsCost`, `onclick_so_dtlSet_gsmCalculated`, `onclick_so_dtlSet_leadTime`, `reset_so_dtlSet_form`, `soApprove`, `soChangeStatus`, `soConfirm`, `soDelete`, `soEdit`, `soEditExtraFunction`, `soPopulate`, `soReport`, `soReset`, `soShow`, `so_dtlSetCreateUpdateEvent`, `so_dtlSet_dtlLineCrUpEvent`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), bookingType(select), currency(select), specialType(select), transactionDate(text), deliveryRequiredDate(text), stakeholder(select), customerBrand(select), customerGarments(select), customerCosting(text), factoryAddress(textarea), marketingPerson(select), remarks(textarea), colorCode(text), colorName(text), fabricsStyle(text), colorReference(text), strikeOffReference(text), labDipReference(text), transactionQty(number), unitPrice(number), remarks(textarea)
- **Grid columns:** SL, Booking No, Buyer, Garments, Booking Date, Currency, BK Qty, AVG Price, Total Amount, Status, Action, SL, Item, Construction, Composition, Weave Type, Weave Styles, Finished Width (Inch), Cuttable Width (Inch), Color breakdown

## businessUnit — Business Unit

- **Endpoints:** /businessUnit/edit, /businessUnit/index, /businessUnit/remove, /businessUnit/write
- **Master-detail:** no | fields: 17 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `businessUnitCreateModalClick();`
    - `onclick` on `?` → `businessUnitReset()`
- **JS functions:** `businessUnitApprove`, `businessUnitChangeStatus`, `businessUnitClose`, `businessUnitConfirm`, `businessUnitCreateModalClick`, `businessUnitDelete`, `businessUnitEdit`, `businessUnitOpen`, `businessUnitPopulate`, `businessUnitReport`, `businessUnitReset`, `businessUnitResetMasterForm`, `businessUnitScriptInit`, `businessUnitShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), shortName(text), address(text), contactNo(text), phoneNo(text), emailAddress(text), supervisor(select), organization(select), sortOrder(number)
- **Grid columns:** SL, Code, Name, Short Name, Address, Contact NO, Phone NO, Email Address, Supervisor, Organization, Sort Order, Action

## ciClosing — Page Not Found

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## ciIssuing — Page Not Found

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## ciPayment — Page Not Found

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## ciRealization — Page Not Found

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## ciReceiving — Page Not Found

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## comCustomer — Commercial Customer

- **Endpoints:** /comCustomer/edit, /comCustomer/index, /comCustomer/remove, /comCustomer/write
- **Master-detail:** no | fields: 24 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `customerCreateModalClick();`
    - `onclick` on `?` → `customerReset()`
- **JS functions:** `customerApprove`, `customerChangeStatus`, `customerClose`, `customerConfirm`, `customerCreateModalClick`, `customerDelete`, `customerEdit`, `customerOpen`, `customerPopulate`, `customerReport`, `customerReset`, `customerResetMasterForm`, `customerScriptInit`, `customerShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), address(text), contactNo(text), phoneNo(text), emailAddress(text), contactPerson(text), contactPersonMobile(text), bondLicence(text), tinNumber(text), ircNumber(text), ercNumber(text), binNumber(text), eBinNumber(text), vatNumber(text), shippingAddress(text), billingAddress(text)
- **Grid columns:** SL, Code, Name, Contact NO, Telephone NO, Email Address, Contact Person, Contact Persons Mobile, Bond Licence, Brand, Garments, Buying House, Action

## comDashboard — Grails

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)
- **Grid columns:** Material, Current Stock (kg/L), Reorder Level (kg/L), Last Updated, Source, Total, Trend

## comDocName — Document Names

- **Endpoints:** /comDocName/edit, /comDocName/index, /comDocName/remove, /comDocName/write
- **Master-detail:** no | fields: 11 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `comDocNameCreateModalClick();`
    - `onclick` on `?` → `comDocNameReset()`
- **JS functions:** `comDocNameApprove`, `comDocNameChangeStatus`, `comDocNameClose`, `comDocNameConfirm`, `comDocNameCreateModalClick`, `comDocNameDelete`, `comDocNameEdit`, `comDocNameOpen`, `comDocNamePopulate`, `comDocNameReport`, `comDocNameReset`, `comDocNameResetMasterForm`, `comDocNameScriptInit`, `comDocNameShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), conditionType(select), sortOrder(number)
- **Grid columns:** SL, Code, Name, Type, Sort Order, Action

## comTermsAndConditions — Commercial Terms and conditions

- **Endpoints:** /comTermsAndConditions/edit, /comTermsAndConditions/index, /comTermsAndConditions/remove, /comTermsAndConditions/write
- **Master-detail:** no | fields: 14 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `comTncCreateModalClick();`
    - `onclick` on `?` → `comTncReset()`
- **JS functions:** `comTncApprove`, `comTncChangeStatus`, `comTncClose`, `comTncConfirm`, `comTncCreateModalClick`, `comTncDelete`, `comTncEdit`, `comTncOpen`, `comTncPopulate`, `comTncReport`, `comTncReset`, `comTncResetMasterForm`, `comTncScriptInit`, `comTncShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), bodyText(textarea), conditionType(select), isDefault(checkbox), sortOrder(number), isDefault(checkbox)
- **Grid columns:** SL, Code, Name, Condition Body, Type, Default, Sort Order, Action

## commercialCostHead — LC Cost Head

- **Endpoints:** /commercialCostHead/edit, /commercialCostHead/index, /commercialCostHead/remove, /commercialCostHead/write
- **Master-detail:** no | fields: 11 | events: 3 | functions: 4
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **JS functions:** `comrcCostHeadEdit`, `comrcCostHeadShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), caption(text), docType(select), accHead(select), sortOrder(number)
- **Grid columns:** SL, CAPTION, COA TITLE, ACTION

## consumptionSpr — Consumption SPR

- **Endpoints:** /consumptionSpr/edit, /consumptionSpr/index, /consumptionSpr/remove, /consumptionSpr/write
- **Master-detail:** YES | fields: 39 | events: 8 | functions: 38
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `spr_tab` → `sprCreateModalClick();`
    - `onclick` on `spr_dtlSetAddBtn` → `spr_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `sprReset()`
    - `onclick` on `pro_dtlSetAddBtn` → `pro_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `proReset()`
- **JS functions:** `hs_pro_dtlSetDeleteEvent`, `hs_pro_dtlSetEditEvent`, `hs_spr_dtlSetDeleteEvent`, `hs_spr_dtlSetEditEvent`, `proApprove`, `proChangeStatus`, `proClose`, `proConfirm`, `proCreateModalClick`, `proDelete`, `proEdit`, `proOpen`, `proPopulate`, `proReport`, `proReset`, `proResetMasterForm`, `proScriptInit`, `proShow`, `pro_dtlSetCreateUpdateEvent`, `reset_pro_dtlSet_form`, `reset_spr_dtlSet_form`, `sprApprove`, `sprChangeStatus`, `sprClose`, `sprConfirm`, `sprCreateModalClick`, `sprDelete`, `sprEdit`, `sprOpen`, `sprPopulate`, `sprReport`, `sprReset`, `sprResetMasterForm`, `sprScriptInit`, `sprShow`, `spr_dtlSetCreateUpdateEvent`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), transactionDate(text), leadTime(number), pro_code(text), remarks(textarea), code(text), transactionDatePRO(text), transactionDate(text), leadTime(number), remarks(textarea)
- **Grid columns:** SL, SPR No, BPO No, Date, Lead Time (days), BPO code, BPO date, Confirm Status, Checked Status, Approve Status, Action, SL, Item, Consumption Qty, Stock Qty, PRV ISSUE Qty, PRV Qty, SPR Qty, Specification, Action

## crmDashboard — Grails

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## currencyConversion — Currency Conversion

- **Endpoints:** /currencyConversion/edit, /currencyConversion/index, /currencyConversion/remove, /currencyConversion/write
- **Master-detail:** no | fields: 12 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `conversionCreateModalClick();`
    - `onclick` on `?` → `conversionReset()`
- **JS functions:** `conversionApprove`, `conversionChangeStatus`, `conversionClose`, `conversionConfirm`, `conversionCreateModalClick`, `conversionDelete`, `conversionEdit`, `conversionOpen`, `conversionPopulate`, `conversionReport`, `conversionReset`, `conversionResetMasterForm`, `conversionScriptInit`, `conversionShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), currency(select), startDate(text), endDate(text), conversionRate(number)
- **Grid columns:** SL, Code, Currency, StartDate, End Date, Conversion Rate, Conversion Rate, Action

## customer — Marketing Customer

- **Endpoints:** /customer/edit, /customer/index, /customer/remove, /customer/write
- **Master-detail:** no | fields: 30 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `customerCreateModalClick();`
    - `onclick` on `?` → `customerReset()`
- **JS functions:** `customerApprove`, `customerChangeStatus`, `customerClose`, `customerConfirm`, `customerCreateModalClick`, `customerDelete`, `customerEdit`, `customerOpen`, `customerPopulate`, `customerReport`, `customerReset`, `customerResetMasterForm`, `customerScriptInit`, `customerShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), address(text), contactNo(text), phoneNo(text), emailAddress(text), contactPerson(text), contactPersonMobile(text), bondLicence(text), tinNumberDummy(text), ircNumberDummy(text), ercNumberDummy(text), binNumberDummy(text), eBinNumberDummy(text), vatNumberDummy(text), shippingAddress(textarea), billingAddress(textarea), isBrand(checkbox), isGarments(checkbox), isBuyingHouse(checkbox), isBrand(checkbox), isGarments(checkbox), isBuyingHouse(checkbox)
- **Grid columns:** SL, Code, Name, Contact NO, Telephone NO, Email Address, Contact Person, Contact Persons Mobile, Bond Licence, Brand, Garments, Buying House, Action

## deliveryOrder — Delivery Order

- **Endpoints:** /deliveryOrder/edit, /deliveryOrder/index, /deliveryOrder/remove, /deliveryOrder/write
- **Master-detail:** YES | fields: 28 | events: 7 | functions: 25
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `create_dlo_dtlSet_dtlLine_transactionQty` → `dlo_dtlSet_dtlLine_transactionQtyOnChangeEvent(this)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_dlo_dtlSet_dtlLineResetEvent()`
    - `onclick` on `dlo_dtlSet_dtlLineResetBtn` → `dlo_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `dloApprove`, `dloChangeStatus`, `dloConfirm`, `dloDelete`, `dloEdit`, `dloReport`, `dloReset`, `dloShow`, `dlo_dtlSetCreateUpdateEvent`, `dlo_dtlSet_dtlLineCrUpEvent`, `dlo_dtlSet_dtlLine_transactionQtyOnChangeEvent`, `hs_dlo_dtlSetAddEvent`, `hs_dlo_dtlSetDeleteEvent`, `hs_dlo_dtlSetEditEvent`, `hs_dlo_dtlSetViewTableEvent`, `hs_dlo_dtlSet_dtlLineDeleteEvent`, `hs_dlo_dtlSet_dtlLineEditEvent`, `hs_dlo_dtlSet_dtlLineResetEvent`, `hs_dlo_dtlSet_dtlLineViewTableEvent`, `reset_dlo_dtlSet_form`, `soPopulate`, `soReceive`, `soReport`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), transactionDate(text), customerGarments(text), storeConcern(text), storeConcernContact(text), shippingAddress(textarea), remarks(textarea), colorCode(text), colorName(text), fabrics_style(text), transactionQtySchedule(text), prevTransactionQty(text), transactionQty(number), remarks(textarea)
- **Grid columns:** SL, Delivery Code, Schedule Code, Garments, BPO NO, Dispo NO, PI NO, LC NO, DO Date, DO Qty, Fabrics Delivery NO, Fabrics Delivery Qty, DO Status, Action, SL, BPO NO, BPO Date, Color breakdown, Action, SL

## department — Department

- **Endpoints:** /department/edit, /department/index, /department/remove, /department/write
- **Master-detail:** no | fields: 10 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `departmentCreateModalClick();`
    - `onclick` on `?` → `departmentReset()`
- **JS functions:** `departmentApprove`, `departmentChangeStatus`, `departmentClose`, `departmentConfirm`, `departmentCreateModalClick`, `departmentDelete`, `departmentEdit`, `departmentOpen`, `departmentPopulate`, `departmentReport`, `departmentReset`, `departmentResetMasterForm`, `departmentScriptInit`, `departmentShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), sortOrder(number)
- **Grid columns:** SL, Code, Name, Sort Order, Action

## designation — Designation

- **Endpoints:** /designation/edit, /designation/index, /designation/remove, /designation/write
- **Master-detail:** no | fields: 10 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `designationCreateModalClick();`
    - `onclick` on `?` → `designationReset()`
- **JS functions:** `designationApprove`, `designationChangeStatus`, `designationClose`, `designationConfirm`, `designationCreateModalClick`, `designationDelete`, `designationEdit`, `designationOpen`, `designationPopulate`, `designationReport`, `designationReset`, `designationResetMasterForm`, `designationScriptInit`, `designationShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), sortOrder(number)
- **Grid columns:** SL, Code, Name, Sort Order, Action

## directIssue — Direct Issue

- **Endpoints:** /directIssue/edit, /directIssue/index, /directIssue/remove, /directIssue/write
- **Master-detail:** YES | fields: 17 | events: 6 | functions: 20
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `directIssueCreateModalClick();`
    - `onclick` on `directIssue_dtlSetAddBtn` → `directIssue_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `directIssueReset()`
- **JS functions:** `directIssueApprove`, `directIssueChangeStatus`, `directIssueClose`, `directIssueConfirm`, `directIssueCreateModalClick`, `directIssueDelete`, `directIssueEdit`, `directIssueOpen`, `directIssuePopulate`, `directIssueReport`, `directIssueReset`, `directIssueResetMasterForm`, `directIssueScriptInit`, `directIssueShow`, `directIssue_dtlSetCreateUpdateEvent`, `hs_directIssue_dtlSetDeleteEvent`, `hs_directIssue_dtlSetEditEvent`, `reset_directIssue_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), inventoryStore(select), transactionDate(text), remarks(textarea)
- **Grid columns:** SL, Issue No, Date, Period, Item Store, Action, SL, Item, Stock Qty, Issue Quantity, Specification, Action, SL, Item, Stock Qty, Issue Quantity, Specification

## directReceive — Direct Receive

- **Endpoints:** /directReceive/edit, /directReceive/index, /directReceive/remove, /directReceive/write
- **Master-detail:** YES | fields: 17 | events: 6 | functions: 20
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `directReceiveCreateModalClick();`
    - `onclick` on `directReceive_dtlSetAddBtn` → `directReceive_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `directReceiveReset()`
- **JS functions:** `directReceiveApprove`, `directReceiveChangeStatus`, `directReceiveClose`, `directReceiveConfirm`, `directReceiveCreateModalClick`, `directReceiveDelete`, `directReceiveEdit`, `directReceiveOpen`, `directReceivePopulate`, `directReceiveReport`, `directReceiveReset`, `directReceiveResetMasterForm`, `directReceiveScriptInit`, `directReceiveShow`, `directReceive_dtlSetCreateUpdateEvent`, `hs_directReceive_dtlSetDeleteEvent`, `hs_directReceive_dtlSetEditEvent`, `reset_directReceive_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), inventoryStore(select), transactionDate(text), remarks(textarea)
- **Grid columns:** SL, Receive No, Date, Period, Item Store, Action, SL, Item, Stock Qty, Receive Quantity, Specification, Action, SL, Item, Stock Qty, Receive Quantity, Specification

## dispoAcknowledgement — DISPO Acknowledgement

- **Endpoints:** /dispoAcknowledgement/edit, /dispoAcknowledgement/index, /dispoAcknowledgement/remove, /dispoAcknowledgement/write
- **Master-detail:** no | fields: 44 | events: 4 | functions: 10
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `soReset()`
- **JS functions:** `dispoApprove`, `dispoChangeStatus`, `dispoConfirm`, `dispoDelete`, `dispoEdit`, `dispoReport`, `dispoReset`, `dispoShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), dispo_no(text), construction_so(text), customer_name(text), item_caption(text), sales_order_code(text), marketing_person(text), booking_type_caption(text), composition_caption_so(text), sales_order_date(text), construction(text), gsm(text), finishedWidth(text), bookingType(text), fabricsComposition(text), weaveType(text), fabricsFinishType(text), noteForAnalysis(text), reedCount(text), endsPerDent(text), selvage(text), selvageEndPerDent(text), selvageEndPerDentOne(text), fabricsCompositionRnd(text), booking_type_caption(text), weaveTypeRnd(text), fabricsFinishTypeRnd(text), noteForRnd(textarea), dispo_is_analysis_complete(text), analysis_complete_by(text), analysis_complete_date(text), dispo_is_rnd_complete(text), rnd_complete_by(text), rnd_complete_date(text), planing_complete(text), planing_complete_by(text), planing_complete_date(text), noteForPlaning(textarea)
- **Grid columns:** SL, DISPO NO, BK NO, BK Date, Marketing person, Buyer, Booking type, Item Name, Composition(Sales), Construction(Sales), Analysis complete, RND complete, Planing complete, Action

## documentApproval — Document Approval

- **Endpoints:** /documentApproval/edit, /documentApproval/index, /documentApproval/remove, /documentApproval/write
- **Master-detail:** YES | fields: 42 | events: 10 | functions: 26
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `documentApprovalReset()`
    - `onclick` on `documentApprovalResetBtn` → `documentApprovalSearch()`
    - `onclick` on `?` → `documentApprovalbtnOne()`
    - `onclick` on `?` → `documentApprovalButtonOne()`
    - `onclick` on `?` → `documentApprovalButtonTwo()`
    - `onclick` on `?` → `deteleItem(2)`
    - `onclick` on `?` → `deteleItem(`
- **JS functions:** `bookingRevisionShow`, `bookingShow`, `createCiViewTable`, `deliveryOrderShow`, `documentApprovalApproval`, `documentApprovalButtonOne`, `documentApprovalButtonTwo`, `documentApprovalPopulate`, `documentApprovalReport`, `documentApprovalReset`, `documentApprovalResetMasterForm`, `documentApprovalSearch`, `documentApprovalShow`, `documentApprovalbtnOne`, `expCiRcvShow`, `expLcRcvShow`, `expPiRcvShow`, `hs_dlo_dtlSetViewTableEvent`, `hs_po_dtlSetViewTableEvent`, `hs_po_rsn_dtlSetViewTableEvent`, `hs_so_dtlSetViewTableEvent`, `hs_so_rsn_dtlSetViewTableEvent`, `productionRevisionShow`, `productionShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), approveArchiveStatus(select), approveStatus(select), documentUrl(select), document_code(text), menu_caption(text), reference_code(text), date_created(text), waiting_status(text), waiting_by_captions(text), approval_status(text), approval_history(text), remarks(textarea)
- **Grid columns:** SL, Approval No, Document Name, Team, BK/PRO/DO Number, Submission date, Created By, Waiting For, Approval Users, Approval Status, Action, SL, Item, Construction, Composition, Weave Type, Weave Styles, Finished Width (Inch), Cuttable Width (Inch), Color breakdown

## dyeingReceive — Finish Goods Received

- **Endpoints:** /dyeingReceive/edit, /dyeingReceive/index, /dyeingReceive/remove, /dyeingReceive/write
- **Master-detail:** YES | fields: 28 | events: 7 | functions: 25
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `create_receive_dtlSet_dtlLine_receiveQty` → `receive_dtlSet_dtlLine_receiveQtyOnChangeEvent(this)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_receive_dtlSet_dtlLineResetEvent()`
    - `onclick` on `receive_dtlSet_dtlLineResetBtn` → `receive_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `hs_receive_dtlSetAddEvent`, `hs_receive_dtlSetDeleteEvent`, `hs_receive_dtlSetEditEvent`, `hs_receive_dtlSetViewTableEvent`, `hs_receive_dtlSet_dtlLineDeleteEvent`, `hs_receive_dtlSet_dtlLineEditEvent`, `hs_receive_dtlSet_dtlLineResetEvent`, `hs_receive_dtlSet_dtlLineViewTableEvent`, `receiveApprove`, `receiveChangeStatus`, `receiveConfirm`, `receiveDelete`, `receiveEdit`, `receiveReport`, `receiveReset`, `receiveShow`, `receive_dtlSetCreateUpdateEvent`, `receive_dtlSet_dtlLineCrUpEvent`, `receive_dtlSet_dtlLine_receiveQtyOnChangeEvent`, `reset_receive_dtlSet_form`, `woPopulate`, `woReceive`, `woReport`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), woCode(text), salesOrderCode(text), productionOrderCode(text), transactionDate(text), transactionDateWO(text), transactionDatePO(text), transactionDateSO(text), marketingPerson(text), stakeholder(text), remarks(textarea), colorCode(text), colorName(text), receiveQty(number), remarks(textarea)
- **Grid columns:** SL, Receiving NO, Receiving Date, Customer, WO NO, WO Date, BPO NO, BPO Date, Dispo NO, WO QTY, Receive QTY, Total RCV, Marketing Person, Status, Action, SL, Item, Construction, Composition, Weave Type

## dyeingWoByPro — Processing WO against BPO

- **Endpoints:** /dyeingWoByPro/edit, /dyeingWoByPro/index, /dyeingWoByPro/remove, /dyeingWoByPro/write
- **Master-detail:** YES | fields: 29 | events: 7 | functions: 25
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `create_wo_dtlSet_dtlLine_transactionQty` → `wo_dtlSet_dtlLine_transactionQtyOnChangeEvent(this)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_wo_dtlSet_dtlLineResetEvent()`
    - `onclick` on `wo_dtlSet_dtlLineResetBtn` → `wo_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `hs_wo_dtlSetAddEvent`, `hs_wo_dtlSetDeleteEvent`, `hs_wo_dtlSetEditEvent`, `hs_wo_dtlSetViewTableEvent`, `hs_wo_dtlSet_dtlLineDeleteEvent`, `hs_wo_dtlSet_dtlLineEditEvent`, `hs_wo_dtlSet_dtlLineResetEvent`, `hs_wo_dtlSet_dtlLineViewTableEvent`, `proPopulate`, `proReceive`, `proReport`, `reset_wo_dtlSet_form`, `woApprove`, `woChangeStatus`, `woConfirm`, `woDelete`, `woEdit`, `woReport`, `woReset`, `woShow`, `wo_dtlSetCreateUpdateEvent`, `wo_dtlSet_dtlLineCrUpEvent`, `wo_dtlSet_dtlLine_transactionQtyOnChangeEvent`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), salesOrderCode(text), productionOrderCode(text), dispo_number(text), transactionDatePRO(text), transactionDate(text), stakeholder(text), transactionDateSO(text), marketingPerson(text), remarks(textarea), colorCode(text), colorName(text), transactionQty(number), remarks(textarea)
- **Grid columns:** SL, WO Code, WO Date, BPO NO, BPO Date, Dispo NO, BPO QTY, WO QTY, Buyer, WO Status, Action, SL, Item, Construction, Composition, Weave Type, Finished Width, Cuttable Width, Color breakdown, Action

## dyesChemical — Dyes Chemical Item

- **Endpoints:** /dyesChemical/edit, /dyesChemical/index, /dyesChemical/remove, /dyesChemical/write
- **Master-detail:** no | fields: 16 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `itemCreateModalClick();`
    - `onclick` on `?` → `itemReset()`
- **JS functions:** `itemApprove`, `itemChangeStatus`, `itemClose`, `itemConfirm`, `itemCreateModalClick`, `itemDelete`, `itemEdit`, `itemOpen`, `itemPopulate`, `itemReport`, `itemReset`, `itemResetMasterForm`, `itemScriptInit`, `itemShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), genericName(select), remarks(textarea), itemCategory(select), operationUnit(select), purchaseUnit(select), purchaseToOpUnitCon(number), formationState(select)
- **Grid columns:** SL, Code, Name, Generic, Sub Sub Category, Operation Unit, Purchase Unit, P-OP Conversion, Formation State, Action

## employee — Employee

- **Endpoints:** /employee/edit, /employee/index, /employee/remove, /employee/write
- **Master-detail:** no | fields: 22 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `employeeCreateModalClick();`
    - `onclick` on `?` → `employeeReset()`
- **JS functions:** `employeeApprove`, `employeeChangeStatus`, `employeeClose`, `employeeConfirm`, `employeeCreateModalClick`, `employeeDelete`, `employeeEdit`, `employeeOpen`, `employeePopulate`, `employeeReport`, `employeeReset`, `employeeResetMasterForm`, `employeeScriptInit`, `employeeShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), contactNo(text), phoneNo(text), emailAddress(text), designation(select), department(select), imagePath(file), contactPerson(text), contactPersonMobile(text), accountName(text), accountNumber(text), address(textarea), permanentAddress(textarea), signature(file)
- **Grid columns:** SL, Code, Name, Designation, Department, Contact NO, Telephone NO, Email Address, Contact Person, Contact Persons Mobile, Action

## employeeShift — Employee Shift

- **Endpoints:** /employeeShift/edit, /employeeShift/index, /employeeShift/remove, /employeeShift/write
- **Master-detail:** no | fields: 9 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `shiftCreateModalClick();`
    - `onclick` on `?` → `shiftReset()`
- **JS functions:** `shiftApprove`, `shiftChangeStatus`, `shiftClose`, `shiftConfirm`, `shiftCreateModalClick`, `shiftDelete`, `shiftEdit`, `shiftOpen`, `shiftPopulate`, `shiftReport`, `shiftReset`, `shiftResetMasterForm`, `shiftScriptInit`, `shiftShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text)
- **Grid columns:** SL, Code, Name, Action

## expCiReceive — CI Issuing

- **Endpoints:** /expCiReceive/edit, /expCiReceive/index, /expCiReceive/remove, /expCiReceive/write
- **Master-detail:** no | fields: 25 | events: 3 | functions: 13
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **JS functions:** `advanceCiShow`, `changeCiStatus`, `changeCiStatusAmount`, `confirmCiStatus`, `createIbcView`, `expCiConfirm`, `expCiShow`, `expPiShow`, `handleAdvncPiQtyChange`, `renderCertificateTable`, `showCertificatesInTable`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), ciDate(text), totalAmount(number), expLcReceive(select), ciType(select), netWeight(number), grossWeight(number), remark(textarea), statusList(select), ciDate(text), costHeadList(select), ciCostAmount(number), costingDate(text), ibcNo(text)
- **Grid columns:** SL, CI No, LC No, Master LC No, B2B LC No, BPO NO, DISPO, PI NO, Garments Name, Status, Approve Status, Action, SL, CI No, LC NO, Action, SL, Chalan NO, PO NO, Date

## expCiReport — CI Report

- **Endpoints:** /expCiReport/index
- **Master-detail:** no | fields: 16 | events: 6 | functions: 14
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `expPiReset()`
    - `onclick` on `expLcResetBtn` → `expPiSearch()`
    - `onclick` on `?` → `showListReport()`
- **JS functions:** `createCiViewTable`, `expCiRcvShow`, `expCiReportBecificiary`, `expCiReportBill`, `expCiReportCertificateOfOrifin`, `expCiReportChallan`, `expCiReportCommercialInvoice`, `expCiReportPackingList`, `expCiReportTruckReceipt`, `expCiShow`, `expPiReset`, `expPiSearch`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), ciDate(text), ciEndDate(text), piNo(text), bpo(text), diospo(text), masterLcNo(text), btbLcNo(text), stakeholder(select), marketingPerson(select)
- **Grid columns:** SL, CI No, LC No, Master LC No, B2B No, BPO NO, DISPO, PI NO, Garments Name, Marketing Team, Marketing Person, Status, Approve Status, Action, SL, Title, Date, Action, SL, Title

## expLcReceive — LC Receiving

- **Endpoints:** /expLcReceive/addCost, /expLcReceive/edit, /expLcReceive/index, /expLcReceive/remove, /expLcReceive/write
- **Master-detail:** no | fields: 55 | events: 9 | functions: 6
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `submitLcUd()`
    - `onclick` on `?` → `submitLcUp()`
    - `onclick` on `?` → `submitRawMaterial()`
    - `onclick` on `?` → `submitLcCost()`
    - `onclick` on `document` → `docFilePathFileDownloadEvent()`
    - `onclick` on `?` → `changeStatusLC(`
- **JS functions:** `docFilePathFileDownloadEvent`, `expLcConfirm`, `expLcShow`, `getDiff`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), lcStatus(select), lcDate(text), shipmentDate(text), lcExpireDate(text), masterLcNo(text), masterLcDate(text), btbLcNo(text), lcIssueDate(text), lcValue(number), tenure(select), paymentTerms(select), deliveryTerms(select), beneficiaryBank(select), beneficiaryBnkAcc(select), partialShipment(checkbox), btmaCertificate(checkbox), stakeholder(select), buyerBank(select), buyerBankAcc(select), foreignBankName(text), foreignBankBin(text), foreignBankSwiftCode(text), foreignBankRoutingNo(text), piList(select), docFilePath(file), reqDocList(select), udRcvDate(text), udNumber(text), udAmount(number), upIssueDate(text), upNumber(text), upAmount(number), supplier(select), rawMaterialTypeList(select), rawMtrlBtBLc(text), rawMaterialDate(text), rawMaterialAmount(number), incentiveAmount(number), incentiveApplyDate(text), incentiveConfirmedDate(text), costHeadList(select), lcCostAmount(number), costingDate(text)
- **Grid columns:** SL, LC No, Mater Lc No, B2B Lc No, LC value, Date, LC Status, PI NO, DISPO NO, BPO NO, Garments Name, PI Status, Action, SL, PI NO, PI Value, PI Version, PI Date, Action, SL

## expLcReceiveRevision — LC Revision

- **Endpoints:** /expLcReceiveRevision/addCost, /expLcReceiveRevision/edit, /expLcReceiveRevision/index, /expLcReceiveRevision/remove, /expLcReceiveRevision/write
- **Master-detail:** no | fields: 55 | events: 8 | functions: 5
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `submitLcUd()`
    - `onclick` on `?` → `submitLcUp()`
    - `onclick` on `?` → `submitRawMaterial()`
    - `onclick` on `?` → `submitLcCost()`
    - `onclick` on `document` → `docFilePathFileDownloadEvent()`
- **JS functions:** `docFilePathFileDownloadEvent`, `expLcConfirm`, `expLcShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), lcStatus(select), lcDate(text), shipmentDate(text), lcExpireDate(text), masterLcNo(text), masterLcDate(text), btbLcNo(text), lcIssueDate(text), lcValue(number), tenure(select), paymentTerms(select), deliveryTerms(select), beneficiaryBank(select), beneficiaryBnkAcc(select), partialShipment(checkbox), btmaCertificate(checkbox), stakeholder(select), buyerBank(select), buyerBankAcc(select), foreignBankName(text), foreignBankBin(text), foreignBankSwiftCode(text), foreignBankRoutingNo(text), piList(select), docFilePath(file), reqDocList(select), udRcvDate(text), udNumber(text), udAmount(number), upIssueDate(text), upNumber(text), upAmount(number), supplier(select), rawMaterialTypeList(select), rawMtrlBtBLc(text), rawMaterialDate(text), rawMaterialAmount(number), incentiveAmount(number), incentiveApplyDate(text), incentiveConfirmedDate(text), costHeadList(select), lcCostAmount(number), costingDate(text)
- **Grid columns:** SL, LC No, Mater Lc No, B2B Lc No, LC value, Date, LC Status, PI NO, DISPO NO, BPO NO, Garments Name, PI Status, Action, SL, PI NO, PI Value, PI Version, PI Date, Action, SL

## expLcReport — LC Report

- **Endpoints:** /expLcReport/index
- **Master-detail:** no | fields: 23 | events: 8 | functions: 5
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `expLcReset()`
    - `onclick` on `expLcResetBtn` → `expLcSearch()`
    - `onclick` on `?` → `showListReport()`
    - `onclick` on `?` → `deteleItem(2)`
    - `onclick` on `?` → `deteleItem(`
- **JS functions:** `expLcRcvShow`, `expLcReset`, `expLcSearch`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), masterLcNo(text), btbLcNo(text), lcValue(number), lcDate(text), lcEntDate(text), piNo(text), bpo(text), diospo(text), stakeholder(select), marketingPerson(select)
- **Grid columns:** SL, LC No, Master LC No, B2B LC No, LC value, Date, LC Status, PI NO, DISPO NO, BPO NO, Garments Name, PI Status, Marketing Team, Marketing Person, Action, SL, PI NO, PI Value, PI Version, PI Date

## expPiReceive — PI Issuing

- **Endpoints:** /expPiReceive/edit, /expPiReceive/index, /expPiReceive/remove, /expPiReceive/termsAndConditionsList, /expPiReceive/write
- **Master-detail:** no | fields: 33 | events: 3 | functions: 5
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **JS functions:** `expPiConfirm`, `expPiReport`, `expPiShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), piDate(text), piExpireDate(text), totalAmount(number), stakeholder(select), customerBrand(select), bank(select), bnkAccount(select), currency(text), netWeight(number), grossWeight(number), piNetWeight(number), piGrossWeight(number), hsCode(select), tenure(select), marketingPerson(select), paymentTerms(select), deliveryTerms(select), applicantBondLincs(text), remark(textarea), scheduleList(select), docFilePath(file)
- **Grid columns:** SL, PI No, PI Date, BPO NO, DISPO NO, GDS NO, Garment Name, PI QTY, PI Value, PI Status, BPO Status, GDS Status, Action, Sl, Details, SL, BPO NO, Details, Sl, Details

## expPiReceiveRevision — PI Revision

- **Endpoints:** /expPiReceiveRevision/edit, /expPiReceiveRevision/index, /expPiReceiveRevision/remove, /expPiReceiveRevision/termsAndConditionsList, /expPiReceiveRevision/write
- **Master-detail:** no | fields: 33 | events: 3 | functions: 5
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **JS functions:** `expPiConfirm`, `expPiReport`, `expPiShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), piDate(text), piExpireDate(text), totalAmount(number), stakeholder(select), customerBrand(select), bank(select), bnkAccount(select), currency(text), netWeight(number), grossWeight(number), piNetWeight(number), piGrossWeight(number), hsCode(select), tenure(select), marketingPerson(select), paymentTerms(select), deliveryTerms(select), applicantBondLincs(text), remark(textarea), scheduleList(select), docFilePath(file)
- **Grid columns:** SL, PI No, PI Date, BPO NO, DISPO NO, GDS NO, Garment Name, PI QTY, PI Value, PI Status, BPO Status, GDS Status, Action, Sl, Details, SL, BPO NO, Details, Sl, Details

## expPiReport — PI Report

- **Endpoints:** /expPiReport/index
- **Master-detail:** no | fields: 15 | events: 6 | functions: 6
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `expPiReset()`
    - `onclick` on `expLcResetBtn` → `expPiSearch()`
    - `onclick` on `?` → `showListReport()`
- **JS functions:** `expPiRcvShow`, `expPiReport`, `expPiReset`, `expPiSearch`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), piDate(text), piEndDate(text), bpo(text), dispo(text), gds(text), marketingPerson(select), stakeholder(select), customerBrand(select)
- **Grid columns:** SL, PI No, PI Date, BPO NO, DISPO NO, GDS NO, Garment Name, PI Value, PI Status, BPO Status, GDS Status, Marketing Team, Marketing Person, Action, Sl, Details, Sl, Production Order, Delivery Schedule, composition

## exportPi — PI Receiving

- **Endpoints:** /exportPi/edit, /exportPi/index, /exportPi/remove, /exportPi/write
- **Master-detail:** YES | fields: 25 | events: 6 | functions: 24
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_pi_dtlSet_dtlLineResetEvent()`
    - `onclick` on `pi_dtlSet_dtlLineResetBtn` → `pi_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `hs_pi_dtlSetAddEvent`, `hs_pi_dtlSetDeleteEvent`, `hs_pi_dtlSetEditEvent`, `hs_pi_dtlSetViewTableEvent`, `hs_pi_dtlSet_dtlLineDeleteEvent`, `hs_pi_dtlSet_dtlLineEditEvent`, `hs_pi_dtlSet_dtlLineResetEvent`, `hs_pi_dtlSet_dtlLineViewTableEvent`, `piApprove`, `piChangeStatus`, `piConfirm`, `piDelete`, `piEdit`, `piReport`, `piReset`, `piShow`, `pi_dtlSetCreateUpdateEvent`, `pi_dtlSet_dtlLineCrUpEvent`, `reset_pi_dtlSet_form`, `soPopulate`, `soReceive`, `soReport`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), totalAmount(number), piDate(text), piExpireDate(text), stakeholder(select), mainBuyer(text), remarks(textarea), colorCode(text), colorName(text), transactionQty(number), unitPriceCurrency(number), filePath(text), remarks(textarea)
- **Grid columns:** SL, Proforma invoice Code, Customer / Buyer, Business Unit, Proforma invoice Date, Currency, Total PI Amount, Remarks, Action, SL, Item, Construction, Fabrics Finish Type, Light Source, Wash Type, End Use, Color breakdown, Action, SL, Item

## fabricsComposition — Fabrics Composition

- **Endpoints:** /fabricsComposition/edit, /fabricsComposition/index, /fabricsComposition/remove, /fabricsComposition/write
- **Master-detail:** no | fields: 9 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `compositionCreateModalClick();`
    - `onclick` on `?` → `compositionReset()`
- **JS functions:** `compositionApprove`, `compositionChangeStatus`, `compositionClose`, `compositionConfirm`, `compositionCreateModalClick`, `compositionDelete`, `compositionEdit`, `compositionOpen`, `compositionPopulate`, `compositionReport`, `compositionReset`, `compositionResetMasterForm`, `compositionScriptInit`, `compositionShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text)
- **Grid columns:** SL, Code, Name, Action

## fabricsDelivery — Fabrics Delivery

- **Endpoints:** /fabricsDelivery/edit, /fabricsDelivery/index, /fabricsDelivery/remove, /fabricsDelivery/write
- **Master-detail:** YES | fields: 27 | events: 7 | functions: 26
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `create_issue_dtlSet_dtlLine_issueQty` → `issue_dtlSet_dtlLine_issueQtyOnChangeEvent(this)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_issue_dtlSet_dtlLineResetEvent()`
    - `onclick` on `issue_dtlSet_dtlLineResetBtn` → `issue_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `dloPopulate`, `dloReceive`, `dloReport`, `handlePrint`, `hs_issue_dtlSetAddEvent`, `hs_issue_dtlSetDeleteEvent`, `hs_issue_dtlSetEditEvent`, `hs_issue_dtlSetViewTableEvent`, `hs_issue_dtlSet_dtlLineDeleteEvent`, `hs_issue_dtlSet_dtlLineEditEvent`, `hs_issue_dtlSet_dtlLineResetEvent`, `hs_issue_dtlSet_dtlLineViewTableEvent`, `issueApprove`, `issueChangeStatus`, `issueConfirm`, `issueDelete`, `issueEdit`, `issueReport`, `issueReset`, `issueShow`, `issue_dtlSetCreateUpdateEvent`, `issue_dtlSet_dtlLineCrUpEvent`, `issue_dtlSet_dtlLine_issueQtyOnChangeEvent`, `reset_issue_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), doCode(text), shippingAddress(textarea), transactionDate(text), transactionDateDO(text), customerBrand(text), customerGarments(text), stakeholder(text), remarks(textarea), colorCode(text), colorName(text), issueQty(number), numberOfRolls(number), remarks(textarea)
- **Grid columns:** SL, Delivery NO, Issuing Order Date, DO No, Delivery Date, BPO No, BPO Date, Fabric Type, Shipping Address, Brand, Garments, DO Qty, Issue Qty, Total Delivery, Delivery Status, Action, SL, Item, Construction, Composition

## fabricsDeliveryReport — Fabrics Delivery Report

- **Endpoints:** /fabricsDeliveryReport/edit, /fabricsDeliveryReport/index, /fabricsDeliveryReport/remove, /fabricsDeliveryReport/write
- **Master-detail:** no | fields: 8 | events: 6 | functions: 8
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `issueReset()`
    - `onclick` on `issueResetBtn` → `issueSearch()`
    - `onclick` on `?` → `issuebtnOne()`
- **JS functions:** `handlePrint`, `issueReport`, `issueReset`, `issueSearch`, `issueShow`, `issuebtnOne`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), currency(select), approveStatus(select)
- **Grid columns:** SL, Transaction No, Transaction date, WO/DO No, WO/DO Type, WO/DO Date, BPO No, BPO date, Booking No, Booking Date, Status, Action

## fabricsDirectMrr — Finished Fabric Receiving

- **Endpoints:** /fabricsDirectMrr/edit, /fabricsDirectMrr/index, /fabricsDirectMrr/remove, /fabricsDirectMrr/write
- **Master-detail:** YES | fields: 25 | events: 6 | functions: 24
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_receive_dtlSet_dtlLineResetEvent()`
    - `onclick` on `receive_dtlSet_dtlLineResetBtn` → `receive_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `hs_receive_dtlSetAddEvent`, `hs_receive_dtlSetDeleteEvent`, `hs_receive_dtlSetEditEvent`, `hs_receive_dtlSetViewTableEvent`, `hs_receive_dtlSet_dtlLineDeleteEvent`, `hs_receive_dtlSet_dtlLineEditEvent`, `hs_receive_dtlSet_dtlLineResetEvent`, `hs_receive_dtlSet_dtlLineViewTableEvent`, `rcvPopulate`, `rcvReceive`, `rcvReport`, `receiveApprove`, `receiveChangeStatus`, `receiveConfirm`, `receiveDelete`, `receiveEdit`, `receiveReport`, `receiveReset`, `receiveShow`, `receive_dtlSetCreateUpdateEvent`, `receive_dtlSet_dtlLineCrUpEvent`, `reset_receive_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), proCode(text), salesOrderCode(text), transactionDate(text), transactionDateSO(text), marketingPerson(text), stakeholder(text), remarks(textarea), colorCode(text), colorName(text), receiveQty(number), filePath(text), remarks(textarea)
- **Grid columns:** SL, Receiving No, Receiving Date, BPO Code, BPO Order Date, Booking No, Booking Date, Customer, BPO Qty, Receive Qty, Receive Code, Total Receive Qty, Status, Action, SL, Item, Construction, Composition, Weave Type, Finished Width

## fabricsProductionReport — Fabrics Production Report

- **Endpoints:** /fabricsProductionReport/edit, /fabricsProductionReport/index, /fabricsProductionReport/remove, /fabricsProductionReport/write
- **Master-detail:** YES | fields: 39 | events: 6 | functions: 20
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `fprCreateModalClick();`
    - `onclick` on `fpr_dtlSetAddBtn` → `fpr_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `fprReset()`
- **JS functions:** `fprApprove`, `fprChangeStatus`, `fprClose`, `fprConfirm`, `fprCreateModalClick`, `fprDelete`, `fprEdit`, `fprOpen`, `fprPopulate`, `fprReport`, `fprReset`, `fprResetMasterForm`, `fprScriptInit`, `fprShow`, `fpr_dtlSetCreateUpdateEvent`, `hs_fpr_dtlSetDeleteEvent`, `hs_fpr_dtlSetEditEvent`, `reset_fpr_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), transaction_date(text), so_code(text), transaction_qty(text), so_transaction_date(text), stakeholder_caption(text), customer_garments_caption(text), dyeing_rcv_code(text), wo_textile_code(text), wo_dyeing_code(text), textile_rcv_code(text), textile_issue_code(text)
- **Grid columns:** SL, BPO NO, BPO date, BPO QTY, Customer, Garments, Dispo NO, Weaving WO NO, Processing WO NO, Greige Fabrics Received, Greige Issue for Processing, Finish Goods Received, Action, SL, Composition, Color code, Color name, BPO QTY, Style, Textile WO

## fabricsTransfer — Error

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## fabricsTransferIssue — Transfer Issue

- **Endpoints:** /fabricsTransferIssue/edit, /fabricsTransferIssue/index, /fabricsTransferIssue/remove, /fabricsTransferIssue/write
- **Master-detail:** YES | fields: 23 | events: 6 | functions: 24
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_issue_dtlSet_dtlLineResetEvent()`
    - `onclick` on `issue_dtlSet_dtlLineResetBtn` → `issue_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `hs_issue_dtlSetAddEvent`, `hs_issue_dtlSetDeleteEvent`, `hs_issue_dtlSetEditEvent`, `hs_issue_dtlSetViewTableEvent`, `hs_issue_dtlSet_dtlLineDeleteEvent`, `hs_issue_dtlSet_dtlLineEditEvent`, `hs_issue_dtlSet_dtlLineResetEvent`, `hs_issue_dtlSet_dtlLineViewTableEvent`, `issueApprove`, `issueChangeStatus`, `issueConfirm`, `issueDelete`, `issueEdit`, `issueReport`, `issueReset`, `issueShow`, `issue_dtlSetCreateUpdateEvent`, `issue_dtlSet_dtlLineCrUpEvent`, `reset_issue_dtlSet_form`, `transferPopulate`, `transferReceive`, `transferReport`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), transferCode(text), transactionDate(text), fromStore(text), toStore(text), transactionDateTransfer(text), remarks(textarea), colorCode(text), colorName(text), transactionQty(number), remarks(textarea)
- **Grid columns:** SL, Issuing No, Transfer Code, Issuing Date, Transfer Date, From Store, TO Store, Remarks, Action, SL, Item, Construction, Composition, Weave Type, Finished Width, Cuttable Width, Color breakdown, Action, SL, Item

## fabricsTransferReceive — Transfer Receive

- **Endpoints:** /fabricsTransferReceive/edit, /fabricsTransferReceive/index, /fabricsTransferReceive/remove, /fabricsTransferReceive/write
- **Master-detail:** YES | fields: 23 | events: 6 | functions: 24
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_receive_dtlSet_dtlLineResetEvent()`
    - `onclick` on `receive_dtlSet_dtlLineResetBtn` → `receive_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `hs_receive_dtlSetAddEvent`, `hs_receive_dtlSetDeleteEvent`, `hs_receive_dtlSetEditEvent`, `hs_receive_dtlSetViewTableEvent`, `hs_receive_dtlSet_dtlLineDeleteEvent`, `hs_receive_dtlSet_dtlLineEditEvent`, `hs_receive_dtlSet_dtlLineResetEvent`, `hs_receive_dtlSet_dtlLineViewTableEvent`, `receiveApprove`, `receiveChangeStatus`, `receiveConfirm`, `receiveDelete`, `receiveEdit`, `receiveReport`, `receiveReset`, `receiveShow`, `receive_dtlSetCreateUpdateEvent`, `receive_dtlSet_dtlLineCrUpEvent`, `reset_receive_dtlSet_form`, `transferPopulate`, `transferReceive`, `transferReport`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), transferCode(text), transactionDate(text), fromStore(text), toStore(text), transactionDateTransfer(text), remarks(textarea), colorCode(text), colorName(text), transactionQty(number), remarks(textarea)
- **Grid columns:** SL, Issuing Number, Transfer Code, Issuing Date, Transfer Date, From Store, TO Store, Remarks, Action, SL, Item, Construction, Composition, Weave Type, Finished Width, Cuttable Width, Color breakdown, Action, SL, Item

## fiberItem — Fiber Item

- **Endpoints:** /fiberItem/edit, /fiberItem/index, /fiberItem/remove, /fiberItem/write
- **Master-detail:** no | fields: 16 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `itemCreateModalClick();`
    - `onclick` on `?` → `itemReset()`
- **JS functions:** `itemApprove`, `itemChangeStatus`, `itemClose`, `itemConfirm`, `itemCreateModalClick`, `itemDelete`, `itemEdit`, `itemOpen`, `itemPopulate`, `itemReport`, `itemReset`, `itemResetMasterForm`, `itemScriptInit`, `itemShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), hsCode(select), countries(select), remarks(textarea), itemCategory(select), operationUnit(select), purchaseUnit(select), purchaseToOpUnitCon(number)
- **Grid columns:** SL, Code, Name, Sub Sub Category, Operation Unit, Purchase Unit, P-OP Conversion, Action

## finishedFabrics — Fabrics

- **Endpoints:** /finishedFabrics/edit, /finishedFabrics/index, /finishedFabrics/remove, /finishedFabrics/write
- **Master-detail:** no | fields: 16 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `itemCreateModalClick();`
    - `onclick` on `?` → `itemReset()`
- **JS functions:** `itemApprove`, `itemChangeStatus`, `itemClose`, `itemConfirm`, `itemCreateModalClick`, `itemDelete`, `itemEdit`, `itemOpen`, `itemPopulate`, `itemReport`, `itemReset`, `itemResetMasterForm`, `itemScriptInit`, `itemShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), itemCategory(select), remarks(textarea), operationUnit(select), purchaseUnit(select), purchaseToOpUnitCon(number), salesUnit(select), salesToOpUnitCon(number)
- **Grid columns:** SL, Code, Name, Sub Sub Category, Operation Unit, Purchase Unit, P-OP Conversion, Sales Unit, S-OP Conversion, Action

## genericName — Generic Name

- **Endpoints:** /genericName/edit, /genericName/index, /genericName/remove, /genericName/write
- **Master-detail:** no | fields: 9 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `genericNameCreateModalClick();`
    - `onclick` on `?` → `genericNameReset()`
- **JS functions:** `genericNameApprove`, `genericNameChangeStatus`, `genericNameClose`, `genericNameConfirm`, `genericNameCreateModalClick`, `genericNameDelete`, `genericNameEdit`, `genericNameOpen`, `genericNamePopulate`, `genericNameReport`, `genericNameReset`, `genericNameResetMasterForm`, `genericNameScriptInit`, `genericNameShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text)
- **Grid columns:** SL, Code, Name, Action

## globalArea — Error

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## globalCity — Error

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## globalCountries — Error

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## globalCurrency — Error

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## globalRegion — Error

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## goodsDelivery — Goods Delivery

- **Endpoints:** /goodsDelivery/edit, /goodsDelivery/index, /goodsDelivery/remove, /goodsDelivery/write
- **Master-detail:** YES | fields: 42 | events: 7 | functions: 38
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `gdv_dtlSetAddBtn` → `gdv_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `gdvReset()`
    - `onclick` on `so_dtlSetAddBtn` → `so_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `soReset()`
- **JS functions:** `gdvApprove`, `gdvChangeStatus`, `gdvClose`, `gdvConfirm`, `gdvCreateModalClick`, `gdvDelete`, `gdvEdit`, `gdvOpen`, `gdvPopulate`, `gdvReport`, `gdvReset`, `gdvResetMasterForm`, `gdvScriptInit`, `gdvShow`, `gdv_dtlSetCreateUpdateEvent`, `hs_gdv_dtlSetDeleteEvent`, `hs_gdv_dtlSetEditEvent`, `hs_so_dtlSetDeleteEvent`, `hs_so_dtlSetEditEvent`, `reset_gdv_dtlSet_form`, `reset_so_dtlSet_form`, `soApprove`, `soChangeStatus`, `soClose`, `soConfirm`, `soCreateModalClick`, `soDelete`, `soEdit`, `soOpen`, `soPopulate`, `soReport`, `soReset`, `soResetMasterForm`, `soScriptInit`, `soShow`, `so_dtlSetCreateUpdateEvent`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), stakeholderDummy(text), transactionDate(text), inventoryStore(select), salesOrderDummy(text), remarks(textarea), codeSO(text), businessUnitSO(text), stakeholderSO(text), inventoryStore(select), transactionDateSO(text), transactionDate(text), remarks(textarea)
- **Grid columns:** SL, Code, Purchase order, Period, Customer, Item Store, Action, SL, Item, BK Qty, Stock Qty, Previous Issue, Issue Qty, Specification, Condition, Action, SL, Item, BK Qty, Stock Qty

## goodsReceiptNote — MRR

- **Endpoints:** /goodsReceiptNote/edit, /goodsReceiptNote/index, /goodsReceiptNote/remove, /goodsReceiptNote/write
- **Master-detail:** YES | fields: 77 | events: 7 | functions: 38
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `grm_dtlSetAddBtn` → `grm_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `grmReset()`
    - `onclick` on `po_dtlSetAddBtn` → `po_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `poReset()`
- **JS functions:** `grmApprove`, `grmChangeStatus`, `grmClose`, `grmConfirm`, `grmCreateModalClick`, `grmDelete`, `grmEdit`, `grmOpen`, `grmPopulate`, `grmReport`, `grmReset`, `grmResetMasterForm`, `grmScriptInit`, `grmShow`, `grm_dtlSetCreateUpdateEvent`, `hs_grm_dtlSetDeleteEvent`, `hs_grm_dtlSetEditEvent`, `hs_po_dtlSetDeleteEvent`, `hs_po_dtlSetEditEvent`, `poApprove`, `poChangeStatus`, `poClose`, `poConfirm`, `poCreateModalClick`, `poDelete`, `poEdit`, `poOpen`, `poPopulate`, `poReport`, `poReset`, `poResetMasterForm`, `poScriptInit`, `poShow`, `po_dtlSetCreateUpdateEvent`, `reset_grm_dtlSet_form`, `reset_po_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), stakeholderDummy(text), transactionDate(text), poTypePO(text), purchaseOrderDummy(text), purchaseOrderDateDummy(text), invoiceNo(text), physicalClnNo(text), vehicleNo(text), approve_status_caption(text), remarks(textarea), codePO(text), stakeholderPO(text), transactionDate(text), poTypePO(text), transactionDatePO(text), invoiceNo(text), physicalClnNo(text), vehicleNo(text), approve_status_caption(text), remarks(textarea)
- **Grid columns:** SL, Code, MRR Date, BPO No, BPO Date, Supplier, Approve status, Action, SL, Item, Country Of Origin, Color Name, Brand, Construction, Composition, Weave Type, Fabrics Finish Type, Fabrics Width (Inch), GSM, Fabrics TR

## hrmDashboard — Grails

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## hsCode — HS Code

- **Endpoints:** /hsCode/edit, /hsCode/index, /hsCode/remove, /hsCode/write
- **Master-detail:** no | fields: 9 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `hsCodeCreateModalClick();`
    - `onclick` on `?` → `hsCodeReset()`
- **JS functions:** `hsCodeApprove`, `hsCodeChangeStatus`, `hsCodeClose`, `hsCodeConfirm`, `hsCodeCreateModalClick`, `hsCodeDelete`, `hsCodeEdit`, `hsCodeOpen`, `hsCodePopulate`, `hsCodeReport`, `hsCodeReset`, `hsCodeResetMasterForm`, `hsCodeScriptInit`, `hsCodeShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text)
- **Grid columns:** SL, Code, Name, Action

## impLcIssue — LC Issuing (Import)

- **Endpoints:** /impLcIssue/edit, /impLcIssue/index, /impLcIssue/remove, /impLcIssue/write
- **Master-detail:** no | fields: 44 | events: 6 | functions: 11
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `billOfEntryFilePathFileDownloadEvent()`
    - `onclick` on `?` → `docFilePathFileDownloadEvent()`
    - `onclick` on `?` → `submitLcCost()`
- **JS functions:** `billOfEntryFilePathFileDownloadEvent`, `docFilePathFileDownloadEvent`, `getLcReceiveDetails`, `importLcConfirm`, `importLcEdit`, `importLcReport`, `importLcRevision`, `importLcShow`, `importPiShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), type(select), invoiceNo(text), lcType(select), tenure(select), lcNo(text), lcValue(number), lcDate(text), lcIssueDate(text), lcExpireDate(text), shipmentDate(text), cnfAgentCaption(text), ipNo(text), port(select), isSroBenefited(checkbox), btmaNo(text), btmaDate(text), buyerBank(select), buyerBankAcc(select), beneficiaryBank(text), beneficiaryBnkAcc(text), swiftCode(text), piList(select), docFilePath(file), billOfEntryDate(text), billOfEntryNo(text), billDocFilePath(file), docName(text), docFilePath(file), costHeadList(select), lcCostAmount(number), costingDate(text)
- **Grid columns:** SL, PI NO, PI Value, PI Version, PI Date, Action, SL, PI NO, PI Value, PI Version, PI Date, SL, Document, File Name, Download, SL, Title, Amount, Date, Action

## impLcRevision — LC Revision (Import)

- **Endpoints:** /impLcRevision/edit, /impLcRevision/index, /impLcRevision/remove, /impLcRevision/write
- **Master-detail:** no | fields: 44 | events: 6 | functions: 9
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `billOfEntryFilePathFileDownloadEvent()`
    - `onclick` on `?` → `docFilePathFileDownloadEvent()`
    - `onclick` on `?` → `submitLcCost()`
- **JS functions:** `billOfEntryFilePathFileDownloadEvent`, `docFilePathFileDownloadEvent`, `importLcConfirm`, `importLcEdit`, `importLcReport`, `importLcShow`, `importPiShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), type(select), invoiceNo(text), lcType(select), tenure(select), lcNo(text), lcValue(number), lcDate(text), lcIssueDate(text), lcExpireDate(text), shipmentDate(text), cnfAgentCaption(text), ipNo(text), port(select), isSroBenefited(checkbox), btmaNo(text), btmaDate(text), buyerBank(select), buyerBankAcc(select), beneficiaryBank(text), beneficiaryBnkAcc(text), swiftCode(text), piList(select), docFilePath(file), billOfEntryDate(text), billOfEntryNo(text), billDocFilePath(file), docName(text), docFilePath(file), costHeadList(select), lcCostAmount(number), costingDate(text)
- **Grid columns:** SL, PI NO, PI Value, PI Version, PI Date, Action, SL, PI NO, PI Value, PI Version, PI Date, SL, Document, File Name, Download, SL, Title, Amount, Date, Action

## importGRN — GRN (Import)

- **Endpoints:** /importGRN/edit, /importGRN/index, /importGRN/remove, /importGRN/write
- **Master-detail:** no | fields: 20 | events: 4 | functions: 5
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `submitLcCost()`
- **JS functions:** `importGRNConfirm`, `importGRNEdit`, `importGRNShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), poList(select), grnDate(text), billOfEntryDate(text), piNo(text), lcNo(text), supplier_caption(text), docName(text), docFilePath(file), costHeadList(select), grnCostAmount(number), costingDate(text)
- **Grid columns:** SL, Code, GRN Date, Bill entry Date, LC NO, PO NO, PI NO, Supplier Name, Status, Action, ID, Name, Quantity, Unit Price, Assess Value, SD (Input), CD (Input), Landed cost, Sl, Item Name

## importPi — Page Not Found

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## importPiReceive — PI Receive (Import)

- **Endpoints:** /importPiReceive/edit, /importPiReceive/index, /importPiReceive/remove, /importPiReceive/write
- **Master-detail:** no | fields: 27 | events: 11 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `mngntActions` → `submitApproval(`
    - `onclick` on `cedActions` → `submitApproval(`
    - `onclick` on `cfActions` → `submitApproval(`
    - `onclick` on `bondActions` → `submitApproval(`
    - `onclick` on `piActions` → `submitApproval(`
    - `onclick` on `lcDraftActions` → `submitApproval(`
    - `onclick` on `lcCorrectActions` → `submitApproval(`
    - `onclick` on `?` → `docFilePathFileDownloadEvent()`
- **JS functions:** `applyApprovalUI`, `docFilePathFileDownloadEvent`, `getPiReceiveDetails`, `importPiConfirm`, `importPiCreate`, `importPiCreateLc`, `importPiCreatePo`, `importPiEdit`, `importPiReport`, `importPiRevision`, `importPiShow`, `resetCategorySelection`, `submitApproval`, `toggleForms`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), piDate(text), piNo(text), totalAmount(number), supplier(select), localAgent(select), currency(select), remark(textarea), docFilePath(file), piType(radio), piType(radio), piRcv_category(select), piRcv_subCategory(select), piRcv_subSubCategory(select), piRcv_inventoryItemByCategory(select), sprList(select), docName(text), docFilePath(file)
- **Grid columns:** SL, PI Code, PI No, PI Date, Management Status, Cnf Status, Bond Status, Supplier, Total, Status, Action, #, Item Code, Item Name, Description, Quantity, Unit Price, Unit, Total Amount, Actions

## importPiRevision — PI Revision (Import)

- **Endpoints:** /importPiRevision/edit, /importPiRevision/index, /importPiRevision/remove, /importPiRevision/write
- **Master-detail:** no | fields: 27 | events: 11 | functions: 13
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `mngntActions` → `submitApproval(`
    - `onclick` on `cedActions` → `submitApproval(`
    - `onclick` on `cfActions` → `submitApproval(`
    - `onclick` on `bondActions` → `submitApproval(`
    - `onclick` on `piActions` → `submitApproval(`
    - `onclick` on `lcDraftActions` → `submitApproval(`
    - `onclick` on `lcCorrectActions` → `submitApproval(`
    - `onclick` on `?` → `docFilePathFileDownloadEvent()`
- **JS functions:** `applyApprovalUI`, `docFilePathFileDownloadEvent`, `importPiConfirm`, `importPiCreate`, `importPiCreateLc`, `importPiCreatePo`, `importPiEdit`, `importPiReport`, `importPiShow`, `submitApproval`, `toggleForms`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), piDate(text), piNo(text), totalAmount(number), supplier(select), localAgent(select), currency(select), remark(textarea), docFilePath(file), piType(radio), piType(radio), piRcv_category(select), piRcv_subCategory(select), piRcv_subSubCategory(select), piRcv_inventoryItemByCategory(select), sprList(select), docName(text), docFilePath(file)
- **Grid columns:** SL, PI Code, PI No, PI Date, Management Status, Cnf Status, Bond Status, Supplier, Total, Status, Action, #, Item Code, Item Name, Description, Quantity, Unit Price, Unit, Total Amount, Actions

## importPurchaseOrder — Purchase Order (Import)

- **Endpoints:** /importPurchaseOrder/edit, /importPurchaseOrder/index, /importPurchaseOrder/remove, /importPurchaseOrder/write
- **Master-detail:** no | fields: 9 | events: 4 | functions: 4
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `submitLcCost()`
- **JS functions:** `importPoShow`, `renderPiTable`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), costHeadList(select), grnCostAmount(number), costingDate(text)
- **Grid columns:** SL, PO No, PI Code, PI NO, PI Date, PO Amount, Supplier, Action, #, Caption, Qty, Unit Price, Item Total, Allocated Expense, Provisional Cost, Subtotal, 0.00, 0.00, 0.00, SL

## invDashboard — Stock & Delivery Dashboard

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 8 | events: 9 | functions: 18
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `periodFilter` → `loadDeliveryData()`
    - `onchange` on `storeFilter` → `loadStockData()`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `updateDeliveryTrend(`
    - `onclick` on `?` → `updateDeliveryTrend(`
    - `onclick` on `?` → `updateDeliveryTrend(`
    - `onclick` on `?` → `loadStockLevels()`
- **JS functions:** `formatCurrency`, `formatNumber`, `initDeliveryTrendChart`, `initStockByStoreChart`, `initTopItemsChart`, `loadDashboardData`, `loadDeliveryData`, `loadStockData`, `loadStockLevels`, `populateLowStockTable`, `populateMovementsTable`, `populateStockLevelsTable`, `populateStoreFilter`, `updateDeliveryKPIs`, `updateDeliveryTrend`, `updateStockKPI`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)
- **Grid columns:** Item, Store, Current Stock, Value, Transaction No, Date, Type, Mode, Store, Receive Qty, Issue Qty, Status, Item Code, Item Name, Dispo NO, Store, Received, Issued, Current Stock, Avg Price

## inventoryItem — General Item

- **Endpoints:** /inventoryItem/edit, /inventoryItem/index, /inventoryItem/remove, /inventoryItem/write
- **Master-detail:** no | fields: 21 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `itemCreateModalClick();`
    - `onclick` on `?` → `itemReset()`
- **JS functions:** `itemApprove`, `itemChangeStatus`, `itemClose`, `itemConfirm`, `itemCreateModalClick`, `itemDelete`, `itemEdit`, `itemOpen`, `itemPopulate`, `itemReport`, `itemReset`, `itemResetMasterForm`, `itemScriptInit`, `itemShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), operationUnit(select), purchaseUnit(select), purchaseToOpUnitCon(number), salesUnit(select), salesToOpUnitCon(number), itemCategoryGrp(select), itemCategoryCls(select), itemCategory(select), countries(select), itemModel(select), itemBrand(select), remarks(textarea)
- **Grid columns:** SL, Code, Name, Sub Sub Category, Operation Unit, Purchase Unit, P-OP Conversion, Sales Unit, S-OP Conversion, Countries, Item Model, Item Brand, Action

## inventoryPeriod — Period

- **Endpoints:** /inventoryPeriod/edit, /inventoryPeriod/index, /inventoryPeriod/remove, /inventoryPeriod/write
- **Master-detail:** no | fields: 8 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `periodCreateModalClick();`
    - `onclick` on `?` → `periodReset()`
- **JS functions:** `periodApprove`, `periodChangeStatus`, `periodClose`, `periodConfirm`, `periodCreateModalClick`, `periodDelete`, `periodEdit`, `periodOpen`, `periodPopulate`, `periodReport`, `periodReset`, `periodResetMasterForm`, `periodScriptInit`, `periodShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), periodMonth(select)
- **Grid columns:** SL, Code, Name, Start Date, Close Date, Primary Open, Open, Primary Close, Close, Open By, Close By, Action

## inventoryStore — Item Store

- **Endpoints:** /inventoryStore/edit, /inventoryStore/index, /inventoryStore/remove, /inventoryStore/write
- **Master-detail:** no | fields: 14 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `storeCreateModalClick();`
    - `onclick` on `?` → `storeReset()`
- **JS functions:** `storeApprove`, `storeChangeStatus`, `storeClose`, `storeConfirm`, `storeCreateModalClick`, `storeDelete`, `storeEdit`, `storeOpen`, `storePopulate`, `storeReport`, `storeReset`, `storeResetMasterForm`, `storeScriptInit`, `storeShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), contactNo(text), phoneNo(text), emailAddress(text), businessUnit(select), itemInputType(select)
- **Grid columns:** SL, Code, Name, Contact No, Phone NO, Email Address, Business Unit, Item Input Type, Action

## inventoryTransfer — Transfer Request

- **Endpoints:** /inventoryTransfer/edit, /inventoryTransfer/index, /inventoryTransfer/remove, /inventoryTransfer/write
- **Master-detail:** YES | fields: 19 | events: 6 | functions: 20
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `transferCreateModalClick();`
    - `onclick` on `transfer_dtlSetAddBtn` → `transfer_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `transferReset()`
- **JS functions:** `hs_transfer_dtlSetDeleteEvent`, `hs_transfer_dtlSetEditEvent`, `reset_transfer_dtlSet_form`, `transferApprove`, `transferChangeStatus`, `transferClose`, `transferConfirm`, `transferCreateModalClick`, `transferDelete`, `transferEdit`, `transferOpen`, `transferPopulate`, `transferReport`, `transferReset`, `transferResetMasterForm`, `transferScriptInit`, `transferShow`, `transfer_dtlSetCreateUpdateEvent`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), toStore(select), transactionDate(text), leadTime(number), remarks(textarea)
- **Grid columns:** SL, Code, From Store, TO Store, Date, Lead Time (days), Remarks, Action, SL, Item, Unit, Quantity, Specification, Action, SL, Item, Unit, Quantity, Specification

## inventoryTransferIssue — Transfer Issue

- **Endpoints:** /inventoryTransferIssue/edit, /inventoryTransferIssue/index, /inventoryTransferIssue/remove, /inventoryTransferIssue/write
- **Master-detail:** YES | fields: 32 | events: 7 | functions: 38
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `transfer_dtlSetAddBtn` → `transfer_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `transferReset()`
    - `onclick` on `issue_dtlSetAddBtn` → `issue_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `issueReset()`
- **JS functions:** `hs_issue_dtlSetDeleteEvent`, `hs_issue_dtlSetEditEvent`, `hs_transfer_dtlSetDeleteEvent`, `hs_transfer_dtlSetEditEvent`, `issueApprove`, `issueChangeStatus`, `issueClose`, `issueConfirm`, `issueCreateModalClick`, `issueDelete`, `issueEdit`, `issueOpen`, `issuePopulate`, `issueReport`, `issueReset`, `issueResetMasterForm`, `issueScriptInit`, `issueShow`, `issue_dtlSetCreateUpdateEvent`, `reset_issue_dtlSet_form`, `reset_transfer_dtlSet_form`, `transferApprove`, `transferChangeStatus`, `transferClose`, `transferConfirm`, `transferCreateModalClick`, `transferDelete`, `transferEdit`, `transferOpen`, `transferPopulate`, `transferReport`, `transferReset`, `transferResetMasterForm`, `transferScriptInit`, `transferShow`, `transfer_dtlSetCreateUpdateEvent`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), toStore(text), transactionDate(text), leadTime(number), remarks(textarea), code(text), toStore(text), transactionDate(text), leadTime(number), remarks(textarea)
- **Grid columns:** SL, Code, From Store, TO Store, Date, Lead Time (days), Remarks, Action, SL, Item, Unit, Quantity, Specification, Action, SL, Item, Unit, Quantity, Specification, SL

## inventoryTransferReceive — Transfer Receive

- **Endpoints:** /inventoryTransferReceive/edit, /inventoryTransferReceive/index, /inventoryTransferReceive/remove, /inventoryTransferReceive/write
- **Master-detail:** YES | fields: 32 | events: 7 | functions: 38
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `issue_dtlSetAddBtn` → `issue_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `issueReset()`
    - `onclick` on `receive_dtlSetAddBtn` → `receive_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `receiveReset()`
- **JS functions:** `hs_issue_dtlSetDeleteEvent`, `hs_issue_dtlSetEditEvent`, `hs_receive_dtlSetDeleteEvent`, `hs_receive_dtlSetEditEvent`, `issueApprove`, `issueChangeStatus`, `issueClose`, `issueConfirm`, `issueCreateModalClick`, `issueDelete`, `issueEdit`, `issueOpen`, `issuePopulate`, `issueReport`, `issueReset`, `issueResetMasterForm`, `issueScriptInit`, `issueShow`, `issue_dtlSetCreateUpdateEvent`, `receiveApprove`, `receiveChangeStatus`, `receiveClose`, `receiveConfirm`, `receiveCreateModalClick`, `receiveDelete`, `receiveEdit`, `receiveOpen`, `receivePopulate`, `receiveReport`, `receiveReset`, `receiveResetMasterForm`, `receiveScriptInit`, `receiveShow`, `receive_dtlSetCreateUpdateEvent`, `reset_issue_dtlSet_form`, `reset_receive_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), toStore(text), transactionDate(text), leadTime(number), remarks(textarea), code(text), toStore(text), transactionDate(text), leadTime(number), remarks(textarea)
- **Grid columns:** SL, Code, From Store, TO Store, Date, Lead Time (days), Remarks, Action, SL, Item, Unit, Quantity, Specification, Action, SL, Item, Unit, Quantity, Specification, SL

## itElectronicsFa — Non current asset Item

- **Endpoints:** /itElectronicsFa/edit, /itElectronicsFa/index, /itElectronicsFa/remove, /itElectronicsFa/write
- **Master-detail:** no | fields: 15 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `itemCreateModalClick();`
    - `onclick` on `?` → `itemReset()`
- **JS functions:** `itemApprove`, `itemChangeStatus`, `itemClose`, `itemConfirm`, `itemCreateModalClick`, `itemDelete`, `itemEdit`, `itemOpen`, `itemPopulate`, `itemReport`, `itemReset`, `itemResetMasterForm`, `itemScriptInit`, `itemShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), hsCode(select), countries(select), itemModel(select), itemBrand(select), itemCategory(select), remarks(textarea)
- **Grid columns:** SL, Code, Name, Sub Sub Category, Item Model, Item Brand, HS Code, Countries, Operation Unit, P-OP Conversion, Action

## itemBrand — Brand

- **Endpoints:** /itemBrand/edit, /itemBrand/index, /itemBrand/remove, /itemBrand/write
- **Master-detail:** no | fields: 9 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `brandCreateModalClick();`
    - `onclick` on `?` → `brandReset()`
- **JS functions:** `brandApprove`, `brandChangeStatus`, `brandClose`, `brandConfirm`, `brandCreateModalClick`, `brandDelete`, `brandEdit`, `brandOpen`, `brandPopulate`, `brandReport`, `brandReset`, `brandResetMasterForm`, `brandScriptInit`, `brandShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text)
- **Grid columns:** SL, Code, Name, Action

## itemCategory — Item Category

- **Endpoints:** /itemCategory/edit, /itemCategory/index, /itemCategory/remove, /itemCategory/write
- **Master-detail:** no | fields: 11 | events: 8 | functions: 8
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `$(`
    - `onclick` on `?` → `$(`
    - `onclick` on `?` → `pdfReport(this)`
    - `onclick` on `?` → `xlsReport(this)`
    - `onclick` on `?` → `categoryReset()`
- **JS functions:** `addSubSubCategory`, `categoryCreate`, `deleteSubSubCategory`, `editSubSubCategory`, `pdfReport`, `xlsReport`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), categoryTree_q(text), parentCategory(text), caption(text)

## itemLedgerReport — Item Ledger Report

- **Endpoints:** /itemLedgerReport/edit, /itemLedgerReport/index, /itemLedgerReport/remove, /itemLedgerReport/write
- **Master-detail:** no | fields: 16 | events: 6 | functions: 7
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `reportReset()`
    - `onclick` on `reportResetBtn` → `reportSearch()`
    - `onclick` on `?` → `reportbtnOne()`
- **JS functions:** `reportReport`, `reportReset`, `reportSearch`, `reportShow`, `reportbtnOne`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), inventory_item_code(text), inventory_item_caption(text), operation_unit_caption(text), transaction_date(text), issue_qty(text), receive_qty(text), confirm_status(text), approve_status(text), status(text)
- **Grid columns:** SL, code, Item No, Item Name, Unit, date, issue qty, receive qty, confirm status, approve status, status, Action

## itemModel — Model

- **Endpoints:** /itemModel/edit, /itemModel/index, /itemModel/remove, /itemModel/write
- **Master-detail:** no | fields: 9 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `modelCreateModalClick();`
    - `onclick` on `?` → `modelReset()`
- **JS functions:** `modelApprove`, `modelChangeStatus`, `modelClose`, `modelConfirm`, `modelCreateModalClick`, `modelDelete`, `modelEdit`, `modelOpen`, `modelPopulate`, `modelReport`, `modelReset`, `modelResetMasterForm`, `modelScriptInit`, `modelShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text)
- **Grid columns:** SL, Code, Name, Action

## lCTermsCondition — Page Not Found

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## lcClosing — Page Not Found

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## lcIssuing — Page Not Found

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## localAgent — Local Agent

- **Endpoints:** /localAgent/edit, /localAgent/index, /localAgent/remove, /localAgent/write
- **Master-detail:** no | fields: 18 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `localAgentCreateModalClick();`
    - `onclick` on `?` → `localAgentReset()`
- **JS functions:** `localAgentApprove`, `localAgentChangeStatus`, `localAgentClose`, `localAgentConfirm`, `localAgentCreateModalClick`, `localAgentDelete`, `localAgentEdit`, `localAgentOpen`, `localAgentPopulate`, `localAgentReport`, `localAgentReset`, `localAgentResetMasterForm`, `localAgentScriptInit`, `localAgentShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), address(text), contactNo(text), phoneNo(text), emailAddress(text), routingNumber(text), contactPerson(text), contactPersonMobile(text), accountName(text), accountNumber(text)
- **Grid columns:** SL, Code, Name, Contact NO, Telephone NO, Email Address, Contact Person, Contact Persons Mobile, Action

## machine — Machine

- **Endpoints:** /machine/edit, /machine/index, /machine/remove, /machine/write
- **Master-detail:** no | fields: 10 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `machineCreateModalClick();`
    - `onclick` on `?` → `machineReset()`
- **JS functions:** `machineApprove`, `machineChangeStatus`, `machineClose`, `machineConfirm`, `machineCreateModalClick`, `machineDelete`, `machineEdit`, `machineOpen`, `machinePopulate`, `machineReport`, `machineReset`, `machineResetMasterForm`, `machineScriptInit`, `machineShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), machineNo(text)
- **Grid columns:** SL, Code, Name, Machine No, Action

## marketingGroup — Marketing Group

- **Endpoints:** /marketingGroup/edit, /marketingGroup/index, /marketingGroup/remove, /marketingGroup/write
- **Master-detail:** YES | fields: 16 | events: 6 | functions: 20
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `groupCreateModalClick();`
    - `onclick` on `group_dtlSetAddBtn` → `group_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `groupReset()`
- **JS functions:** `groupApprove`, `groupChangeStatus`, `groupClose`, `groupConfirm`, `groupCreateModalClick`, `groupDelete`, `groupEdit`, `groupOpen`, `groupPopulate`, `groupReport`, `groupReset`, `groupResetMasterForm`, `groupScriptInit`, `groupShow`, `group_dtlSetCreateUpdateEvent`, `hs_group_dtlSetDeleteEvent`, `hs_group_dtlSetEditEvent`, `reset_group_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), remarks(textarea)
- **Grid columns:** SL, Code, Group Name, Group Members, Remarks, Action, SL, Employee, Sort Order, Status, remarks, Action, SL, Employee, Sort Order, Status, remarks

## monthlyStockReport — Monthly Stock Report

- **Endpoints:** /monthlyStockReport/edit, /monthlyStockReport/index, /monthlyStockReport/remove, /monthlyStockReport/write
- **Master-detail:** no | fields: 6 | events: 6 | functions: 7
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `reportReset()`
    - `onclick` on `reportResetBtn` → `reportSearch()`
    - `onclick` on `?` → `reportbtnOne()`
- **JS functions:** `reportReport`, `reportReset`, `reportSearch`, `reportShow`, `reportbtnOne`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)
- **Grid columns:** SL, Item No, Item Name, Transaction number, Transaction mode, Item Category, Warranty Period, Item Model, Item Brand, Construction, Action

## mroItem — MRO Item

- **Endpoints:** /mroItem/edit, /mroItem/index, /mroItem/remove, /mroItem/write
- **Master-detail:** no | fields: 15 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `itemCreateModalClick();`
    - `onclick` on `?` → `itemReset()`
- **JS functions:** `itemApprove`, `itemChangeStatus`, `itemClose`, `itemConfirm`, `itemCreateModalClick`, `itemDelete`, `itemEdit`, `itemOpen`, `itemPopulate`, `itemReport`, `itemReset`, `itemResetMasterForm`, `itemScriptInit`, `itemShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), hsCode(select), remarks(textarea), itemCategory(select), operationUnit(select), purchaseUnit(select), purchaseToOpUnitCon(number)
- **Grid columns:** SL, Code, Name, Operation Unit, Purchase Unit, P-OP Conversion, HS Code, Action

## organization — Organization

- **Endpoints:** /organization/edit, /organization/index, /organization/remove, /organization/write
- **Master-detail:** no | fields: 20 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `organizationCreateModalClick();`
    - `onclick` on `?` → `organizationReset()`
- **JS functions:** `organizationApprove`, `organizationChangeStatus`, `organizationClose`, `organizationConfirm`, `organizationCreateModalClick`, `organizationDelete`, `organizationEdit`, `organizationOpen`, `organizationPopulate`, `organizationReport`, `organizationReset`, `organizationResetMasterForm`, `organizationScriptInit`, `organizationShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), codePrefix(text), caption(text), shortName(text), supervisor(select), address(textarea), contactNo(text), phoneNo(text), emailAddress(text), imagePath(file), companyBanner(file), companyLogo(file), faviconIcon(file)
- **Grid columns:** SL, Code, Code Prefix, Name, Short Name, Address, Contact NO, Phone NO, Email Address, Supervisor, Action

## pITermsCondition — Page Not Found

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## plnDashboard — Work Order Dashboard

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 10 | events: 10 | functions: 13
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `periodFilter` → `loadDashboardData()`
    - `onchange` on `startDate` → `loadDashboardData()`
    - `onchange` on `endDate` → `loadDashboardData()`
    - `onchange` on `workTypeFilter` → `loadDashboardData()`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `updateWorkOrderTrend(`
    - `onclick` on `?` → `updateWorkOrderTrend(`
    - `onclick` on `?` → `updateWorkOrderTrend(`
- **JS functions:** `formatCurrency`, `formatNumber`, `getFilterParams`, `initStatusDistributionChart`, `initTopVendorsChart`, `initWorkOrderTrendChart`, `initWorkTypeChart`, `loadDashboardData`, `populateCompletionTable`, `updateKPIs`, `updateWorkOrderTrend`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)
- **Grid columns:** WO No, Type, Vendor, Ordered Qty, Received Qty, Completion

## prdDashboard — Production Dashboard

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 9 | events: 9 | functions: 13
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `periodFilter` → `loadDashboardData()`
    - `onchange` on `startDate` → `loadDashboardData()`
    - `onchange` on `endDate` → `loadDashboardData()`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `updateProductionTrend(`
    - `onclick` on `?` → `updateProductionTrend(`
    - `onclick` on `?` → `updateProductionTrend(`
- **JS functions:** `formatCurrency`, `formatNumber`, `getFilterParams`, `initDispoStatusChart`, `initFabricTypeChart`, `initOrderStatusChart`, `initProductionTrendChart`, `loadDashboardData`, `populateProgressTable`, `updateKPIs`, `updateProductionTrend`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)
- **Grid columns:** Order No, Customer, Ordered Qty, Delivered Qty, Completion

## proDeliveryType — Delivery Type

- **Endpoints:** /proDeliveryType/edit, /proDeliveryType/index, /proDeliveryType/remove, /proDeliveryType/write
- **Master-detail:** no | fields: 9 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `brandCreateModalClick();`
    - `onclick` on `?` → `brandReset()`
- **JS functions:** `brandApprove`, `brandChangeStatus`, `brandClose`, `brandConfirm`, `brandCreateModalClick`, `brandDelete`, `brandEdit`, `brandOpen`, `brandPopulate`, `brandReport`, `brandReset`, `brandResetMasterForm`, `brandScriptInit`, `brandShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text)
- **Grid columns:** SL, Code, Name, Action

## proPiDeliverySchedule — Request For PI

- **Endpoints:** /proPiDeliverySchedule/edit, /proPiDeliverySchedule/index, /proPiDeliverySchedule/remove, /proPiDeliverySchedule/write
- **Master-detail:** YES | fields: 27 | events: 10 | functions: 27
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `create_pds_dtlSet_dtlLine_transactionQty` → `pds_dtlSet_dtlLine_transactionQtyOnChangeEvent(this)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `pds_dtlSetAddBtn` → `pds_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_pds_dtlSet_dtlLineResetEvent()`
    - `onclick` on `pds_dtlSet_dtlLineResetBtn` → `pds_dtlSet_dtlLineCrUpEvent(this)`
    - `onclick` on `?` → `hs_`
    - `onclick` on `?` → `hs_`
- **JS functions:** `hsInnerTablesHtmlNew`, `hs_add_table_inner_data_new`, `hs_pds_dtlSetAddEvent`, `hs_pds_dtlSetDeleteEvent`, `hs_pds_dtlSetEditEvent`, `hs_pds_dtlSetViewTableEvent`, `hs_pds_dtlSet_dtlLineCopyEvent`, `hs_pds_dtlSet_dtlLineDeleteEvent`, `hs_pds_dtlSet_dtlLineEditEvent`, `hs_pds_dtlSet_dtlLineResetEvent`, `hs_pds_dtlSet_dtlLineViewTableEvent`, `pdsAddBpoRevision`, `pdsApprove`, `pdsChangeStatus`, `pdsConfirm`, `pdsDelete`, `pdsEdit`, `pdsReport`, `pdsReset`, `pdsRevisedConfirm`, `pdsShow`, `pds_dtlSetCreateUpdateEvent`, `pds_dtlSet_dtlLineCrUpEvent`, `pds_dtlSet_dtlLine_transactionQtyOnChangeEvent`, `reset_pds_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), transactionDate(text), customerGarments(select), color_name(text), solid_yarn_dyed_type(text), dispo_code(text), fabricsStyle(text), transactionQty(number)
- **Grid columns:** SL, Schedule NO, BPO NO, Dispo Number, PI NO, Date, Garments, Total Qty, Remarks, Status, Action, SL, BPO NO, BPO Date, Booking NO, Color breakdown, Action, SL, BPO NO, BPO Date

## proPiDeliveryScheduleReport — BPO Status Report

- **Endpoints:** /proPiDeliveryScheduleReport/edit, /proPiDeliveryScheduleReport/index, /proPiDeliveryScheduleReport/remove, /proPiDeliveryScheduleReport/write
- **Master-detail:** YES | fields: 28 | events: 7 | functions: 21
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `pds_dtlSetAddBtn` → `pds_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_pds_dtlSet_dtlLineResetEvent()`
    - `onclick` on `pds_dtlSet_dtlLineResetBtn` → `pds_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `hs_pds_dtlSetAddEvent`, `hs_pds_dtlSetDeleteEvent`, `hs_pds_dtlSetEditEvent`, `hs_pds_dtlSetViewTableEvent`, `hs_pds_dtlSet_dtlLineDeleteEvent`, `hs_pds_dtlSet_dtlLineEditEvent`, `hs_pds_dtlSet_dtlLineResetEvent`, `hs_pds_dtlSet_dtlLineViewTableEvent`, `pdsApprove`, `pdsChangeStatus`, `pdsConfirm`, `pdsDelete`, `pdsEdit`, `pdsReport`, `pdsReset`, `pdsShow`, `pds_dtlSetCreateUpdateEvent`, `pds_dtlSet_dtlLineCrUpEvent`, `reset_pds_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), so_code(text), approve_status(text), transaction_date(text), so_transaction_date(text), stakeholder_caption(text), transaction_qty(text), schedule_codes(text), construction(text), color_name(text), fabrics_style(text), po_transaction_qty(text), transaction_qty(text), dlo_quantity(text), issue_quantity(text)
- **Grid columns:** SL, Customer, BPO NO, BPO date, BPO QTY, Dispo NO, Schedule NO, PI NO, LC NO, Delivery Order NO, Fabrics Delivery NO, Action, SL, PI Number, Schedule Number, Garments, Color breakdown, Action, SL, PI Number

## proPiDeliveryScheduleRevision — Request For PI Revision

- **Endpoints:** /proPiDeliveryScheduleRevision/edit, /proPiDeliveryScheduleRevision/index, /proPiDeliveryScheduleRevision/remove, /proPiDeliveryScheduleRevision/write
- **Master-detail:** YES | fields: 26 | events: 9 | functions: 25
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `pds_dtlSetAddBtn` → `pds_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_pds_dtlSet_dtlLineResetEvent()`
    - `onclick` on `pds_dtlSet_dtlLineResetBtn` → `pds_dtlSet_dtlLineCrUpEvent(this)`
    - `onclick` on `?` → `hs_`
    - `onclick` on `?` → `hs_`
- **JS functions:** `hsInnerTablesHtmlNew`, `hs_add_table_inner_data_new`, `hs_pds_dtlSetAddEvent`, `hs_pds_dtlSetDeleteEvent`, `hs_pds_dtlSetEditEvent`, `hs_pds_dtlSetViewTableEvent`, `hs_pds_dtlSet_dtlLineCopyEvent`, `hs_pds_dtlSet_dtlLineDeleteEvent`, `hs_pds_dtlSet_dtlLineEditEvent`, `hs_pds_dtlSet_dtlLineResetEvent`, `hs_pds_dtlSet_dtlLineViewTableEvent`, `pdsApprove`, `pdsChangeStatus`, `pdsConfirm`, `pdsDelete`, `pdsEdit`, `pdsReport`, `pdsReset`, `pdsRevision`, `pdsShow`, `pds_dtlSetCreateUpdateEvent`, `pds_dtlSet_dtlLineCrUpEvent`, `reset_pds_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), transactionDate(text), customerGarments(select), fabricsStyle(text), transactionQty(number)
- **Grid columns:** SL, Schedule NO, BPO NO, PI NO, Date, Garments, Total Qty, Remarks, Status, Action, SL, BPO NO, BPO Date, Booking NO, Color breakdown, Action, SL, BPO NO, BPO Date, Booking NO

## productionOrder — Bulk Production Order(BPO)

- **Endpoints:** /productionOrder/edit, /productionOrder/index, /productionOrder/remove, /productionOrder/write
- **Master-detail:** YES | fields: 120 | events: 14 | functions: 44
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `create_po_dtlSet_dtlLine_transactionQty` → `po_dtlSet_dtlLine_transactionQtyOnChangeEvent(this)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `onclick_po_dtlSet_fabricsCost(this)`
    - `onclick` on `?` → `onclick_po_dtlSet_fabricsCostAmendmentNo(this)`
    - `onclick` on `?` → `onclick_po_dtlSet_gsmCalculated(this)`
    - `onclick` on `?` → `onclick_po_dtlSet_leadTime(this)`
    - `onclick` on `po_dtlSetAddBtn` → `po_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `po_dtlTcSetAddBtn` → `po_dtlTcSetCreateUpdateEvent(this)`
    - `onclick` on `po_dtlDeliversSetAddBtn` → `po_dtlDeliversSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_po_dtlSet_dtlLineResetEvent()`
    - `onclick` on `po_dtlSet_dtlLineResetBtn` → `po_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `docFilePathFileDownloadEvent`, `hs_po_dtlDeliversSetDeleteEvent`, `hs_po_dtlDeliversSetEditEvent`, `hs_po_dtlDeliversSetViewTableEvent`, `hs_po_dtlSetAddEvent`, `hs_po_dtlSetDeleteEvent`, `hs_po_dtlSetEditEvent`, `hs_po_dtlSetViewTableEvent`, `hs_po_dtlSet_dtlLineDeleteEvent`, `hs_po_dtlSet_dtlLineEditEvent`, `hs_po_dtlSet_dtlLineResetEvent`, `hs_po_dtlSet_dtlLineViewTableEvent`, `hs_po_dtlTcSetDeleteEvent`, `hs_po_dtlTcSetEditEvent`, `hs_po_dtlTcSetViewTableEvent`, `onclick_po_dtlSet_fabricsCost`, `onclick_po_dtlSet_fabricsCostAmendmentNo`, `onclick_po_dtlSet_gsmCalculated`, `poApprovalDetail`, `poApprove`, `poCancel`, `poChangeStatus`, `poConfirm`, `poDelete`, `poEdit`, `poOperationalDetail`, `poReport`, `poReset`, `poSaveAlert`, `poShow`, `po_dtlDeliversSetCreateUpdateEvent`, `po_dtlSetCreateUpdateEvent`, `po_dtlSet_dtlLineCrUpEvent`, `po_dtlSet_dtlLine_transactionQtyOnChangeEvent`, `po_dtlTcSetCreateUpdateEvent`, `populateExtraScript`, `reset_po_dtlDeliversSet_form`, `reset_po_dtlSet_form`, `reset_po_dtlTcSet_form`, `soPopulate`, `soReceive`, `soReport`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), salesOrderCode(text), bookingType(text), sampleType(text), inHouseTestReport(checkbox), inspectionReport(checkbox), stakeholder(text), customerBrand(text), customerGarments(select), dyeLot(checkbox), testFabrics(checkbox), blanket(checkbox), businessUnit(text), transactionDate(text), transactionDateSO(text), deliveryRequiredDate(text), marketingPerson(text), headCutting(checkbox), packingList(checkbox), priceInMeter(checkbox), remarks(textarea), dispo_number(text), colorCode(text), colorName(text), fabricsStyle(text), colorReference(text), strikeOffReference(text), labDipReference(text), transaction_qty_so(number), transactionQty(number), unitPrice(number), priceInMeter(text), remarks(textarea)
- **Grid columns:** SL, BPO Number, BPO Date, Delivery Required Date, Buyer, Booking Number, Booking Date, Currency, Average Rate, BPO Qty, BPO Amount, Dispo NO, DO Codes, DO Qty, BPO Status, ARV Status, Action, SL, Item, DISPO

## productionOrderReport — BPO Report

- **Endpoints:** /productionOrderReport/edit, /productionOrderReport/index, /productionOrderReport/remove, /productionOrderReport/write
- **Master-detail:** no | fields: 17 | events: 6 | functions: 7
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `proReset()`
    - `onclick` on `proResetBtn` → `proSearch()`
    - `onclick` on `?` → `probtnOne()`
- **JS functions:** `proReport`, `proReset`, `proSearch`, `proShow`, `probtnOne`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), bookingType(select), specialType(select), currency(select), fabricType(select), stakeholder(select), customerBrand(select), customerGarments(select), marketingPerson(select), swatchNumber(text), transaction_date_start(text), transaction_date_end(text)
- **Grid columns:** SL, BPO Number, Buyer, Garments, Team, Transaction date, Currency, BK Qty, AVG Price, Total Amount, Booking Number, BPO Qty, Total Amount, Status, Action

## productionOrderRevision — Bulk Production Order(BPO) Revision

- **Endpoints:** /productionOrderRevision/edit, /productionOrderRevision/index, /productionOrderRevision/remove, /productionOrderRevision/write
- **Master-detail:** YES | fields: 108 | events: 11 | functions: 29
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `onclick_po_dtlSet_fabricsCost(this)`
    - `onclick` on `?` → `onclick_po_dtlSet_fabricsCostAmendmentNo(this)`
    - `onclick` on `?` → `onclick_po_dtlSet_gsmCalculated(this)`
    - `onclick` on `?` → `onclick_po_dtlSet_leadTime(this)`
    - `onclick` on `po_dtlSetAddBtn` → `po_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_po_dtlSet_dtlLineResetEvent()`
    - `onclick` on `po_dtlSet_dtlLineResetBtn` → `po_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `docFilePathFileDownloadEvent`, `hs_po_dtlSetAddEvent`, `hs_po_dtlSetDeleteEvent`, `hs_po_dtlSetEditEvent`, `hs_po_dtlSetViewTableEvent`, `hs_po_dtlSet_dtlLineDeleteEvent`, `hs_po_dtlSet_dtlLineEditEvent`, `hs_po_dtlSet_dtlLineResetEvent`, `hs_po_dtlSet_dtlLineViewTableEvent`, `onclick_po_dtlSet_fabricsCost`, `onclick_po_dtlSet_fabricsCostAmendmentNo`, `onclick_po_dtlSet_gsmCalculated`, `poApprove`, `poChangeStatus`, `poConfirm`, `poDelete`, `poEdit`, `poEditExtraFunction`, `poPopulate`, `poReport`, `poReset`, `poRevision`, `poShow`, `poShowChanges`, `po_dtlSetCreateUpdateEvent`, `po_dtlSet_dtlLineCrUpEvent`, `reset_po_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), salesOrderCode(text), bookingType(text), sampleType(text), inHouseTestReport(checkbox), inspectionReport(checkbox), stakeholder(text), customerBrand(text), customerGarments(select), dyeLot(checkbox), blanket(checkbox), testFabrics(checkbox), businessUnit(text), transactionDate(text), transactionDateSO(text), deliveryRequiredDate(text), marketingPerson(text), headCutting(checkbox), packingList(checkbox), priceInMeter(checkbox), fabricType(text), remarks(textarea), colorCode(text), colorName(text), fabricsStyle(text), colorReference(text), strikeOffReference(text), labDipReference(text), transactionQty(number), unitPrice(number), remarks(textarea)
- **Grid columns:** SL, BPO Number, BPO Date, Delivery Required Date, Buyer, Booking Number, Booking Date, Currency, Average Rate, BPO Qty, BPO Amount, BPO Status, Action, SL, Item, Construction, Composition, Weave Type, Weave Styles, Finished Width (Inch)

## purDashboard — Grails

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## purchaseOrder — Purchase Order(null Item)

- **Endpoints:** /purchaseOrder/edit, /purchaseOrder/index, /purchaseOrder/remove, /purchaseOrder/write
- **Master-detail:** YES | fields: 41 | events: 8 | functions: 39
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `po_tab` → `itemInputTypeSelect(this)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `po_dtlSetAddBtn` → `po_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `poReset()`
    - `onclick` on `spr_dtlSetAddBtn` → `spr_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `sprReset()`
- **JS functions:** `hs_po_dtlSetDeleteEvent`, `hs_po_dtlSetEditEvent`, `hs_spr_dtlSetDeleteEvent`, `hs_spr_dtlSetEditEvent`, `itemInputTypeSelect`, `poApprove`, `poChangeStatus`, `poClose`, `poConfirm`, `poCreateModalClick`, `poDelete`, `poEdit`, `poOpen`, `poPopulate`, `poReport`, `poReset`, `poResetMasterForm`, `poScriptInit`, `poShow`, `po_dtlSetCreateUpdateEvent`, `reset_po_dtlSet_form`, `reset_spr_dtlSet_form`, `sprApprove`, `sprChangeStatus`, `sprClose`, `sprConfirm`, `sprCreateModalClick`, `sprDelete`, `sprEdit`, `sprOpen`, `sprPopulate`, `sprReport`, `sprReset`, `sprResetMasterForm`, `sprScriptInit`, `sprShow`, `spr_dtlSetCreateUpdateEvent`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), poType(select), transactionDate(text), stakeholder(select), remarks(textarea), code(text), poType(select), lead_time(text), transactionDate(text), stakeholder(select), remarks(textarea)
- **Grid columns:** SL, Code, Purchase Type, Supplier, Business Unit, Date, Total Amount, Item type, Total Amount, Action, SL, Item, Brand, Item Model, Quantity, Unit Price, Specification, Action, SL, Item

## rawMatConsumption — RAW Material Consumption

- **Endpoints:** /rawMatConsumption/edit, /rawMatConsumption/index, /rawMatConsumption/remove, /rawMatConsumption/write
- **Master-detail:** YES | fields: 27 | events: 7 | functions: 24
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `consumptionCreateModalClick();`
    - `onclick` on `consumption_dtlSetYarnAddBtn` → `consumption_dtlSetYarnCreateUpdateEvent(this)`
    - `onclick` on `consumption_dtlSetRawMatAddBtn` → `consumption_dtlSetRawMatCreateUpdateEvent(this)`
    - `onclick` on `?` → `consumptionReset()`
- **JS functions:** `consumptionApprove`, `consumptionChangeStatus`, `consumptionClose`, `consumptionConfirm`, `consumptionCreateModalClick`, `consumptionDelete`, `consumptionEdit`, `consumptionOpen`, `consumptionPopulate`, `consumptionReport`, `consumptionReset`, `consumptionResetMasterForm`, `consumptionScriptInit`, `consumptionShow`, `consumption_dtlSetRawMatCreateUpdateEvent`, `consumption_dtlSetYarnCreateUpdateEvent`, `hs_consumption_dtlSetRawMatDeleteEvent`, `hs_consumption_dtlSetRawMatEditEvent`, `hs_consumption_dtlSetYarnDeleteEvent`, `hs_consumption_dtlSetYarnEditEvent`, `reset_consumption_dtlSetRawMat_form`, `reset_consumption_dtlSetYarn_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), transactionDate(text), stakeholder(text), businessUnit(text), salesOrderCode(text), transactionDateSO(text)
- **Grid columns:** SL, BPO NO, Production Order Date, Buyer, Business Unit, Booking NO, Booking Date, Approved by, Received by, Action, SL, Item, Quantity, Allowance (%), Qty With Allowance, Specification, Action, SL, Item, Quantity

## rawMatIssue — RAW Material Issue

- **Endpoints:** /rawMatIssue/edit, /rawMatIssue/index, /rawMatIssue/remove, /rawMatIssue/write
- **Master-detail:** YES | fields: 37 | events: 7 | functions: 38
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `rawMatIssue_dtlSetAddBtn` → `rawMatIssue_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `rawMatIssueReset()`
    - `onclick` on `pro_dtlSetAddBtn` → `pro_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `proReset()`
- **JS functions:** `hs_pro_dtlSetDeleteEvent`, `hs_pro_dtlSetEditEvent`, `hs_rawMatIssue_dtlSetDeleteEvent`, `hs_rawMatIssue_dtlSetEditEvent`, `proApprove`, `proChangeStatus`, `proClose`, `proConfirm`, `proCreateModalClick`, `proDelete`, `proEdit`, `proOpen`, `proPopulate`, `proReport`, `proReset`, `proResetMasterForm`, `proScriptInit`, `proShow`, `pro_dtlSetCreateUpdateEvent`, `rawMatIssueApprove`, `rawMatIssueChangeStatus`, `rawMatIssueClose`, `rawMatIssueConfirm`, `rawMatIssueCreateModalClick`, `rawMatIssueDelete`, `rawMatIssueEdit`, `rawMatIssueOpen`, `rawMatIssuePopulate`, `rawMatIssueReport`, `rawMatIssueReset`, `rawMatIssueResetMasterForm`, `rawMatIssueScriptInit`, `rawMatIssueShow`, `rawMatIssue_dtlSetCreateUpdateEvent`, `reset_pro_dtlSet_form`, `reset_rawMatIssue_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), transactionDate(text), po_code(text), po_transaction_date(text), remarks(textarea), code(text), so_code(text), transactionDatePRO(text), transactionDate(text), remarks(textarea)
- **Grid columns:** SL, Issue No, Issue Date, BPO No, BPO Date, Period, Action, SL, Item, BPO Quantity, Stock Qty, Previous Issue, Issue Quantity, Specification, Action, SL, Item, BPO Quantity, Stock Qty, Previous Issue

## requestmap — Url Mapping

- **Endpoints:** /requestmap/edit, /requestmap/index, /requestmap/remove, /requestmap/write
- **Master-detail:** no | fields: 12 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `requestmapCreateModalClick();`
    - `onclick` on `?` → `requestmapReset()`
- **JS functions:** `requestmapApprove`, `requestmapChangeStatus`, `requestmapClose`, `requestmapConfirm`, `requestmapCreateModalClick`, `requestmapDelete`, `requestmapEdit`, `requestmapOpen`, `requestmapPopulate`, `requestmapReport`, `requestmapReset`, `requestmapResetMasterForm`, `requestmapScriptInit`, `requestmapShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), menu_name(text), index(select), write(select), edit(select), remove(select)
- **Grid columns:** SL, Name, Index, Write, Update, Remove, Action

## rndDashboard — R&D Analytics Dashboard

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 10 | events: 7 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `periodFilter` → `loadDashboardData()`
    - `onchange` on `startDate` → `loadDashboardData()`
    - `onchange` on `endDate` → `loadDashboardData()`
    - `onchange` on `projectTypeFilter` → `loadDashboardData()`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **JS functions:** `formatCurrency`, `formatNumber`, `getFilterParams`, `initCategoryChart`, `initInvestmentTrendChart`, `initPipelineChart`, `initROIChart`, `initStatusChart`, `initTeamChart`, `initTestingChart`, `loadDashboardData`, `loadProjectTypes`, `populateProjectsTable`, `updateKPIs`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)
- **Grid columns:** Project Code, Project Name, Category, Team Lead, Budget, Start Date, Progress, Status

## role — Role

- **Endpoints:** /role/edit, /role/index, /role/remove, /role/write
- **Master-detail:** no | fields: 8 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `roleCreateModalClick();`
    - `onclick` on `?` → `roleReset()`
- **JS functions:** `roleApprove`, `roleChangeStatus`, `roleClose`, `roleConfirm`, `roleCreateModalClick`, `roleDelete`, `roleEdit`, `roleOpen`, `rolePopulate`, `roleReport`, `roleReset`, `roleResetMasterForm`, `roleScriptInit`, `roleShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), authority(text)
- **Grid columns:** SL, Role Name, Action

## routCard — Page Not Found

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## salesReturn — 

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 7 | events: 1 | functions: 0
- **Events:**
    - `onclick` on `?` → `srReset()`
- **Fields:** code(text), requisitionType(text), transactionDate(text), leadTime(text), inventoryStore(text), remarks(textarea)
- **Grid columns:** SL, Item, Quantity, Specification, Action

## selvage — Page Not Found

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## slsDashboard — Sales Analytics Dashboard

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 10 | events: 10 | functions: 25
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `periodFilter` → `loadAllData()`
    - `onchange` on `startDate` → `loadAllData()`
    - `onchange` on `endDate` → `loadAllData()`
    - `onchange` on `salesPersonFilter` → `loadAllData()`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `updateRevenueTrendPeriod(`
    - `onclick` on `?` → `updateRevenueTrendPeriod(`
    - `onclick` on `?` → `updateRevenueTrendPeriod(`
- **JS functions:** `calculateGrowth`, `formatCurrency`, `formatNumber`, `getFilterParams`, `initOrderStatusChart`, `initRevenueTrendChart`, `initSalesByCategoryChart`, `initSalesTeamChart`, `initTopCustomersChart`, `initTopWeaveStylesChart`, `loadAllData`, `loadKPIs`, `loadOrderStatus`, `loadRecentOrders`, `loadRevenueTrend`, `loadSalesByCategory`, `loadSalesPersons`, `loadSalesTeam`, `loadTopCustomers`, `loadTopWeaveStyles`, `populateRecentOrdersTable`, `updateIcon`, `updateRevenueTrendPeriod`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)
- **Grid columns:** Order No, Date, Customer, Sales Person, Quantity, Amount, Status

## slsTermsAndConditions — Marketing Terms And Conditions

- **Endpoints:** /slsTermsAndConditions/edit, /slsTermsAndConditions/index, /slsTermsAndConditions/remove, /slsTermsAndConditions/write
- **Master-detail:** no | fields: 10 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `tncCreateModalClick();`
    - `onclick` on `?` → `tncReset()`
- **JS functions:** `tncApprove`, `tncChangeStatus`, `tncClose`, `tncConfirm`, `tncCreateModalClick`, `tncDelete`, `tncEdit`, `tncOpen`, `tncPopulate`, `tncReport`, `tncReset`, `tncResetMasterForm`, `tncScriptInit`, `tncShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), bodyText(textarea), sortOrder(number)
- **Grid columns:** SL, Code, Condition Body, Sort Order, Action

## sprDirGeneral — Direct SPR

- **Endpoints:** /sprDirGeneral/edit, /sprDirGeneral/index, /sprDirGeneral/remove, /sprDirGeneral/write
- **Master-detail:** YES | fields: 31 | events: 6 | functions: 20
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `sprCreateModalClick();`
    - `onclick` on `spr_dtlSetAddBtn` → `spr_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `sprReset()`
- **JS functions:** `hs_spr_dtlSetDeleteEvent`, `hs_spr_dtlSetEditEvent`, `reset_spr_dtlSet_form`, `sprApprove`, `sprChangeStatus`, `sprClose`, `sprConfirm`, `sprCreateModalClick`, `sprDelete`, `sprEdit`, `sprOpen`, `sprPopulate`, `sprReport`, `sprReset`, `sprResetMasterForm`, `sprScriptInit`, `sprShow`, `spr_dtlSetCreateUpdateEvent`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), transactionDate(text), leadTime(number), remarks(textarea)
- **Grid columns:** SL, SPR NO, Date, Lead Time (days), Confirm Status, Checked Status, Approve Status, Action, SL, Item, DISPO, Color Name, Brand, Construction, Composition, Fabrics Finish Type, Fabrics Width (Inch), GSM, Fabrics TR, Fabrics Tensile

## sprGeneral — SR TO SPR

- **Endpoints:** /sprGeneral/edit, /sprGeneral/index, /sprGeneral/remove, /sprGeneral/write
- **Master-detail:** YES | fields: 66 | events: 7 | functions: 38
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `spr_dtlSetAddBtn` → `spr_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `sprReset()`
    - `onclick` on `sr_dtlSetAddBtn` → `sr_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `srReset()`
- **JS functions:** `hs_spr_dtlSetDeleteEvent`, `hs_spr_dtlSetEditEvent`, `hs_sr_dtlSetDeleteEvent`, `hs_sr_dtlSetEditEvent`, `reset_spr_dtlSet_form`, `reset_sr_dtlSet_form`, `sprApprove`, `sprChangeStatus`, `sprClose`, `sprConfirm`, `sprCreateModalClick`, `sprDelete`, `sprEdit`, `sprOpen`, `sprPopulate`, `sprReport`, `sprReset`, `sprResetMasterForm`, `sprScriptInit`, `sprShow`, `spr_dtlSetCreateUpdateEvent`, `srApprove`, `srChangeStatus`, `srClose`, `srConfirm`, `srCreateModalClick`, `srDelete`, `srEdit`, `srOpen`, `srPopulate`, `srReport`, `srReset`, `srResetMasterForm`, `srScriptInit`, `srShow`, `sr_dtlSetCreateUpdateEvent`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), inventoryStoreDummy(text), transactionDate(text), leadTime(number), remarks(textarea), code(text), inventoryItemDummy(text), requisitionType(text), transactionDateSR(text), transactionDate(text), leadTime(text), remarks(textarea)
- **Grid columns:** SL, SPR No, SR No, Date, Lead Time (days), Item Store, Confirm Status, Checked Status, Approve Status, Action, SL, Item, Dispo NO, Construction, Composition, Fabrics Finish Type, Fabrics Width (Inch), GSM, Stock Qty, SR Qty

## sprReport — Page Not Found

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## srIssue — Item Issue Against SR

- **Endpoints:** /srIssue/edit, /srIssue/index, /srIssue/remove, /srIssue/write
- **Master-detail:** YES | fields: 46 | events: 7 | functions: 38
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `srIssue_dtlSetAddBtn` → `srIssue_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `srIssueReset()`
    - `onclick` on `sr_dtlSetAddBtn` → `sr_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `srReset()`
- **JS functions:** `hs_srIssue_dtlSetDeleteEvent`, `hs_srIssue_dtlSetEditEvent`, `hs_sr_dtlSetDeleteEvent`, `hs_sr_dtlSetEditEvent`, `reset_srIssue_dtlSet_form`, `reset_sr_dtlSet_form`, `srApprove`, `srChangeStatus`, `srClose`, `srConfirm`, `srCreateModalClick`, `srDelete`, `srEdit`, `srIssueApprove`, `srIssueChangeStatus`, `srIssueClose`, `srIssueConfirm`, `srIssueCreateModalClick`, `srIssueDelete`, `srIssueEdit`, `srIssueOpen`, `srIssuePopulate`, `srIssueReport`, `srIssueReset`, `srIssueResetMasterForm`, `srIssueScriptInit`, `srIssueShow`, `srIssue_dtlSetCreateUpdateEvent`, `srOpen`, `srPopulate`, `srReport`, `srReset`, `srResetMasterForm`, `srScriptInit`, `srShow`, `sr_dtlSetCreateUpdateEvent`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), srDateDummy(text), transactionDate(text), inventoryStore(text), storeRequisitionDummy(text), remarks(textarea), code(text), inventoryItemDummy(text), requisitionType(text), transactionDateSR(text), transactionDate(text), leadTime(text), remarks(textarea)
- **Grid columns:** SL, Issue No, Date, SR No, SR Date, Period, Item Store, Action, SL, Item, Dispo NO, Color Name, Brand, SR Quantity, Stock Qty, Previous Issue, Issue Quantity, Specification, Action, SL

## srReport — Page Not Found

- **Endpoints:** (none in script)
- **Master-detail:** no | fields: 6 | events: 3 | functions: 2
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)

## stockReport — Transaction Report

- **Endpoints:** /stockReport/edit, /stockReport/index, /stockReport/remove, /stockReport/write
- **Master-detail:** no | fields: 6 | events: 6 | functions: 7
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `reportReset()`
    - `onclick` on `reportResetBtn` → `reportSearch()`
    - `onclick` on `?` → `reportbtnOne()`
- **JS functions:** `reportReport`, `reportReset`, `reportSearch`, `reportShow`, `reportbtnOne`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio)
- **Grid columns:** SL, code, inventory_item_code, inventory_item_caption, operation_unit_caption, transaction_date, issue_qty, receive_qty, confirm_status, approve_status, status, Action

## storePurchaseRequisition — Store Purchase Requisition

- **Endpoints:** /storePurchaseRequisition/edit, /storePurchaseRequisition/index, /storePurchaseRequisition/remove, /storePurchaseRequisition/write
- **Master-detail:** YES | fields: 62 | events: 8 | functions: 38
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `spr_tab` → `sprCreateModalClick();`
    - `onclick` on `spr_dtlSetAddBtn` → `spr_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `sprReset()`
    - `onclick` on `sr_dtlSetAddBtn` → `sr_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `srReset()`
- **JS functions:** `hs_spr_dtlSetDeleteEvent`, `hs_spr_dtlSetEditEvent`, `hs_sr_dtlSetDeleteEvent`, `hs_sr_dtlSetEditEvent`, `reset_spr_dtlSet_form`, `reset_sr_dtlSet_form`, `sprApprove`, `sprChangeStatus`, `sprClose`, `sprConfirm`, `sprCreateModalClick`, `sprDelete`, `sprEdit`, `sprOpen`, `sprPopulate`, `sprReport`, `sprReset`, `sprResetMasterForm`, `sprScriptInit`, `sprShow`, `spr_dtlSetCreateUpdateEvent`, `srApprove`, `srChangeStatus`, `srClose`, `srConfirm`, `srCreateModalClick`, `srDelete`, `srEdit`, `srOpen`, `srPopulate`, `srReport`, `srReset`, `srResetMasterForm`, `srScriptInit`, `srShow`, `sr_dtlSetCreateUpdateEvent`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), inventoryStoreDummy(text), transactionDate(text), leadTime(number), remarks(textarea), code(text), inventoryItemDummy(text), requisitionType(text), transactionDateSR(text), transactionDate(text), leadTime(text), remarks(textarea)
- **Grid columns:** SL, SPR No, SR No, Date, Lead Time (days), Item Store, Confirm Status, Checked Status, Approve Status, Action, SL, Item, Machine Name, Dispo NO, Color Name, Composition, Fabrics Width (Inch), Construction, GSM, Fabrics Finish Type

## storeRequisition — Store Requisition(null)

- **Endpoints:** /storeRequisition/edit, /storeRequisition/index, /storeRequisition/remove, /storeRequisition/write
- **Master-detail:** YES | fields: 21 | events: 6 | functions: 21
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `?` → `itemInputTypeSelect(this)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `storeRequisition_dtlSetAddBtn` → `storeRequisition_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `storeRequisitionReset()`
- **JS functions:** `hs_storeRequisition_dtlSetDeleteEvent`, `hs_storeRequisition_dtlSetEditEvent`, `itemInputTypeSelect`, `reset_storeRequisition_dtlSet_form`, `storeRequisitionApprove`, `storeRequisitionChangeStatus`, `storeRequisitionClose`, `storeRequisitionConfirm`, `storeRequisitionCreateModalClick`, `storeRequisitionDelete`, `storeRequisitionEdit`, `storeRequisitionOpen`, `storeRequisitionPopulate`, `storeRequisitionReport`, `storeRequisitionReset`, `storeRequisitionResetMasterForm`, `storeRequisitionScriptInit`, `storeRequisitionShow`, `storeRequisition_dtlSetCreateUpdateEvent`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), requisitionType(select), department(select), transactionDate(text), leadTime(number), inventoryStore(select), remarks(textarea)
- **Grid columns:** SL, Code, Requisition Type, Item Store, Department, Date, Lead Time (days), Confirm Status, Checked Status, Approve Status, Action, SL, Item, Quantity, Specification, Action, SL, Item, Quantity, Specification

## supplier — Supplier

- **Endpoints:** /supplier/edit, /supplier/index, /supplier/remove, /supplier/write
- **Master-detail:** no | fields: 20 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `supplierCreateModalClick();`
    - `onclick` on `?` → `supplierReset()`
- **JS functions:** `supplierApprove`, `supplierChangeStatus`, `supplierClose`, `supplierConfirm`, `supplierCreateModalClick`, `supplierDelete`, `supplierEdit`, `supplierOpen`, `supplierPopulate`, `supplierReport`, `supplierReset`, `supplierResetMasterForm`, `supplierScriptInit`, `supplierShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), address(text), contactNo(text), phoneNo(text), emailAddress(text), routingNumber(text), contactPerson(text), contactPersonMobile(text), accountName(text), accountNumber(text), shippingAddress(textarea), billingAddress(textarea)
- **Grid columns:** SL, Code, Name, Contact NO, Telephone NO, Email Address, Contact Person, Contact Persons Mobile, Action

## termsAndConditions — Terms &amp; Conditions

- **Endpoints:** /termsAndConditions/edit, /termsAndConditions/index, /termsAndConditions/remove, /termsAndConditions/write
- **Master-detail:** no | fields: 14 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `tncCreateModalClick();`
    - `onclick` on `?` → `tncReset()`
- **JS functions:** `tncApprove`, `tncChangeStatus`, `tncClose`, `tncConfirm`, `tncCreateModalClick`, `tncDelete`, `tncEdit`, `tncOpen`, `tncPopulate`, `tncReport`, `tncReset`, `tncResetMasterForm`, `tncScriptInit`, `tncShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), bodyText(textarea), conditionType(select), isDefault(checkbox), sortOrder(number), isDefault(checkbox)
- **Grid columns:** SL, Code, Name, Condition Body, Type, Default, Sort Order, Action

## textileIssue — Greige Issue for Processing

- **Endpoints:** /textileIssue/edit, /textileIssue/index, /textileIssue/remove, /textileIssue/write
- **Master-detail:** YES | fields: 29 | events: 7 | functions: 25
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `create_issue_dtlSet_dtlLine_issueQty` → `issue_dtlSet_dtlLine_issueQtyOnChangeEvent(this)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_issue_dtlSet_dtlLineResetEvent()`
    - `onclick` on `issue_dtlSet_dtlLineResetBtn` → `issue_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `hs_issue_dtlSetAddEvent`, `hs_issue_dtlSetDeleteEvent`, `hs_issue_dtlSetEditEvent`, `hs_issue_dtlSetViewTableEvent`, `hs_issue_dtlSet_dtlLineDeleteEvent`, `hs_issue_dtlSet_dtlLineEditEvent`, `hs_issue_dtlSet_dtlLineResetEvent`, `hs_issue_dtlSet_dtlLineViewTableEvent`, `issueApprove`, `issueChangeStatus`, `issueConfirm`, `issueDelete`, `issueEdit`, `issueReport`, `issueReset`, `issueShow`, `issue_dtlSetCreateUpdateEvent`, `issue_dtlSet_dtlLineCrUpEvent`, `issue_dtlSet_dtlLine_issueQtyOnChangeEvent`, `reset_issue_dtlSet_form`, `woPopulate`, `woReceive`, `woReport`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), woCode(text), salesOrderCode(text), productionOrderCode(text), transactionDate(text), transactionDateWO(text), transactionDatePO(text), transactionDateSO(text), marketingPerson(text), stakeholder(text), remarks(textarea), colorCode(text), colorName(text), issueQty(number), filePath(text), remarks(textarea)
- **Grid columns:** SL, Receiving NO, Receiving Date, Customer, WO NO, WO Date, BPO NO, BPO Date, Dispo NO, WO QTY, Issue QTY, Total ISU, Marketing Person, Status, Action, SL, Item, Construction, Composition, Weave Type

## textileReceive — Greige Fabrics Received

- **Endpoints:** /textileReceive/edit, /textileReceive/index, /textileReceive/remove, /textileReceive/write
- **Master-detail:** YES | fields: 28 | events: 7 | functions: 25
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `create_receive_dtlSet_dtlLine_receiveQty` → `receive_dtlSet_dtlLine_receiveQtyOnChangeEvent(this)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_receive_dtlSet_dtlLineResetEvent()`
    - `onclick` on `receive_dtlSet_dtlLineResetBtn` → `receive_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `hs_receive_dtlSetAddEvent`, `hs_receive_dtlSetDeleteEvent`, `hs_receive_dtlSetEditEvent`, `hs_receive_dtlSetViewTableEvent`, `hs_receive_dtlSet_dtlLineDeleteEvent`, `hs_receive_dtlSet_dtlLineEditEvent`, `hs_receive_dtlSet_dtlLineResetEvent`, `hs_receive_dtlSet_dtlLineViewTableEvent`, `proPopulate`, `proReceive`, `proReport`, `receiveApprove`, `receiveChangeStatus`, `receiveConfirm`, `receiveDelete`, `receiveEdit`, `receiveReport`, `receiveReset`, `receiveShow`, `receive_dtlSetCreateUpdateEvent`, `receive_dtlSet_dtlLineCrUpEvent`, `receive_dtlSet_dtlLine_receiveQtyOnChangeEvent`, `reset_receive_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), woCode(text), salesOrderCode(text), productionOrderCode(text), transactionDate(text), transactionDateWO(text), transactionDatePO(text), transactionDateSO(text), marketingPerson(text), stakeholder(text), remarks(textarea), colorCode(text), colorName(text), receiveQty(number), remarks(textarea)
- **Grid columns:** SL, Receiving NO, Receiving Date, Customer, WO NO, WO Date, BPO NO, BPO Date, Dispo NO, WO QTY, Receive QTY, Total RCV, Marketing Person, Status, Action, SL, Item, Construction, Composition, Weave Type

## textileWoByPro — Weaving WO against BPO

- **Endpoints:** /textileWoByPro/edit, /textileWoByPro/index, /textileWoByPro/remove, /textileWoByPro/write
- **Master-detail:** YES | fields: 29 | events: 7 | functions: 25
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onchange` on `create_wo_dtlSet_dtlLine_transactionQty` → `wo_dtlSet_dtlLine_transactionQtyOnChangeEvent(this)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `soReset()`
    - `onclick` on `?` → `hs_wo_dtlSet_dtlLineResetEvent()`
    - `onclick` on `wo_dtlSet_dtlLineResetBtn` → `wo_dtlSet_dtlLineCrUpEvent(this)`
- **JS functions:** `hs_wo_dtlSetAddEvent`, `hs_wo_dtlSetDeleteEvent`, `hs_wo_dtlSetEditEvent`, `hs_wo_dtlSetViewTableEvent`, `hs_wo_dtlSet_dtlLineDeleteEvent`, `hs_wo_dtlSet_dtlLineEditEvent`, `hs_wo_dtlSet_dtlLineResetEvent`, `hs_wo_dtlSet_dtlLineViewTableEvent`, `proPopulate`, `proReceive`, `proReport`, `reset_wo_dtlSet_form`, `woApprove`, `woChangeStatus`, `woConfirm`, `woDelete`, `woEdit`, `woReport`, `woReset`, `woShow`, `wo_dtlSetCreateUpdateEvent`, `wo_dtlSet_dtlLineCrUpEvent`, `wo_dtlSet_dtlLine_transactionQtyOnChangeEvent`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), po_transaction_qty(text), productionOrderCode(text), dispo_number(text), transactionDatePRO(text), transactionDate(text), stakeholder(text), transactionDateSO(text), marketingPerson(text), remarks(textarea), colorCode(text), colorName(text), transactionQty(number), remarks(textarea)
- **Grid columns:** SL, WO NO, WO Date, BPO NO, BPO Date, Dispo NO, BPO QTY, WO QTY, Buyer, WO Status, Action, SL, Item, Construction, Composition, Weave Type, Finished Width, Cuttable Width, Color breakdown, Action

## unitsOfMeasure — Item Unit

- **Endpoints:** /unitsOfMeasure/edit, /unitsOfMeasure/index, /unitsOfMeasure/remove, /unitsOfMeasure/write
- **Master-detail:** no | fields: 20 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `unitCreateModalClick();`
    - `onclick` on `?` → `unitReset()`
- **JS functions:** `unitApprove`, `unitChangeStatus`, `unitClose`, `unitConfirm`, `unitCreateModalClick`, `unitDelete`, `unitEdit`, `unitOpen`, `unitPopulate`, `unitReport`, `unitReset`, `unitResetMasterForm`, `unitScriptInit`, `unitShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), shortName(text), fractionAllow(checkbox), isDefault(checkbox), isOperationUnit(checkbox), isPurchaseUnit(checkbox), isSalesUnit(checkbox), fractionAllow(checkbox), isDefault(checkbox), isOperationUnit(checkbox), isPurchaseUnit(checkbox), isSalesUnit(checkbox)
- **Grid columns:** SL, Code, Name, Short name, Fraction Allow, Default, Operation Unit, Purchase Unit, Sales Unit, Action

## user — User

- **Endpoints:** /user/edit, /user/index, /user/remove, /user/write
- **Master-detail:** no | fields: 16 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `userCreateModalClick();`
    - `onclick` on `?` → `userReset()`
- **JS functions:** `userApprove`, `userChangeStatus`, `userClose`, `userConfirm`, `userCreateModalClick`, `userDelete`, `userEdit`, `userOpen`, `userPopulate`, `userReport`, `userReset`, `userResetMasterForm`, `userScriptInit`, `userShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), employee(select), username(text), password(text), rePassword(text), employeeType(select), dashboardEnum(select), organizations(select), businessUnits(select), inventoryStores(select)
- **Grid columns:** SL, Employee, Designation, Department, Username, Enabled, Employee Type, Dashboard, Last Login, Business Units, Inventory Store, Action

## userRole — Assign Role

- **Endpoints:** /userRole/edit, /userRole/index, /userRole/remove, /userRole/write
- **Master-detail:** no | fields: 10 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `userRoleCreateModalClick();`
    - `onclick` on `?` → `userRoleReset()`
- **JS functions:** `userRoleApprove`, `userRoleChangeStatus`, `userRoleClose`, `userRoleConfirm`, `userRoleCreateModalClick`, `userRoleDelete`, `userRoleEdit`, `userRoleOpen`, `userRolePopulate`, `userRoleReport`, `userRoleReset`, `userRoleResetMasterForm`, `userRoleScriptInit`, `userRoleShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), employee_caption(text), user_caption(text), role(select)
- **Grid columns:** SL, Employee Name, Designation, Department, User Name, Role, Action

## yarnComposition — Yarn Composition

- **Endpoints:** /yarnComposition/edit, /yarnComposition/index, /yarnComposition/remove, /yarnComposition/write
- **Master-detail:** YES | fields: 14 | events: 6 | functions: 20
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `compositionCreateModalClick();`
    - `onclick` on `composition_dtlSetAddBtn` → `composition_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `compositionReset()`
- **JS functions:** `compositionApprove`, `compositionChangeStatus`, `compositionClose`, `compositionConfirm`, `compositionCreateModalClick`, `compositionDelete`, `compositionEdit`, `compositionOpen`, `compositionPopulate`, `compositionReport`, `compositionReset`, `compositionResetMasterForm`, `compositionScriptInit`, `compositionShow`, `composition_dtlSetCreateUpdateEvent`, `hs_composition_dtlSetDeleteEvent`, `hs_composition_dtlSetEditEvent`, `reset_composition_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), remarks(textarea)
- **Grid columns:** SL, Code, Name, Remarks, Action, SL, Blend %, Fiber, Action, SL, Blend %, Fiber

## yarnConsumption — Yarn Consumption

- **Endpoints:** /yarnConsumption/edit, /yarnConsumption/index, /yarnConsumption/remove, /yarnConsumption/write
- **Master-detail:** YES | fields: 21 | events: 6 | functions: 20
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `consumptionCreateModalClick();`
    - `onclick` on `consumption_dtlSetAddBtn` → `consumption_dtlSetCreateUpdateEvent(this)`
    - `onclick` on `?` → `consumptionReset()`
- **JS functions:** `consumptionApprove`, `consumptionChangeStatus`, `consumptionClose`, `consumptionConfirm`, `consumptionCreateModalClick`, `consumptionDelete`, `consumptionEdit`, `consumptionOpen`, `consumptionPopulate`, `consumptionReport`, `consumptionReset`, `consumptionResetMasterForm`, `consumptionScriptInit`, `consumptionShow`, `consumption_dtlSetCreateUpdateEvent`, `hs_consumption_dtlSetDeleteEvent`, `hs_consumption_dtlSetEditEvent`, `reset_consumption_dtlSet_form`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), transactionDate(text), stakeholder(text), businessUnit(text), salesOrderCode(text), transactionDateSO(text)
- **Grid columns:** SL, BPO NO, Production Order Date, Buyer, Business Unit, Booking NO, Booking Date, Approved by, Received by, Action, SL, Item, Weaving Type, Cone Length, Quantity, Specification, Action, SL, Item, Weaving Type

## yarnCount — Yarn Count

- **Endpoints:** /yarnCount/edit, /yarnCount/index, /yarnCount/remove, /yarnCount/write
- **Master-detail:** no | fields: 9 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `prefCreateModalClick();`
    - `onclick` on `?` → `prefReset()`
- **JS functions:** `prefApprove`, `prefChangeStatus`, `prefClose`, `prefConfirm`, `prefCreateModalClick`, `prefDelete`, `prefEdit`, `prefOpen`, `prefPopulate`, `prefReport`, `prefReset`, `prefResetMasterForm`, `prefScriptInit`, `prefShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text)
- **Grid columns:** SL, Code, Name, Action

## yarnFiber — Yarn Fiber

- **Endpoints:** /yarnFiber/edit, /yarnFiber/index, /yarnFiber/remove, /yarnFiber/write
- **Master-detail:** no | fields: 9 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `prefCreateModalClick();`
    - `onclick` on `?` → `prefReset()`
- **JS functions:** `prefApprove`, `prefChangeStatus`, `prefClose`, `prefConfirm`, `prefCreateModalClick`, `prefDelete`, `prefEdit`, `prefOpen`, `prefPopulate`, `prefReport`, `prefReset`, `prefResetMasterForm`, `prefScriptInit`, `prefShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text)
- **Grid columns:** SL, Code, Name, Action

## yarnItem — Yarn Item

- **Endpoints:** /yarnItem/edit, /yarnItem/index, /yarnItem/remove, /yarnItem/write
- **Master-detail:** no | fields: 20 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `itemCreateModalClick();`
    - `onclick` on `?` → `itemReset()`
- **JS functions:** `itemApprove`, `itemChangeStatus`, `itemClose`, `itemConfirm`, `itemCreateModalClick`, `itemDelete`, `itemEdit`, `itemOpen`, `itemPopulate`, `itemReport`, `itemReset`, `itemResetMasterForm`, `itemScriptInit`, `itemShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text), yarnCount(select), yarnPly(select), yarnType(select), yarnComposition(select), itemCategory(select), operationUnit(select), purchaseUnit(select), purchaseToOpUnitCon(number), salesUnit(select), salesToOpUnitCon(number), remarks(textarea)
- **Grid columns:** SL, Code, Name, Category, Yarn Count, Yarn Ply, Yarn Type, Yarn Composition, Operation Unit, Purchase Unit, P-OP Conversion, Sales Unit, S-OP Conversion, Action

## yarnPly — Yarn Ply

- **Endpoints:** /yarnPly/edit, /yarnPly/index, /yarnPly/remove, /yarnPly/write
- **Master-detail:** no | fields: 9 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `prefCreateModalClick();`
    - `onclick` on `?` → `prefReset()`
- **JS functions:** `prefApprove`, `prefChangeStatus`, `prefClose`, `prefConfirm`, `prefCreateModalClick`, `prefDelete`, `prefEdit`, `prefOpen`, `prefPopulate`, `prefReport`, `prefReset`, `prefResetMasterForm`, `prefScriptInit`, `prefShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text)
- **Grid columns:** SL, Code, Name, Action

## yarnType — Yarn Type

- **Endpoints:** /yarnType/edit, /yarnType/index, /yarnType/remove, /yarnType/write
- **Master-detail:** no | fields: 9 | events: 5 | functions: 16
- **Events:**
    - `onchange` on `?` → `unitChangeFunction(13196)`
    - `onchange` on `?` → `storeChangeFunction(13197)`
    - `onclick` on `?` → `window.open(document.getElementById(`
    - `onclick` on `?` → `prefCreateModalClick();`
    - `onclick` on `?` → `prefReset()`
- **JS functions:** `prefApprove`, `prefChangeStatus`, `prefClose`, `prefConfirm`, `prefCreateModalClick`, `prefDelete`, `prefEdit`, `prefOpen`, `prefPopulate`, `prefReport`, `prefReset`, `prefResetMasterForm`, `prefScriptInit`, `prefShow`
- **Fields:** app-theme-dark-mode(checkbox), businessUnitSession(radio), storeSession(radio), code(text), caption(text)
- **Grid columns:** SL, Code, Name, Action
