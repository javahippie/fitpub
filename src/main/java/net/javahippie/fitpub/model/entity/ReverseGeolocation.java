package net.javahippie.fitpub.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "gadm_410", indexes = {
        @Index(name = "gadm_410_pkey", columnList = "fid", unique = true),
        @Index(name = "gadm_410_geom_geom_idx", columnList = "geom", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReverseGeolocation {

    @Id
    @Column(name = "fid", nullable = false)
    private Integer id;

    /**
     * Country
     */
    @Column(name = "name_0")
    private String name_0;

    @Column(name = "name_1")
    private String name_1;

    @Column(name = "name_2")
    private String name_2;

    @Column(name = "name_3")
    private String name_3;

    @Column(name = "name_4")
    private String name_4;

    public String formatWithHighestResolution() {
        String country = name_0;

        if(containsText(name_4)) {
            return "%s, %s".formatted(name_4, country);
        }
        if(containsText(name_3)) {
            return "%s, %s".formatted(name_3, country);
        }
        if(containsText(name_2)) {
            return "%s, %s".formatted(name_2, country);
        }
        if(containsText(name_1)) {
            return "%s, %s".formatted(name_1, country);
        }

        return country;
    }

    private boolean containsText(String val) {
        if(null == val) {
            return false;
        }
        var trimmedValue = val.trim();
        return !trimmedValue.isBlank();
    }
}
