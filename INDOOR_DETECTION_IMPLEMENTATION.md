# Indoor Activity Detection - Implementation Summary

## Overview

Implemented multi-format indoor activity detection with retroactive migration support. Indoor activities (trainer rides, treadmill runs, virtual activities) are now:
- ✅ **Visible in timeline** with full GPS visualization
- ✅ **Excluded from heatmap** to prevent pollution of outdoor activity maps
- ✅ **Automatically detected** from FIT SubSport field or GPS heuristics

---

## Database Changes

### Migration V20 (Already Applied)
- Added `indoor` boolean column (defaults to FALSE)
- Created index `idx_activity_indoor` for efficient heatmap queries

### Migration V21 (New - Just Applied)
- Added `sub_sport` VARCHAR(50) - SubSport from FIT files
- Added `indoor_detection_method` VARCHAR(20) - How indoor flag was determined

### Schema Columns

| Column | Type | Purpose |
|---|---|---|
| `indoor` | BOOLEAN NOT NULL | Main flag - exclude from heatmap if TRUE |
| `sub_sport` | VARCHAR(50) NULL | FIT SubSport field (e.g., INDOOR_CYCLING, ROAD, TRAIL) |
| `indoor_detection_method` | VARCHAR(20) NULL | Detection method enum value |

---

## Detection Methods

### 1. FIT Files - SubSport Field (Most Accurate)
**Method**: `FIT_SUBSPORT`

Reads the SubSport field from FIT file session message and detects:
- `INDOOR_CYCLING` → Indoor
- `TREADMILL` → Indoor
- `VIRTUAL_ACTIVITY` → Indoor (Zwift, RGT, etc.)
- `TRAINER` → Indoor
- `ROAD`, `MOUNTAIN`, `TRAIL` → Outdoor

**Implementation**: `FitParser.extractSessionData()` (lines 332-348)

### 2. FIT Files - No GPS Data
**Method**: `HEURISTIC_NO_GPS`

If FIT file has no GPS track points → marked as indoor.

**Implementation**: `FitParser.parse()` (lines 111-120)

### 3. GPX Files - Stationary GPS
**Method**: `HEURISTIC_STATIONARY`

If all GPS points are within 50 meters of first point → marked as indoor.
Detects trainer rides or treadmill runs with GPS enabled (phone/tablet).

**Implementation**: `GpxParser.detectIndoorActivity()` + `isStationaryGps()` (lines 627-673)

### 4. GPX Files - No GPS Data
**Method**: `HEURISTIC_NO_GPS`

If GPX file has no GPS track points → marked as indoor.

**Implementation**: `GpxParser.detectIndoorActivity()` (lines 630-634)

### 5. Manual Override (Future)
**Method**: `MANUAL`

User can manually flag activity as indoor (UI not yet implemented).

---

## How It Works

### For NEW Uploads (After This Feature)

#### FIT File Upload:
1. User uploads FIT file
2. `FitParser.extractSessionData()` reads SubSport field
3. Sets `parsedData.setSubSport("INDOOR_CYCLING")` (example)
4. Detects indoor keywords → sets `parsedData.setIndoor(true)`
5. Sets `parsedData.setIndoorDetectionMethod(FIT_SUBSPORT)`
6. `ActivityFileService` saves to database with all fields populated
7. **Done** - no re-parsing needed

#### GPX File Upload:
1. User uploads GPX file
2. `GpxParser.detectIndoorActivity()` analyzes GPS points
3. Checks if all points within 50m radius
4. Sets `parsedData.setIndoor(true)` if stationary
5. Sets `parsedData.setIndoorDetectionMethod(HEURISTIC_STATIONARY)`
6. `ActivityFileService` saves to database
7. **Done** - no re-parsing needed

### For Timeline Loading

**Fast database query** - no file parsing:
```sql
SELECT id, activity_type, title, indoor, sub_sport,
       indoor_detection_method, ...
FROM activities
WHERE ...
```

### For Heatmap Generation

**Automatically excludes indoor activities**:

Updated queries in `UserHeatmapGridRepository`:
```sql
-- Single activity update
WHERE a.id = :activityId
  AND a.track_points_json IS NOT NULL
  AND a.indoor = FALSE  -- NEW

-- Full user recalculation
WHERE a.user_id = :userId
  AND a.track_points_json IS NOT NULL
  AND a.indoor = FALSE  -- NEW
```

---

## Retroactive Migration

### For Activities Uploaded BEFORE This Feature

**Run once** to populate indoor flags for existing data:

```bash
POST /api/admin/migrate-indoor-flags
Authorization: Bearer <your-jwt-token>
```

