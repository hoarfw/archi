/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.read;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IProperty;

/**
 * Shared filtering, sorting and paging substrate for concept queries.
 */
@SuppressWarnings("nls")
public class ConceptQuerySupport {

    private static final String SEARCH_NAME = "name";
    private static final String SEARCH_DOCUMENTATION = "documentation";
    private static final String SEARCH_PROPERTY_VALUE = "propertyValue";

    public <C extends IArchimateConcept> QueryResultPage<Map<String, Object>> query(MCPQueryRequestView request,
            Collection<C> concepts, Function<C, Map<String, Object>> conceptMapper,
            Function<C, List<Map<String, Object>>> viewContextMapper) {
        if(request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        if(concepts == null) {
            throw new IllegalArgumentException("concepts must not be null");
        }

        if(conceptMapper == null) {
            throw new IllegalArgumentException("conceptMapper must not be null");
        }

        if(viewContextMapper == null) {
            throw new IllegalArgumentException("viewContextMapper must not be null");
        }

        FilterState filterState = FilterState.from(request);
        List<MatchedConcept<C>> matches = new ArrayList<>();

        for(C concept : concepts) {
            List<Map<String, Object>> mappedViews = filterAndSortViews(viewContextMapper.apply(concept), filterState.viewIds);

            if(!matchesConcept(concept, filterState, mappedViews)) {
                continue;
            }

            matches.add(new MatchedConcept<>(concept, mappedViews));
        }

        matches.sort(Comparator.comparing(match -> safeString(match.concept.getId())));

        int total = matches.size();
        int start = Math.min(request.getOffset(), total);
        int end = Math.min(start + request.getLimit(), total);
        List<Map<String, Object>> items = new ArrayList<>(Math.max(0, end - start));

        for(int i = start; i < end; i++) {
            MatchedConcept<C> match = matches.get(i);
            Map<String, Object> item = new LinkedHashMap<>(conceptMapper.apply(match.concept));
            item.put("views", List.copyOf(match.views));
            items.add(item);
        }

        return new QueryResultPage<>(items, total, request.getOffset(), request.getLimit(), end < total);
    }

    public static String toTypeToken(EObject object) {
        if(object == null || object.eClass() == null) {
            return ""; //$NON-NLS-1$
        }

        return toTypeToken(object.eClass().getName());
    }

    public static String toTypeToken(String typeName) {
        if(typeName == null || typeName.isBlank()) {
            return ""; //$NON-NLS-1$
        }

        StringBuilder builder = new StringBuilder(typeName.length() + 8);

        for(int i = 0; i < typeName.length(); i++) {
            char ch = typeName.charAt(i);

            if(Character.isUpperCase(ch) && i > 0) {
                builder.append('-');
            }

            builder.append(Character.toLowerCase(ch));
        }

        return builder.toString();
    }

    private <C extends IArchimateConcept> boolean matchesConcept(C concept, FilterState filterState,
            List<Map<String, Object>> mappedViews) {
        if(concept == null) {
            return false;
        }

        if(!filterState.types.isEmpty() && !filterState.types.contains(toTypeToken(concept))) {
            return false;
        }

        if(!filterState.viewIds.isEmpty() && mappedViews.isEmpty()) {
            return false;
        }

        return matchesTextAndProperties(concept, filterState);
    }

    private boolean matchesTextAndProperties(IArchimateConcept concept, FilterState filterState) {
        if(filterState.text == null) {
            return filterState.propertyKeys.isEmpty() || hasMatchingPropertyKey(concept, filterState.propertyKeys);
        }

        if(filterState.searchIn.contains(SEARCH_NAME) && containsIgnoreCase(concept.getName(), filterState.text)) {
            return true;
        }

        if(filterState.searchIn.contains(SEARCH_DOCUMENTATION)
                && containsIgnoreCase(concept.getDocumentation(), filterState.text)) {
            return true;
        }

        if(filterState.searchIn.contains(SEARCH_PROPERTY_VALUE)
                && hasMatchingPropertyValue(concept, filterState.propertyKeys, filterState.text)) {
            return true;
        }

        return false;
    }

    private boolean hasMatchingPropertyKey(IArchimateConcept concept, Set<String> propertyKeys) {
        for(IProperty property : concept.getProperties()) {
            if(propertyKeys.contains(property.getKey())) {
                return true;
            }
        }

        return false;
    }

    private boolean hasMatchingPropertyValue(IArchimateConcept concept, Set<String> propertyKeys, String text) {
        for(IProperty property : concept.getProperties()) {
            if(!propertyKeys.isEmpty() && !propertyKeys.contains(property.getKey())) {
                continue;
            }

            if(containsIgnoreCase(property.getValue(), text)) {
                return true;
            }
        }

        return false;
    }

    private List<Map<String, Object>> filterAndSortViews(List<Map<String, Object>> views, Set<String> requestedViewIds) {
        List<Map<String, Object>> filtered = new ArrayList<>();

        if(views != null) {
            for(Map<String, Object> view : views) {
                if(view == null) {
                    continue;
                }

                String viewId = safeString(view.get("viewId"));
                if(!requestedViewIds.isEmpty() && !requestedViewIds.contains(viewId)) {
                    continue;
                }

                filtered.add(view);
            }
        }

        filtered.sort(Comparator
                .comparing((Map<String, Object> view) -> safeString(view.get("viewId")))
                .thenComparing(view -> safeString(view.get("objectId")))
                .thenComparing(view -> safeString(view.get("connectionId"))));

        return List.copyOf(filtered);
    }

    private boolean containsIgnoreCase(String value, String text) {
        if(value == null || value.isEmpty() || text == null || text.isEmpty()) {
            return false;
        }

        return value.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
    }

    private static String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static final class MatchedConcept<C extends IArchimateConcept> {
        private final C concept;
        private final List<Map<String, Object>> views;

        private MatchedConcept(C concept, List<Map<String, Object>> views) {
            this.concept = concept;
            this.views = views;
        }
    }

    private static final class FilterState {
        private final String text;
        private final List<String> searchIn;
        private final Set<String> types;
        private final Set<String> propertyKeys;
        private final Set<String> viewIds;

        private FilterState(String text, List<String> searchIn, Set<String> types, Set<String> propertyKeys,
                Set<String> viewIds) {
            this.text = text;
            this.searchIn = searchIn;
            this.types = types;
            this.propertyKeys = propertyKeys;
            this.viewIds = viewIds;
        }

        private static FilterState from(MCPQueryRequestView request) {
            Map<String, Object> filters = request.getFilters();
            String text = (String)filters.get("text");
            List<String> rawSearchIn = getListFilter(filters, "searchIn");
            List<String> searchIn = text != null && rawSearchIn.isEmpty() ? List.of(SEARCH_NAME) : rawSearchIn;
            Set<String> types = canonicalizeTypes(getListFilter(filters, "types"));
            Set<String> propertyKeys = new LinkedHashSet<>(getListFilter(filters, "propertyKeys"));
            Set<String> viewIds = new LinkedHashSet<>(getListFilter(filters, "viewIds"));
            return new FilterState(text, List.copyOf(searchIn), Set.copyOf(types), Set.copyOf(propertyKeys),
                    Set.copyOf(viewIds));
        }

        @SuppressWarnings("unchecked")
        private static List<String> getListFilter(Map<String, Object> filters, String key) {
            Object value = filters.get(key);
            if(value == null) {
                return List.of();
            }

            return List.copyOf((List<String>)value);
        }

        private static Set<String> canonicalizeTypes(List<String> filterTypes) {
            Set<String> canonical = new LinkedHashSet<>();

            for(String filterType : filterTypes) {
                canonical.add(toTypeToken(filterType));
            }

            return canonical;
        }
    }

    /**
     * Minimal request view so the shared substrate stays reusable outside the contract package.
     */
    public interface MCPQueryRequestView {
        Map<String, Object> getFilters();

        int getLimit();

        int getOffset();
    }
}
