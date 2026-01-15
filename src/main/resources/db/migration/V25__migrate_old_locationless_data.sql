UPDATE activities a
SET activity_location =
        concat(
                COALESCE(
                        NULLIF(g.name_4, ''),
                        NULLIF(g.name_3, ''),
                        NULLIF(g.name_2, '')
                ),
                ', ',
                g.name_0
        )
    FROM gadm_410 g
WHERE a.activity_location IS NULL
  AND a.simplified_track IS NOT NULL
  AND ST_Intersects(
    g.geom,
    ST_SetSRID(ST_StartPoint(a.simplified_track), 4326)
    );