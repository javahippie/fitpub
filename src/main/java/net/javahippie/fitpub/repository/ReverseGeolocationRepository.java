package net.javahippie.fitpub.repository;

import net.javahippie.fitpub.model.entity.ReverseGeolocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ReverseGeolocationRepository  extends JpaRepository<ReverseGeolocation, Integer> {

    @Query(value = """
                   SELECT fid, name_0, name_1, name_2, name_3, name_4, name_5
                   FROM gadm_410
                   WHERE ST_Intersects(ST_SetSRID(ST_Point(:lon, :lat), 4326), geom)
                   """,
          nativeQuery = true)
    ReverseGeolocation findForLocation(double lon, double lat);
}
