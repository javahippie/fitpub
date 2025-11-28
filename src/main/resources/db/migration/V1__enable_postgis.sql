-- V1: Enable PostGIS extension for geospatial support
-- This extension is required for storing GPS track data and performing spatial queries

CREATE EXTENSION IF NOT EXISTS postgis;

-- Verify PostGIS version
SELECT PostGIS_version();
