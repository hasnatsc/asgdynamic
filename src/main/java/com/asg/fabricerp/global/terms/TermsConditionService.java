package com.asg.fabricerp.global.terms;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.BusinessDocumentTerm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * The organization's standard clauses, and the one place a new document gets its defaults from.
 */
@Service
public class TermsConditionService {

    private final TermsConditionRepository repository;
    private final OrgContext context;

    public TermsConditionService(TermsConditionRepository repository, OrgContext context) {
        this.repository = repository;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Page<TermsCondition> search(ConditionType type, String query, Pageable pageable) {
        return repository.search(context.requireOrganizationId(), type, query, pageable);
    }

    @Transactional(readOnly = true)
    public List<TermsCondition> library(ConditionType type) {
        return repository.library(context.requireOrganizationId(), type);
    }

    /**
     * Fresh clause rows for a new document of {@code type}, numbered 1..n in the order the setup
     * screen gives them. Copies, so the document owns its wording from here on.
     */
    @Transactional(readOnly = true)
    public List<BusinessDocumentTerm> defaultTermsFor(ConditionType type) {
        List<BusinessDocumentTerm> terms = new ArrayList<>();
        int serial = 1;
        for (TermsCondition clause : repository.defaults(context.requireOrganizationId(), type)) {
            terms.add(new BusinessDocumentTerm(serial++, clause.getBodyText()));
        }
        return terms;
    }

    @Transactional(readOnly = true)
    public TermsCondition get(Long id) {
        return repository.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Terms & conditions entry not found: " + id));
    }

    @Transactional
    public TermsCondition save(TermsCondition submitted) {
        if (submitted.getId() == null) {
            return repository.save(submitted);
        }
        TermsCondition target = get(submitted.getId());
        target.setConditionType(submitted.getConditionType());
        target.setCaption(submitted.getCaption());
        target.setBodyText(submitted.getBodyText());
        target.setIsDefault(submitted.getIsDefault());
        target.setSortOrder(submitted.getSortOrder());
        target.setActive(submitted.getActive());
        return repository.save(target);
    }

    /** Soft delete. Documents keep their own copy of the wording, so nothing else changes. */
    @Transactional
    public void delete(Long id) {
        TermsCondition target = get(id);
        target.markDeleted();
        target.setActive(false);
        repository.save(target);
    }
}
