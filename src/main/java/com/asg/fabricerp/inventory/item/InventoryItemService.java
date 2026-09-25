package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.numbering.BusinessNumberService;
import com.asg.fabricerp.global.numbering.BusinessSeries;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.asg.fabricerp.inventory.item.Masters.*;

/**
 * The item master. Ported from SpindleERP's {@code InventoryItemServiceImpl}; the rules kept are
 * its own (yarn needs type, count, ply and blend and takes its generated name; no two active
 * yarns share that combination; a hazardous chemical needs a safety data sheet). Added:
 * the category must be an ITEM-layer category of the same item type, a model must belong to the
 * chosen brand, minimum stock cannot exceed maximum, and a fiber that blends use cannot stop
 * being a fiber or be deleted.
 */
@Service
public class InventoryItemService {

    private static final int LOOKUP_LIMIT = 50;

    public record ItemRequest(
        Long id, ItemType itemType, String name, String nameBn, String description,
        Long categoryId, Long baseUnitId, Long hsCodeId, Long brandId, Long modelId,
        String barcode, String sku, Boolean active,
        // stock & pricing
        BigDecimal reorderLevel, BigDecimal minimumStock, BigDecimal maximumStock,
        BigDecimal unitPrice, BigDecimal costPrice, BigDecimal taxRate,
        // fiber
        FiberType fiberType, String originName, String grade, BigDecimal stapleLength,
        BigDecimal micronaire, BigDecimal strength, BigDecimal moisture, BigDecimal trash, BigDecimal purity,
        // yarn
        Long yarnTypeId, Long yarnCountId, Long yarnPlyId, Long yarnBlendId, String qualityGrade,
        // chemicals
        String chemicalFormula, String casNumber, Boolean hazardous, String safetyDataSheet,
        BigDecimal concentration, LocalDate expiryDate,
        // fixed asset
        String manufacturer, String modelNumber, String serialNumber, Integer warrantyMonths,
        BigDecimal assetValue, BigDecimal depreciationRate,
        // production & costing
        BigDecimal processLossPercent, BigDecimal yieldPercent,
        BigDecimal standardCostPerKg, BigDecimal sellingPricePerKg) { }

    private final InventoryItemRepository repository;
    private final ItemCategoryService categories;
    private final UnitOfMeasureService units;
    private final HsCodeService hsCodes;
    private final ItemBrandService brands;
    private final ItemModelService models;
    private final YarnTypeService yarnTypes;
    private final YarnCountService yarnCounts;
    private final YarnPlyService yarnPlies;
    private final YarnBlendService yarnBlends;
    private final YarnBlendRepository blendRepository;
    private final BusinessNumberService numbering;
    private final OrgContext context;

    public InventoryItemService(InventoryItemRepository repository, ItemCategoryService categories,
                                UnitOfMeasureService units, HsCodeService hsCodes, ItemBrandService brands,
                                ItemModelService models, YarnTypeService yarnTypes, YarnCountService yarnCounts,
                                YarnPlyService yarnPlies, YarnBlendService yarnBlends,
                                YarnBlendRepository blendRepository, BusinessNumberService numbering,
                                OrgContext context) {
        this.repository = repository;
        this.categories = categories;
        this.units = units;
        this.hsCodes = hsCodes;
        this.brands = brands;
        this.models = models;
        this.yarnTypes = yarnTypes;
        this.yarnCounts = yarnCounts;
        this.yarnPlies = yarnPlies;
        this.yarnBlends = yarnBlends;
        this.blendRepository = blendRepository;
        this.numbering = numbering;
        this.context = context;
    }

