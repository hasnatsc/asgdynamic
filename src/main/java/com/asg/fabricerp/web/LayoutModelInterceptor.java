package com.asg.fabricerp.web;

import com.asg.fabricerp.common.BusinessUnit;
import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.Warehouse;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.security.CurrentUser;
import com.asg.fabricerp.security.FabricUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UrlPathHelper;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Supplies what {@code layout/main.html} renders around every page: the sidebar, the operating
 * context (unit / store) and the signed-in user.
 *
 * <p>An interceptor rather than a {@code @ModelAttribute} advice on purpose: advice methods run
 * before <em>every</em> handler, including each JSON grid call, and would look up the unit and
 * store names for a response that never renders them. {@code postHandle} sees the chosen view,
 * so the lookups happen only when the layout is actually about to be drawn.
 */
@Component
public class LayoutModelInterceptor implements HandlerInterceptor {

    static final String LAYOUT_VIEW = "layout/main";

    /** Unit and store as shown in the header. Names are null when the id resolves to nothing. */
    public record OperatingContext(String businessUnitCode, String businessUnitName, String storeName) { }

    private final BusinessUnitRepository businessUnits;
    private final WarehouseRepository warehouses;

    public LayoutModelInterceptor(BusinessUnitRepository businessUnits, WarehouseRepository warehouses) {
        this.businessUnits = businessUnits;
        this.warehouses = warehouses;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView mav) {
        if (mav == null || !LAYOUT_VIEW.equals(mav.getViewName())) {
            return;
        }
        String currentPath = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        mav.addObject("currentPath", currentPath);

        CurrentUser.principal().ifPresent(principal -> {
            Set<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
            mav.addObject("nav", Navigation.build(authorities, currentPath));
            mav.addObject("currentUser", principal);
            mav.addObject("operatingContext", operatingContext(principal));
        });
    }

    private OperatingContext operatingContext(FabricUserPrincipal principal) {
        String unitName = principal.getBusinessUnitId() == null ? null
            : businessUnits.findById(principal.getBusinessUnitId()).map(BusinessUnit::getName).orElse(null);
        String storeName = principal.getWarehouseId() == null ? null
            : warehouses.findById(principal.getWarehouseId()).map(Warehouse::getName).orElse(null);
        return new OperatingContext(principal.getBusinessUnitCode(), unitName, storeName);
    }
}
