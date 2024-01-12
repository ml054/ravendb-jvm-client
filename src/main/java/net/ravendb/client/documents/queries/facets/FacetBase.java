package net.ravendb.client.documents.queries.facets;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.ravendb.client.documents.session.tokens.FacetToken;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public abstract class FacetBase {
    @JsonProperty("DisplayFieldName")
    private String displayFieldName;

    @JsonProperty("Aggregations")
    private Map<FacetAggregation, Set<FacetAggregationField>> aggregations;

    public FacetBase() {
        aggregations = new HashMap<>();
    }

    public String getDisplayFieldName() {
        return displayFieldName;
    }

    public void setDisplayFieldName(String displayFieldName) {
        this.displayFieldName = displayFieldName;
    }


    public Map<FacetAggregation, Set<FacetAggregationField>> getAggregations() {
        return aggregations;
    }

    public void setAggregations(Map<FacetAggregation, Set<FacetAggregationField>> aggregations) {
        this.aggregations = aggregations;
    }

    public abstract FacetToken toFacetToken(Function<Object, String> addQueryParameter);

}