    // ---- Reads -----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> search(ItemType type, Long categoryId, Boolean active, String q,
                                            Pageable pageable) {
        return repository.search(context.requireOrganizationId(), type, categoryId, active, q, pageable)
            .map(InventoryItemService::row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        return detailRow(get(id));
    }

    /** Picker feed for other screens: "code | name | unit". */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> lookup(ItemType type, String q) {
        return repository.lookup(context.requireOrganizationId(), type, clean(q), PageRequest.of(0, LOOKUP_LIMIT))
            .stream()
            .map(i -> Map.<String, Object>of(
                "id", i.getId(),
                "code", i.getItemCode(),
                "text", i.getItemCode() + " | " + i.getName() + " | " + i.getBaseUnit().getCode(),
                "itemType", i.getItemType(),
                "baseUnitId", i.getBaseUnit().getId(),
                "baseUnitCode", i.getBaseUnit().getCode()))
            .toList();
    }

    @Transactional(readOnly = true)
    public InventoryItem get(Long id) {
        return repository.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Item not found: " + id));
    }

    // ---- Writes ----------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> save(ItemRequest r) {
        Long orgId = context.requireOrganizationId();
        ItemType type = required(r.itemType(), "Item type");

        InventoryItem item;
        if (r.id() == null) {
            item = new InventoryItem();
        } else {
            item = get(r.id());
            if (item.getItemType() == ItemType.FIBER && type != ItemType.FIBER && blendRepository.usesFiber(item.getId())) {
                throw new IllegalStateException("Yarn blends use this fiber, so it must stay a fiber item.");
            }
        }
        item.setItemType(type);

        ItemCategory category = categories.get(required(r.categoryId(), "Category"));
        if (category.getLayer() != ItemCategory.Layer.ITEM) {
            throw new IllegalArgumentException("Items can only be filed under an item-level category.");
        }
        if (category.getItemType() != null && category.getItemType() != type) {
            throw new IllegalArgumentException("'%s' is a %s category; this item is %s."
                .formatted(category.getName(), category.getItemType().label(), type.label()));
        }
        item.setCategory(category);
        item.setBaseUnit(units.get(required(r.baseUnitId(), "Base unit")));
        item.setHsCode(r.hsCodeId() == null ? null : hsCodes.get(r.hsCodeId()));

        ItemBrand brand = r.brandId() == null ? null : brands.get(r.brandId());
        ItemModel model = r.modelId() == null ? null : models.get(r.modelId());
        if (model != null && model.getBrand() != null && (brand == null || !brand.getId().equals(model.getBrand().getId()))) {
            throw new IllegalArgumentException("Model '%s' belongs to brand '%s'."
                .formatted(model.getName(), model.getBrand().getName()));
        }
        item.setBrand(brand);
        item.setModel(model);

        applyGeneral(item, r);

        if (type == ItemType.YARN) {
            applyYarn(orgId, item, r);
        } else {
            item.clearYarn();
            item.setName(required(r.name(), "Item name"));
        }
        if (repository.nameTaken(orgId, item.getName(), item.getId())) {
            throw new IllegalArgumentException("An item named '%s' already exists.".formatted(item.getName()));
        }
        if (type == ItemType.CHEMICALS && Boolean.TRUE.equals(item.getHazardous()) && item.getSafetyDataSheet() == null) {
            throw new IllegalArgumentException("A hazardous chemical needs its safety data sheet reference.");
        }

        if (item.getId() == null) {
            item.setItemCode(numbering.next(BusinessSeries.ITEM));
        }
        return detailRow(repository.save(item));
    }

    private void applyYarn(Long orgId, InventoryItem item, ItemRequest r) {
        YarnType yarnType = yarnTypes.get(required(r.yarnTypeId(), "Yarn type"));
        YarnCount count = yarnCounts.get(required(r.yarnCountId(), "Yarn count"));
        YarnPly ply = yarnPlies.get(required(r.yarnPlyId(), "Yarn ply"));
        YarnBlend blend = yarnBlends.get(required(r.yarnBlendId(), "Yarn blend"));

        if (Boolean.TRUE.equals(item.getActive())) {
            boolean clash = repository.findYarnDuplicates(orgId, yarnType.getId(), count.getId(), ply.getId(), blend.getId())
                .stream().anyMatch(d -> !d.getId().equals(item.getId()));
            if (clash) {
                throw new IllegalArgumentException(
                    "An active yarn item with the same type, count, ply and blend already exists.");
            }
        }
        item.specifyYarn(yarnType, count, ply, blend);
        item.setQualityGrade(clean(r.qualityGrade()));
        // Yarn names are generated, never typed: "30/1 CD 60% Cotton 40% Viscose".
        item.setName(item.yarnDisplayName());
    }

    private static void applyGeneral(InventoryItem item, ItemRequest r) {
        item.setNameBn(clean(r.nameBn()));
        item.setDescription(clean(r.description()));
        item.setBarcode(clean(r.barcode()));
        item.setSku(clean(r.sku()));
        item.setActive(r.active() == null || r.active());

        item.setReorderLevel(nonNegative(r.reorderLevel(), "Reorder level"));
        item.setMinimumStock(nonNegative(r.minimumStock(), "Minimum stock"));
        item.setMaximumStock(nonNegative(r.maximumStock(), "Maximum stock"));
        if (r.minimumStock() != null && r.maximumStock() != null && r.minimumStock().compareTo(r.maximumStock()) > 0) {
            throw new IllegalArgumentException("Minimum stock cannot exceed maximum stock.");
        }
        item.setUnitPrice(nonNegative(r.unitPrice(), "Unit price"));
        item.setCostPrice(nonNegative(r.costPrice(), "Cost price"));
        item.setTaxRate(percent(r.taxRate(), "Tax rate"));

        item.setFiberType(r.fiberType());
        item.setOriginName(clean(r.originName()));
        item.setGrade(clean(r.grade()));
        item.setStapleLength(nonNegative(r.stapleLength(), "Staple length"));
        item.setMicronaire(nonNegative(r.micronaire(), "Micronaire"));
        item.setStrength(nonNegative(r.strength(), "Strength"));
        item.setMoisture(percent(r.moisture(), "Moisture"));
        item.setTrash(percent(r.trash(), "Trash"));
        item.setPurity(percent(r.purity(), "Purity"));

        item.setChemicalFormula(clean(r.chemicalFormula()));
        item.setCasNumber(clean(r.casNumber()));
        item.setHazardous(r.hazardous());
        item.setSafetyDataSheet(clean(r.safetyDataSheet()));
        item.setConcentration(percent(r.concentration(), "Concentration"));
        item.setExpiryDate(r.expiryDate());

        item.setManufacturer(clean(r.manufacturer()));
        item.setModelNumber(clean(r.modelNumber()));
        item.setSerialNumber(clean(r.serialNumber()));
        if (r.warrantyMonths() != null && r.warrantyMonths() < 0) {
            throw new IllegalArgumentException("Warranty months cannot be negative.");
        }
        item.setWarrantyMonths(r.warrantyMonths());
        item.setAssetValue(nonNegative(r.assetValue(), "Asset value"));
        item.setDepreciationRate(percent(r.depreciationRate(), "Depreciation rate"));

        item.setProcessLossPercent(percent(r.processLossPercent(), "Process loss"));
        item.setYieldPercent(percent(r.yieldPercent(), "Yield"));
        item.setStandardCostPerKg(nonNegative(r.standardCostPerKg(), "Standard cost per kg"));
        item.setSellingPricePerKg(nonNegative(r.sellingPricePerKg(), "Selling price per kg"));
    }

    @Transactional
    public Map<String, Object> approve(Long id) {
        InventoryItem item = get(id);
        item.approve(context.username(), LocalDateTime.now());
        return detailRow(repository.save(item));
    }

    @Transactional
    public void delete(Long id) {
        InventoryItem item = get(id);
        if (blendRepository.usesFiber(id)) {
            throw new IllegalStateException("Yarn blends use this fiber. Deactivate it instead.");
        }
        item.markDeleted();
        item.setActive(false);
        repository.save(item);
    }

    // ---- Rows ------------------------------------------------------------------------------------

    static Map<String, Object> row(InventoryItem i) {
        Map<String, Object> row = approvableRow(i);
        row.put("itemCode", i.getItemCode());
        row.put("name", i.getName());
        row.put("itemType", i.getItemType());
        row.put("itemTypeLabel", i.getItemType().label());
        row.put("categoryId", i.getCategory().getId());
        row.put("categoryName", i.getCategory().getName());
        row.put("baseUnitCode", i.getBaseUnit().getCode());
        row.put("baseUnitName", i.getBaseUnit().getName());
        row.put("sku", i.getSku());
        return row;
    }

    /** Everything the editor needs to repopulate itself. */
    static Map<String, Object> detailRow(InventoryItem i) {
        Map<String, Object> row = row(i);
        row.put("nameBn", i.getNameBn());
        row.put("description", i.getDescription());
        row.put("baseUnitId", i.getBaseUnit().getId());
        row.put("hsCodeId", i.getHsCode() == null ? null : i.getHsCode().getId());
        row.put("hsCode", i.getHsCode() == null ? null : i.getHsCode().getHsCode());
        row.put("brandId", i.getBrand() == null ? null : i.getBrand().getId());
        row.put("brandName", i.getBrand() == null ? null : i.getBrand().getName());
        row.put("modelId", i.getModel() == null ? null : i.getModel().getId());
        row.put("modelName", i.getModel() == null ? null : i.getModel().getName());
        row.put("barcode", i.getBarcode());

        row.put("reorderLevel", i.getReorderLevel());
        row.put("minimumStock", i.getMinimumStock());
        row.put("maximumStock", i.getMaximumStock());
        row.put("unitPrice", i.getUnitPrice());
        row.put("costPrice", i.getCostPrice());
        row.put("taxRate", i.getTaxRate());
        row.put("sellingPriceWithTax", i.sellingPriceWithTax());

        row.put("fiberType", i.getFiberType());
        row.put("originName", i.getOriginName());
        row.put("grade", i.getGrade());
        row.put("stapleLength", i.getStapleLength());
        row.put("micronaire", i.getMicronaire());
        row.put("strength", i.getStrength());
        row.put("moisture", i.getMoisture());
        row.put("trash", i.getTrash());
        row.put("purity", i.getPurity());

        row.put("yarnTypeId", i.getYarnType() == null ? null : i.getYarnType().getId());
        row.put("yarnCountId", i.getYarnCount() == null ? null : i.getYarnCount().getId());
        row.put("yarnPlyId", i.getYarnPly() == null ? null : i.getYarnPly().getId());
        row.put("yarnBlendId", i.getYarnBlend() == null ? null : i.getYarnBlend().getId());
        row.put("qualityGrade", i.getQualityGrade());

        row.put("chemicalFormula", i.getChemicalFormula());
        row.put("casNumber", i.getCasNumber());
        row.put("hazardous", i.getHazardous());
        row.put("safetyDataSheet", i.getSafetyDataSheet());
        row.put("concentration", i.getConcentration());
        row.put("expiryDate", i.getExpiryDate());

        row.put("manufacturer", i.getManufacturer());
        row.put("modelNumber", i.getModelNumber());
        row.put("serialNumber", i.getSerialNumber());
        row.put("warrantyMonths", i.getWarrantyMonths());
        row.put("assetValue", i.getAssetValue());
        row.put("depreciationRate", i.getDepreciationRate());

        row.put("processLossPercent", i.getProcessLossPercent());
        row.put("yieldPercent", i.getYieldPercent());
        row.put("standardCostPerKg", i.getStandardCostPerKg());
        row.put("sellingPricePerKg", i.getSellingPricePerKg());
        row.put("createdAt", i.getCreatedAt());
        row.put("updatedBy", i.getUpdatedBy());
        return row;
    }
}
