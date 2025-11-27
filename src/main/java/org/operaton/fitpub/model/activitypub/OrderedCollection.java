package org.operaton.fitpub.model.activitypub;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ActivityPub OrderedCollection.
 * Used for inbox, outbox, followers, and following collections.
 *
 * Spec: https://www.w3.org/TR/activitystreams-core/#collections
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderedCollection {

    @JsonProperty("@context")
    private String context;

    private String type;
    private String id;
    private Integer totalItems;
    private String first;
    private String last;
    private List<Object> orderedItems;

    /**
     * Creates an empty OrderedCollection.
     */
    public static OrderedCollection empty(String id) {
        return OrderedCollection.builder()
            .context("https://www.w3.org/ns/activitystreams")
            .type("OrderedCollection")
            .id(id)
            .totalItems(0)
            .orderedItems(List.of())
            .build();
    }

    /**
     * Creates an OrderedCollection with items.
     */
    public static OrderedCollection of(String id, List<Object> items) {
        return OrderedCollection.builder()
            .context("https://www.w3.org/ns/activitystreams")
            .type("OrderedCollection")
            .id(id)
            .totalItems(items.size())
            .orderedItems(items)
            .build();
    }
}