This endpoint:
1. Fetches all FIT activities with stored raw files
2. Re-parses FIT files to extract SubSport
3. Updates `indoor`, `sub_sport`, and `indoor_detection_method`
4. Only saves if values changed (idempotent)

**Response**:
```json
{
  "message": "Indoor activity flag migration complete",
  "activitiesUpdated": 15
}
```

### How to Run Migration

#### Option 1: Using Browser DevTools
1. Login to FitPub
2. Open DevTools (F12) → Application → Local Storage
3. Copy `jwt_token` value
4. Use browser fetch or Postman:
```javascript
fetch('http://localhost:8080/api/admin/migrate-indoor-flags', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer YOUR_TOKEN_HERE'
  }
}).then(r => r.json()).then(console.log);
```

#### Option 2: Using curl
```bash
curl -X POST \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  http://localhost:8080/api/admin/migrate-indoor-flags
```

---

## Code Structure

### Key Files Modified

1. **Database**:
   - `V20__add_indoor_flag_to_activities.sql` - Indoor flag column
   - `V21__add_indoor_detection_metadata.sql` - SubSport & detection method columns

2. **Entity**:
   - `Activity.java` - Added `subSport`, `indoorDetectionMethod` fields
   - `Activity.IndoorDetectionMethod` - New enum for detection methods

3. **Parsers**:
   - `FitParser.java` - Extract SubSport, detect indoor from FIT files
   - `GpxParser.java` - Heuristic detection with GPS analysis (Haversine distance)
   - `ParsedActivityData.java` - Added fields for parsed data transfer

4. **Service**:
   - `ActivityFileService.java` - Save SubSport & detection method to database
   - `IndoorActivityMigrationService.java` - Retroactive migration logic

5. **Repository**:
   - `UserHeatmapGridRepository.java` - Exclude indoor activities from heatmap queries
   - `ActivityRepository.java` - Added query method for migration

6. **Controller**:
   - `AdminController.java` - Migration endpoint
   - `SecurityConfig.java` - Added `/api/admin/**` route (authenticated)

---

## Testing

### Test Indoor Detection

#### 1. Upload Zwift FIT File
Expected:
- `indoor = TRUE`
- `sub_sport = "VIRTUAL_ACTIVITY"`
- `indoor_detection_method = "FIT_SUBSPORT"`
- Visible in timeline ✅
- Not in heatmap ✅

#### 2. Upload Treadmill FIT File
Expected:
- `indoor = TRUE`
- `sub_sport = "TREADMILL"`
- `indoor_detection_method = "FIT_SUBSPORT"`

#### 3. Upload GPX with Stationary GPS
Expected:
- `indoor = TRUE`
- `sub_sport = NULL` (GPX doesn't have SubSport)
- `indoor_detection_method = "HEURISTIC_STATIONARY"`

#### 4. Upload Outdoor Ride FIT
Expected:
- `indoor = FALSE`
- `sub_sport = "ROAD"` or `"MOUNTAIN"`
- `indoor_detection_method = NULL` (not indoor)

---

## Performance

- **New uploads**: SubSport extracted during normal parsing (no overhead)
- **Timeline loading**: Simple column read (instant)
- **Heatmap queries**: Added `AND indoor = FALSE` filter (uses index)
- **Migration**: One-time operation, only re-parses FIT files with raw data

---

## Future Enhancements

### Phase 2 (Optional):
1. **GPX Extension Parsing**: Read Garmin/Strava custom XML extensions
2. **Manual Override UI**: Allow users to manually mark activities as indoor
3. **Activity Edit**: Update indoor flag via activity edit form
4. **Statistics**: Show "X indoor activities excluded from heatmap" message

---

## Database Query Examples

### Find All Indoor Activities
```sql
SELECT id, title, activity_type, sub_sport, indoor_detection_method
FROM activities
WHERE indoor = TRUE;
```

### Find FIT Activities with SubSport
```sql
SELECT id, title, sub_sport
FROM activities
WHERE sub_sport IS NOT NULL
ORDER BY created_at DESC;
```

### Count Detection Methods
```sql
SELECT indoor_detection_method, COUNT(*) as count
FROM activities
WHERE indoor = TRUE
GROUP BY indoor_detection_method;
```

---

## Summary

✅ **Fully implemented** multi-format indoor detection
✅ **Backward compatible** - existing activities default to outdoor
✅ **Retroactive migration** endpoint for old data
✅ **Heatmap exclusion** automatic via SQL filters
✅ **Timeline display** includes all activities
✅ **Works for FIT and GPX** files with different detection strategies
✅ **Type-safe** detection method enum
✅ **Well documented** with inline comments

The solution is production-ready and scales to all supported file formats!
