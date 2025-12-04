# FitPub - Federated Fitness Tracking Platform

## Project Overview

FitPub is a decentralized fitness tracking application that integrates with the Fediverse through the ActivityPub protocol. It allows users to upload FIT (Flexible and Interoperable Data Transfer) files from their fitness devices and share their activities with followers across the federated social web. The application renders GPS tracks on interactive maps and federates workout data as ActivityPub activities.

## Core Concept

The platform bridges the gap between fitness tracking and social networking by leveraging the open ActivityPub standard. Users can:
- Upload FIT files from GPS-enabled fitness devices (Garmin, Wahoo, etc.)
- View their tracks rendered on interactive maps
- Share activities with followers on Mastodon, Pleroma, and other Fediverse platforms
- Follow other athletes and see their public workouts
- Maintain full data ownership and privacy control

## Technical Architecture

### Technology Stack

**Backend:**
- Java 17+ (LTS version)
- Maven for dependency management and build automation
- Spring Boot 4 for application framework
- Spring Web MVC for REST API
- Spring Data JPA for database operations
- Spring Security for authentication and authorization
- PostgreSQL for primary data storage
- PostGIS extension for geospatial data

**Frontend:**
- Thymeleaf or React for UI rendering
- Leaflet.js for interactive map display
- Chart.js for activity statistics visualization
- Bootstrap or Tailwind CSS for responsive design

**Protocols & Standards:**
- ActivityPub (W3C Recommendation)
- WebFinger (RFC 7033) for actor discovery
- HTTP Signatures for authenticated federation
- JSON-LD for linked data representation
- GeoJSON for geographic data interchange

### System Components

#### 1. FIT File Processing Module

**Responsibilities:**
- Parse binary FIT files uploaded by users
- Extract GPS coordinates (latitude, longitude, elevation)
- Parse activity metrics (heart rate, cadence, power, speed, distance)
- Validate file integrity and format
- Store parsed data in normalized database schema

**Key Classes:**
- `FitFileParser`: Core parsing logic using FIT SDK
- `TrackPointEntity`: Database entity for GPS coordinates
- `ActivityMetricsEntity`: Database entity for performance data
- `FitFileValidator`: Validation and sanitization

#### 2. ActivityPub Federation Module

**Responsibilities:**
- Implement ActivityPub server-to-server (S2S) protocol
- Implement ActivityPub client-to-server (C2S) protocol
- Handle incoming activities from other servers
- Distribute local activities to followers' servers
- Manage actor profiles and collections

**Key Components:**

**Actor Model:**
```
Actor (User Profile)
├── inbox: OrderedCollection
├── outbox: OrderedCollection
├── followers: Collection
├── following: Collection
└── publicKey: For HTTP signature verification
```

**Activity Types:**
- `Create`: New workout activity posted
- `Update`: Activity edited (title, description, privacy)
- `Delete`: Activity removed
- `Follow`: User follows another athlete
- `Accept`: Follow request accepted
- `Announce`: Sharing/boosting another user's activity
- `Like`: Appreciating someone's workout

**Endpoints:**
- `/.well-known/webfinger`: User discovery
- `/users/{username}`: Actor profile (ActivityPub object)
- `/users/{username}/inbox`: Receive activities (POST)
- `/users/{username}/outbox`: User's activities (GET)
- `/users/{username}/followers`: Followers collection
- `/users/{username}/following`: Following collection
- `/activities/{id}`: Individual activity objects

#### 3. Geospatial Data Module

**Responsibilities:**
- Store GPS track data efficiently
- Generate map-ready GeoJSON from track points
- Calculate route statistics (distance, elevation gain/loss)
- Support spatial queries (nearby activities, route matching)
- Render track simplification for performance

**Data Structure:**
```
Activity
├── id: UUID
├── user: Actor reference
├── activityType: (Run, Ride, Hike, Swim, etc.)
├── startTime: Timestamp
├── endTime: Timestamp
├── title: String
├── description: Text
├── visibility: (Public, Followers, Private)
├── track: LineString (PostGIS geometry)
├── metrics: JSON (distance, duration, avg_speed, etc.)
└── statistics: JSON (elevation profile, splits, etc.)
```

**GeoJSON Output Format:**
```json
{
  "type": "FeatureCollection",
  "features": [{
    "type": "Feature",
    "geometry": {
      "type": "LineString",
      "coordinates": [[lon, lat, elevation], ...]
    },
    "properties": {
      "time": "ISO-8601 timestamp",
      "heartRate": 145,
      "cadence": 85,
      "speed": 4.5
    }
  }]
}
```

#### 4. Web API Module

**REST Endpoints:**

**Activity Management:**
- `POST /api/activities/upload`: Upload FIT file
- `GET /api/activities/{id}`: Retrieve activity details
- `GET /api/activities/{id}/track`: Get GeoJSON track data
- `PUT /api/activities/{id}`: Update activity metadata
- `DELETE /api/activities/{id}`: Remove activity
- `GET /api/activities`: List user's activities (paginated)

**User Management:**
- `POST /api/users/register`: Create new account
- `GET /api/users/{username}`: Public profile
- `GET /api/users/{username}/activities`: User's public activities
- `PUT /api/users/profile`: Update profile information

**Social Features:**
- `POST /api/follow/{username}`: Follow a user
- `DELETE /api/follow/{username}`: Unfollow
- `GET /api/timeline`: Federated timeline of followed users
- `POST /api/activities/{id}/like`: Like an activity
- `POST /api/activities/{id}/comment`: Comment on activity

#### 5. Authentication & Authorization

**Implementation:**
- JWT tokens for session management
- OAuth 2.0 for third-party integrations (optional)
- HTTP Signatures (required for ActivityPub federation)
- RSA key pairs per user for signature verification

**Security Considerations:**
- Password hashing with bcrypt
- Rate limiting on API endpoints
- CORS configuration for frontend
- Content-Security-Policy headers
- Input validation and sanitization
- Protection against SSRF in federation

## Database Schema

### Core Tables

**users**
- id (UUID, PK)
- username (VARCHAR, UNIQUE)
- email (VARCHAR, UNIQUE)
- password_hash (VARCHAR)
- display_name (VARCHAR)
- bio (TEXT)
- avatar_url (VARCHAR)
- public_key (TEXT)
- private_key (TEXT, encrypted)
- created_at (TIMESTAMP)
- updated_at (TIMESTAMP)

**activities**
- id (UUID, PK)
- user_id (UUID, FK → users)
- activity_type (VARCHAR)
- title (VARCHAR)
- description (TEXT)
- started_at (TIMESTAMP)
- ended_at (TIMESTAMP)
- visibility (VARCHAR)
- track (GEOMETRY LineString, PostGIS)
- total_distance (DECIMAL)
- total_duration (INTERVAL)
- elevation_gain (DECIMAL)
- elevation_loss (DECIMAL)
- raw_fit_file (BYTEA, optional)
- created_at (TIMESTAMP)
- updated_at (TIMESTAMP)

**track_points**
- id (BIGSERIAL, PK)
- activity_id (UUID, FK → activities)
- timestamp (TIMESTAMP)
- position (GEOMETRY Point, PostGIS)
- elevation (DECIMAL)
- heart_rate (INTEGER)
- cadence (INTEGER)
- power (INTEGER)
- speed (DECIMAL)
- temperature (DECIMAL)

**follows**
- id (UUID, PK)
- follower_id (UUID, FK → users)
- following_id (UUID, FK → users OR remote_actor_id)
- status (VARCHAR: pending, accepted)
- created_at (TIMESTAMP)

**remote_actors**
- id (UUID, PK)
- actor_uri (VARCHAR, UNIQUE)
- username (VARCHAR)
- domain (VARCHAR)
- inbox_url (VARCHAR)
- outbox_url (VARCHAR)
- public_key (TEXT)
- avatar_url (VARCHAR)
- display_name (VARCHAR)
- last_fetched (TIMESTAMP)

**activity_pub_activities**
- id (UUID, PK)
- activity_type (VARCHAR)
- actor_id (UUID)
- object_id (VARCHAR)
- target_id (VARCHAR)
- content (JSONB)
- created_at (TIMESTAMP)

**likes**
- id (UUID, PK)
- activity_id (UUID, FK → activities)
- user_id (UUID, FK → users OR remote_actor_id)
- created_at (TIMESTAMP)

**comments**
- id (UUID, PK)
- activity_id (UUID, FK → activities)
- user_id (UUID, FK → users OR remote_actor_id)
- content (TEXT)
- created_at (TIMESTAMP)
- updated_at (TIMESTAMP)

## ActivityPub Integration Details

### Actor Object Example

```json
{
  "@context": [
    "https://www.w3.org/ns/activitystreams",
    "https://w3id.org/security/v1"
  ],
  "type": "Person",
  "id": "https://fitpub.example/users/runner123",
  "preferredUsername": "runner123",
  "name": "Jane Runner",
  "summary": "Marathon runner | Trail enthusiast | 🏃‍♀️",
  "inbox": "https://fitpub.example/users/runner123/inbox",
  "outbox": "https://fitpub.example/users/runner123/outbox",
  "followers": "https://fitpub.example/users/runner123/followers",
  "following": "https://fitpub.example/users/runner123/following",
  "publicKey": {
    "id": "https://fitpub.example/users/runner123#main-key",
    "owner": "https://fitpub.example/users/runner123",
    "publicKeyPem": "-----BEGIN PUBLIC KEY-----\n..."
  },
  "icon": {
    "type": "Image",
    "mediaType": "image/jpeg",
    "url": "https://fitpub.example/avatars/runner123.jpg"
  }
}
```

### Activity Object Example (Workout Post)

```json
{
  "@context": "https://www.w3.org/ns/activitystreams",
  "type": "Create",
  "id": "https://fitpub.example/activities/create/12345",
  "actor": "https://fitpub.example/users/runner123",
  "published": "2025-11-27T10:30:00Z",
  "to": ["https://www.w3.org/ns/activitystreams#Public"],
  "cc": ["https://fitpub.example/users/runner123/followers"],
  "object": {
    "type": "Note",
    "id": "https://fitpub.example/workouts/98765",
    "attributedTo": "https://fitpub.example/users/runner123",
    "content": "Morning 10K run through the park! Felt strong today. 💪",
    "published": "2025-11-27T10:30:00Z",
    "attachment": [
      {
        "type": "Document",
        "mediaType": "application/geo+json",
        "name": "GPS Track",
        "url": "https://fitpub.example/workouts/98765/track.geojson"
      },
      {
        "type": "Image",
        "mediaType": "image/png",
        "name": "Route Map",
        "url": "https://fitpub.example/workouts/98765/map.png"
      }
    ],
    "tag": [
      {
        "type": "Hashtag",
        "name": "#running"
      },
      {
        "type": "Hashtag",
        "name": "#10k"
      }
    ],
    "summary": "10.2 km • 48:23 • 4:44/km pace",
    "workoutData": {
      "distance": 10200,
      "duration": "PT48M23S",
      "activityType": "Run",
      "averagePace": "PT4M44S",
      "elevationGain": 127,
      "averageHeartRate": 152
    }
  }
}
```

### WebFinger Implementation

**Request:**
```
GET /.well-known/webfinger?resource=acct:runner123@fitpub.example
```

**Response:**
```json
{
  "subject": "acct:runner123@fitpub.example",
  "aliases": [
    "https://fitpub.example/users/runner123"
  ],
  "links": [
    {
      "rel": "self",
      "type": "application/activity+json",
      "href": "https://fitpub.example/users/runner123"
    },
    {
      "rel": "http://webfinger.net/rel/profile-page",
      "type": "text/html",
      "href": "https://fitpub.example/@runner123"
    }
  ]
}
```

## FIT File Processing Pipeline

### Parse Flow

1. **Upload Validation**
    - Verify file size (max 50MB)
    - Check MIME type
    - Validate FIT file header

2. **Parsing**
    - Use FIT SDK to decode binary format
    - Extract messages: FileId, Record, Lap, Session, Activity
    - Handle corrupted or incomplete files gracefully

3. **Data Extraction**
    - **Record Messages**: GPS coordinates, timestamp, heart rate, cadence, speed, power
    - **Lap Messages**: Split data, lap times, lap distances
    - **Session Messages**: Total distance, total time, average/max values
    - **Activity Messages**: Activity type, timestamp

4. **Data Transformation**
    - Convert semicircles to decimal degrees (lat/lon)
    - Calculate derived metrics (pace, grade-adjusted pace)
    - Detect pauses and stopped periods
    - Smooth GPS noise using Kalman filtering (optional)

5. **Storage**
    - Batch insert track points (optimize for performance)
    - Create PostGIS LineString geometry from points
    - Calculate bounding box for spatial indexing
    - Generate activity statistics

6. **Map Rendering Preparation**
    - Simplify track using Douglas-Peucker algorithm for web display
    - Generate elevation profile data
    - Create thumbnail static map image (optional)
    - Prepare GeoJSON response

## Privacy & Visibility Controls

### Visibility Levels

1. **Public**: Visible to everyone, federated across ActivityPub
2. **Followers Only**: Visible to approved followers, sent to follower inboxes
3. **Private**: Visible only to the user, not federated

### Privacy Features

- Ability to hide exact start/end locations (fuzzy start/finish)
- Option to exclude specific segments from public view
- Bulk privacy updates for historical activities
- Export all personal data (GDPR compliance)
- Delete account with activity cleanup

## Map Rendering

### Frontend Map Stack

**Leaflet.js Configuration:**
- Base layer: OpenStreetMap tiles
- Alternative layers: Satellite, Terrain (Thunderforest, Mapbox)
- Custom track overlay as GeoJSON layer
- Markers for start (green) and finish (red)
- Popup markers for lap splits
- Heatmap overlay for intensity (heart rate zones)

**Interactive Features:**
- Click on track to see point-in-time metrics
- Elevation profile chart synchronized with map
- Segment highlighting
- Playback animation of activity
- Compare multiple activities on same map

### Static Map Generation

For ActivityPub federated posts and thumbnails:
- Use StaticMap library or external service
- Generate PNG/JPEG preview of route
- Include in ActivityPub attachment
- Cache generated images

## Federation Workflow Examples

### Scenario 1: User Posts New Activity

1. User uploads FIT file via web UI
2. Backend parses file and stores data
3. User adds title, description, and sets visibility to "Public"
4. System creates ActivityPub `Create` activity
5. Activity is added to user's outbox
6. System retrieves list of followers
7. For each remote follower:
    - Sign HTTP request with user's private key
    - POST activity to follower's server inbox
8. Remote servers receive and process the activity
9. Activity appears in followers' timelines on their platforms

### Scenario 2: Remote User Follows Local User

1. Remote user (on Mastodon) clicks "Follow" on local user's profile
2. Mastodon server sends `Follow` activity to local user's inbox
3. FitPub receives and validates the activity
4. FitPub creates `Accept` activity in response
5. FitPub stores the follow relationship
6. FitPub sends `Accept` to remote server's inbox
7. Follow relationship is now active
8. Future public activities will be sent to remote follower

### Scenario 3: Remote User Likes Local Activity

1. Remote user views local activity on their platform
2. User clicks "Like" or equivalent action
3. Remote server sends `Like` activity to local inbox
4. FitPub receives and processes the like
5. Like count is incremented and stored
6. Like appears on activity page
7. Original poster may receive notification

## Development Roadmap

### Phase 1: MVP (Minimum Viable Product)

**System Component 1: FIT File Processing Module** ✅
- [x] FIT file upload and parsing (FitParser)
- [x] FIT file validation (FitFileValidator)
- [x] Activity entity with JSONB track points and simplified LineString
- [x] Activity metrics extraction and storage
- [x] Track simplification using Douglas-Peucker algorithm
- [x] FIT file service with comprehensive tests
- [x] Integration test with real FIT file

**User Management & Security** ✅
- [x] User entity with ActivityPub keys
- [x] UserRepository with custom queries
- [x] Password hashing with BCrypt
- [x] JWT token provider for session management
- [x] HTTP Signature validator for ActivityPub federation
- [x] UserDetailsService implementation
- [x] Security configuration (Spring Security)
- [x] User registration endpoint (POST /api/auth/register)
- [x] Login endpoint with JWT response (POST /api/auth/login)

**Application Infrastructure** ✅
- [x] Main application class (FitPubApplication.java)
- [x] Application configuration (application.yml)
- [x] Database configuration (PostgreSQL + PostGIS with Testcontainers Dev Services)
- [x] CORS configuration for frontend
- [x] Exception handling (global error handlers)
- [x] Logging configuration
- [x] Profile-specific configs (application-dev.yml, application-prod.yml)

**Activity REST API** ✅
- [x] POST /api/activities/upload - Upload FIT file
- [x] GET /api/activities/{id} - Get activity details
- [x] GET /api/activities - List user's activities (paginated)
- [x] PUT /api/activities/{id} - Update activity metadata
- [x] DELETE /api/activities/{id} - Delete activity
- [x] Activity DTOs for API responses
- [x] All endpoints tested and working

**ActivityPub Actor Profile** ✅
- [x] Actor model classes (Person, PublicKey)
- [x] GET /users/{username} - Actor profile endpoint
- [x] Actor JSON-LD serialization with @context
- [x] Public key embedding in actor profile
- [x] Profile metadata (name, bio, avatar)
- [x] Tested with application/activity+json and application/ld+json

**WebFinger Support** ✅
- [x] WebFinger controller
- [x] GET /.well-known/webfinger - User discovery
- [x] WebFinger response DTO
- [x] Account identifier parsing (acct:user@domain)
- [x] Tested with valid and invalid requests

**ActivityPub Collections** ✅
- [x] POST /users/{username}/inbox - Inbox endpoint (accepts activities with 202 Accepted)
- [x] GET /users/{username}/outbox - Outbox endpoint (returns empty OrderedCollection)
- [x] GET /users/{username}/followers - Followers collection (returns empty OrderedCollection)
- [x] GET /users/{username}/following - Following collection (returns empty OrderedCollection)
- [x] OrderedCollection model classes
- [x] Basic collection structure (TODOs exist for populating with actual data)
- [x] All endpoints tested and working

**Basic Federation** ✅
- [x] Federation service for outbound activities (FederationService.java)
- [x] HTTP signature signing for outbound requests (signRequest method in HttpSignatureValidator)
- [x] HTTP signature verification for inbound requests (validate method already existed)
- [x] Follow activity - Remote user follows local user (InboxProcessor)
- [x] Accept activity - Accept follow requests (FederationService.sendAcceptActivity)
- [x] Undo activity - Unfollow support (InboxProcessor)
- [x] Follow entity and repository (Follow.java, FollowRepository.java)
- [x] Remote actor entity and repository (RemoteActor.java, RemoteActorRepository.java)
- [x] Inbox processor for incoming activities (InboxProcessor.java)
- [x] Remote actor fetching and caching
- [x] Follower inbox collection for activity distribution

**Public Timeline** ✅
- [x] Timeline service (TimelineService.java)
- [x] Timeline DTOs for response (TimelineActivityDTO.java with ActivityMetricsSummary)
- [x] GET /api/timeline/federated - Federated timeline for authenticated user
- [x] GET /api/timeline/public - Public timeline (all public activities)
- [x] GET /api/timeline/user - User's own timeline
- [x] Timeline filtering and pagination (Spring Data Pageable)
- [x] Activity visibility enforcement (PUBLIC, FOLLOWERS)
- [x] Repository methods for timeline queries (findByUserIdInAndVisibilityInOrderByStartedAtDesc, findByVisibilityOrderByStartedAtDesc)

**Database Migrations** ✅
- [x] Flyway setup and configuration
- [x] V1: Enable PostGIS extension
- [x] V2: Users table with indexes (username, email, created_at)
- [x] V3: Activities table with geospatial support (GIST index on simplified_track, GIN index on track_points_json)
- [x] V4: Activity metrics table with one-to-one relationship
- [x] V5: Follows table for federation (follower_id, following_actor_uri, status)
- [x] V6: Remote actors table for ActivityPub federation cache
- [x] All indexes for performance (user lookups, activity queries, spatial queries)
- [x] Changed Hibernate ddl-auto from 'update' to 'validate'

**Frontend Infrastructure** ✅
- [x] Choose frontend approach (Thymeleaf + HTMX for server-side rendering with dynamic interactions)
- [x] Static asset structure (css/, js/, img/ directories)
- [x] HTMX dependency setup (via CDN in layout.html)
- [x] Leaflet.js dependency setup (via CDN in layout.html)
- [x] Chart.js dependency setup (via CDN in layout.html)
- [x] CSS framework setup (Bootstrap 5.3.2 via CDN + Bootstrap Icons)
- [x] Base Thymeleaf layout template with navigation (layout.html)
- [x] Responsive mobile design foundation (Bootstrap grid + custom CSS)
- [x] Custom CSS with FitPub theme (fitpub.css)
- [x] Custom JavaScript utilities (fitpub.js with map/chart helpers)
- [x] Thymeleaf dependencies added to pom.xml
- [x] Home page template (index.html)
- [x] Home controller for routing

**Authentication UI** ✅
- [x] User registration page/form (auth/register.html)
- [x] Login page/form (auth/login.html)
- [x] JWT token storage (localStorage in auth.js)
- [x] Authentication state management (FitPubAuth object in auth.js)
- [x] Protected route handling (SecurityConfig.java + client-side checks)
- [x] Logout functionality (client-side token removal + server endpoint)
- [x] Session expiration handling (JWT expiration check + warning)
- [x] Login/registration error display (alert components in forms)
- [x] Authentication view controller (AuthViewController.java)
- [x] Dynamic navigation menu (shows/hides based on auth status)
- [x] Form validation with Bootstrap
- [x] Loading states for submit buttons
- [x] Password confirmation validation
- [x] Authenticated API fetch helper (authenticatedFetch in auth.js)

**Activity Upload & Management UI** ✅
- [x] FIT file upload form with drag-and-drop
- [x] Upload progress indicator
- [x] Activity metadata form (title, description, visibility)
- [x] Activity list view (user's own activities)
- [x] Activity pagination controls
- [x] Activity delete confirmation dialog
- [x] Activity edit form
- [x] File upload validation and error messages

**Map Rendering & Visualization** ✅
- [x] Leaflet.js map initialization
- [x] OpenStreetMap tile layer integration
- [x] GeoJSON track rendering on map
- [x] Start/finish markers (green/red)
- [x] Map bounds auto-fitting to track
- [x] Track click handler for point-in-time metrics
- [x] Map loading states and error handling
- [x] Responsive map sizing

**Activity Detail Page** ✅
- [x] Activity metadata display (title, description, date, type)
- [x] Interactive map with GPS track
- [x] Activity metrics display (distance, duration, pace, elevation)
- [x] Elevation profile chart (Chart.js)
- [x] Activity statistics summary cards (distance, duration, pace, elevation gain)
- [x] Visibility indicator (Public/Followers/Private)
- [x] Additional metrics display (heart rate, cadence, speed, calories - shown if available)
- [x] Edit button linking to activity edit page
- [x] Delete button with confirmation modal
- [x] Back to activities button
- [x] Loading state indicator
- [x] Error handling and display
- [x] Start/finish markers on map
- [x] Map bounds auto-fitting to track
- [x] Responsive layout for mobile
- [x] User profile links from activity cards and timeline

**Timeline & Social Features UI** ✅
- [x] Public timeline page (timeline/public.html)
- [x] Federated timeline page (timeline/federated.html - following feed)
- [x] User timeline page (timeline/user.html - own activities)
- [x] Timeline activity cards with preview maps (Leaflet.js integration)
- [x] Activity card metrics summary (distance, duration, pace, elevation)
- [x] Pagination for timeline (with prev/next and page numbers)
- [x] Empty state messages (for each timeline type)
- [x] Loading states for timelines (spinner and loading text)
- [x] Timeline view controller (TimelineViewController.java)
- [x] Timeline JavaScript module (timeline.js with dynamic loading)
- [x] Timeline CSS styles (timeline-card, user-avatar, preview-map)
- [x] User information display (avatar, display name, username, federation indicator)
- [x] Time ago formatting (e.g., "2h ago", "3d ago")
- [x] Activity type badges (Run, Ride, Hike)
- [x] Visibility indicators (Public/Followers/Private)
- [x] Interactive preview maps with start/finish markers
- [x] Responsive design for mobile and desktop
- [x] Authentication-aware UI (shows/hides features based on login status)
- [x] Error handling and user-friendly error messages

**User Profile UI** ✅
- [x] Public user profile page (profile/public.html)
- [x] User profile display (avatar, bio, display name)
- [x] User's activity list on profile with pagination
- [x] Follower/following counts display (real data from backend)
- [x] Profile edit page (profile/edit.html)
- [x] Avatar URL input
- [x] Profile settings form with validation
- [x] Profile view controller (ProfileViewController.java)
- [x] User API endpoints (UserController.java)
- [x] User DTOs (UserDTO, UserUpdateRequest)
- [x] GET /api/users/me - Get current user profile
- [x] PUT /api/users/me - Update current user profile
- [x] GET /api/users/{username} - Get user by username
- [x] GET /api/activities/user/{username} - Get user's public activities
- [x] Profile CSS styles (avatar-placeholder-large, stat-card, activity-item)
- [x] Character counter for bio (500 chars max)
- [x] Avatar preview on edit page
- [x] Form validation and error handling
- [x] Success messages and redirects
- [x] Responsive mobile design
- [x] Settings page placeholder (settings.html)
- [x] Client-side authentication checks for protected pages

**User Discovery UI** ✅
- [x] Discover users page (users/discover.html)
- [x] User search functionality with live search bar
- [x] Browse all users with pagination
- [x] User cards grid layout with avatar, bio, and stats
- [x] Responsive design for mobile and desktop
- [x] Empty state for no results
- [x] Loading indicators
- [x] View controller route (GET /discover)
- [x] Integration with backend search and browse APIs

**Navigation & Layout** ✅
- [x] Top navigation bar with logo
- [x] Navigation links (Timeline, Discover, My Activities, Upload, Profile)
- [x] User menu dropdown (Profile, Settings, Logout)
- [x] Footer with app info
- [x] Mobile hamburger menu (Bootstrap responsive navbar)
- [x] Dynamic navigation (shows/hides based on auth status)

**Error Handling & User Feedback** ✅
- [x] API error message display (in activity upload, detail, list pages)
- [x] Success notifications (FitPub.showAlert function)
- [x] Form validation error display (registration, login, activity forms)
- [x] Loading spinners (activity detail page, upload page, timeline, profile)
- [x] Empty states for timelines and profiles
- [x] Client-side 403 handling via authentication redirects

---

## Phase 1 (MVP) - ✅ COMPLETE!

**All core features implemented and working:**
- ✅ FIT file upload and processing
- ✅ GPS track visualization with Leaflet maps
- ✅ Activity management (CRUD operations)
- ✅ User authentication and profiles
- ✅ Public, federated, and user timelines
- ✅ ActivityPub federation (Follow/Accept/Undo)
- ✅ WebFinger user discovery
- ✅ Responsive mobile-friendly UI
- ✅ PostgreSQL + PostGIS database
- ✅ Complete REST API

---

### Phase 2: Social Features & Enhancements ✅
- [x] Likes on activities (Like entity, repository, ActivityPub Like activity support)
- [x] Comments on activities (Comment entity, repository, ActivityPub Note activity support)
- [x] Activity sharing (Announce/boost functionality via ActivityPub)
- [x] Privacy protection for GPS tracks (fuzzy start/finish zones)
- [x] OSM tile rendering for activity maps (OsmTileRenderer with caching)
- [x] Activity image generation with track overlay (ActivityImageService)
- [x] FIT epoch timestamp fix (631065600 second offset for proper date handling)
- [x] Web Mercator projection for accurate track-to-map alignment
- [x] User search and discovery backend (UserRepository.searchUsers, UserRepository.findAllEnabledUsers, GET /api/users/search, GET /api/users/browse)
- [x] User search and discovery UI (users/discover.html, /discover route, search bar with live filtering, user cards grid, pagination)
- [x] Followers/following lists (ActorDTO, GET /api/users/{username}/followers, GET /api/users/{username}/following)
- [x] Follower/following counts (UserController.populateSocialCounts, UserDTO with followersCount/followingCount, frontend displays real counts)
- [x] Heart rate chart over time on activity details (Chart.js line chart, elapsed time x-axis, heart rate y-axis)
- [x] Speed/pace chart over time on activity details (Chart.js line chart with smoothin[69287079d5e0a4532ba818ee.fit](src/test/resources/69287079d5e0a4532ba818ee.fit)g, displays speed in km/h with pace in tooltip)
- [x] Notifications system (Notification entity, NotificationRepository, NotificationService, NotificationController REST API, notifications.html UI, notification bell in nav with unread count, polling every 30s, mark as read/delete, all/unread filter tabs)
- [x] Custom 404 Not Found page (error/404.html with animated compass icon, suggestions, gradient background)
- [x] Custom 403 Forbidden page (error/403.html with shield-lock icon, auth-aware login button, gradient background)
- [x] Custom 500 Internal Server Error page (error/500.html with tools icon, recovery suggestions)
- [x] Generic error page (error/error.html with dynamic error code display, technical details toggle)
- [x] Empty state illustrations (reusable CSS classes in fitpub.css, floating animation, variant colors for activities/notifications/timeline/users/search, updated all timeline pages, notifications page, and discover page)
- [ ] Enhanced privacy controls UI
- [ ] Follow/unfollow buttons on user profiles
- [ ] Activity visibility to followers (implement FOLLOWERS visibility enforcement)
- [ ] Breadcrumb navigation
- [ ] Active route highlighting in navigation
- [ ] Global error boundary/handler

### Phase 3: Advanced Analytics ✅
- [x] Personal records tracking (PersonalRecord entity, PersonalRecordRepository, PersonalRecordService)
- [x] 10 record types (FASTEST_1K, FASTEST_5K, FASTEST_10K, FASTEST_HALF_MARATHON, FASTEST_MARATHON, LONGEST_DISTANCE, LONGEST_DURATION, HIGHEST_ELEVATION_GAIN, MAX_SPEED, BEST_AVERAGE_PACE)
- [x] Automatic PR detection on activity save
- [x] Split time calculations for distance PRs
- [x] Previous value tracking and improvement calculations
- [x] Training load and recovery metrics (TrainingLoad entity, TrainingLoadRepository, TrainingLoadService)
- [x] Training Stress Score (TSS) calculation based on duration, distance, and elevation
- [x] Acute Training Load (ATL) - 7-day rolling average (fatigue)
- [x] Chronic Training Load (CTL) - 28-day rolling average (fitness)
- [x] Training Stress Balance (TSB) - CTL - ATL (freshness)
- [x] Form status calculation (FRESH, OPTIMAL, FATIGUED, UNKNOWN)
- [x] Achievement/badge system (Achievement entity, AchievementRepository, AchievementService)
- [x] 25+ achievement types with badge icons and colors
- [x] First activity achievements (by type)
- [x] Distance milestones (10K, 50K, 100K, 250K, 500K, 1000K)
- [x] Activity count milestones (10, 50, 100, 250, 500, 1000 activities)
- [x] Streak achievements (7, 30, 100 days)
- [x] Time-based achievements (early bird, night owl)
- [x] Elevation achievements (mountaineer 1000m, 5000m, 10000m)
- [x] Variety achievements (trying multiple activity types)
- [x] Speed achievements (speed demon 40+ km/h)
- [x] Weekly/monthly/yearly summaries (ActivitySummary entity, ActivitySummaryRepository, ActivitySummaryService)
- [x] Period-based aggregations (weekly, monthly, yearly)
- [x] Activity type breakdown per period
- [x] Total metrics (distance, duration, elevation gain)
- [x] Average and max speed tracking
- [x] PRs and achievements counts per period
- [x] Async summary updates
- [x] Analytics REST API (AnalyticsController)
- [x] GET /api/analytics/dashboard - Dashboard stats
- [x] GET /api/analytics/personal-records - List PRs with optional activity type filter
- [x] GET /api/analytics/achievements - List earned achievements
- [x] GET /api/analytics/training-load - Training load data with date range
- [x] GET /api/analytics/form-status - Current form status
- [x] GET /api/analytics/summaries/{period} - Weekly/monthly/yearly summaries
- [x] Analytics UI pages (AnalyticsViewController)
- [x] Analytics dashboard (analytics/dashboard.html) with stats overview, current week/month, recent PRs and achievements
- [x] Personal records page (analytics/personal-records.html) with activity type filters, improvement display
- [x] Achievements page (analytics/achievements.html) with badge gallery, animations, metadata display
- [x] Training load page (analytics/training-load.html) with form status card, Chart.js charts (TSS bar chart, ATL vs CTL line chart, TSB line chart with color zones)
- [x] Summaries page (analytics/summaries.html) with weekly/monthly/yearly tabs, summary cards, type breakdown
- [x] Analytics link in navigation (authenticated users only)
- [x] Database migration V10__create_analytics_tables.sql
- [x] Integration with FitFileService (auto-update analytics on activity save)
- [x] Security configuration updated (analytics routes and API endpoints)
- [x] Weather data integration (WeatherData entity, WeatherDataRepository, WeatherService with OpenWeatherMap API)
- [x] Weather database migration V11__create_weather_data_table.sql
- [x] Weather API configuration (fitpub.weather.enabled, fitpub.weather.api-key)
- [x] Weather fetching on activity upload (automatic for activities within 5 days)
- [x] Weather API endpoint GET /api/activities/{id}/weather
- [x] Weather display on activity detail page (temperature, humidity, wind, pressure, precipitation)

### Phase 4: Enhanced Federation
- [x] Rich preview cards for activities
- [x] Cross-platform activity sync

### Phase 5: Mobile & Integrations
- [ ] Progressive Web App (PWA)
- [ ] Native mobile apps (optional)
- [ ] Direct device sync (Garmin Connect API)
- [ ] Webhook integrations
- [ ] Import from Strava, Garmin, etc.
- [ ] Avatar file upload (currently URL-based)

### Phase 6: Testing & Documentation
- [ ] Integration tests for REST endpoints
- [ ] Integration tests for ActivityPub federation
- [ ] Integration tests for WebFinger
- [ ] Unit tests for services and utilities
- [ ] Frontend E2E tests (Playwright/Cypress)
- [ ] README with setup instructions
- [ ] API documentation (Swagger/OpenAPI)
- [ ] Database setup guide
- [ ] Deployment instructions (Docker, Kubernetes)
- [ ] Frontend development guide
- [ ] User documentation
- [ ] Administrator guide

## Maven Project Structure

```
fitpub/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── fitpub/
│   │   │           ├── FitPubApplication.java
│   │   │           ├── config/
│   │   │           │   ├── SecurityConfig.java
│   │   │           │   ├── WebConfig.java
│   │   │           │   └── ActivityPubConfig.java
│   │   │           ├── controller/
│   │   │           │   ├── ActivityController.java
│   │   │           │   ├── UserController.java
│   │   │           │   ├── ActivityPubController.java
│   │   │           │   └── WebFingerController.java
│   │   │           ├── service/
│   │   │           │   ├── FitFileService.java
│   │   │           │   ├── ActivityService.java
│   │   │           │   ├── FederationService.java
│   │   │           │   ├── UserService.java
│   │   │           │   └── GeospatialService.java
│   │   │           ├── model/
│   │   │           │   ├── entity/
│   │   │           │   │   ├── User.java
│   │   │           │   │   ├── Activity.java
│   │   │           │   │   ├── TrackPoint.java
│   │   │           │   │   ├── Follow.java
│   │   │           │   │   └── RemoteActor.java
│   │   │           │   ├── dto/
│   │   │           │   │   ├── ActivityDTO.java
│   │   │           │   │   ├── UserDTO.java
│   │   │           │   │   └── TrackDTO.java
│   │   │           │   └── activitypub/
│   │   │           │       ├── Actor.java
│   │   │           │       ├── Activity.java
│   │   │           │       └── Collection.java
│   │   │           ├── repository/
│   │   │           │   ├── UserRepository.java
│   │   │           │   ├── ActivityRepository.java
│   │   │           │   ├── TrackPointRepository.java
│   │   │           │   └── FollowRepository.java
│   │   │           ├── security/
│   │   │           │   ├── JwtTokenProvider.java
│   │   │           │   ├── HttpSignatureValidator.java
│   │   │           │   └── UserDetailsServiceImpl.java
│   │   │           └── util/
│   │   │               ├── FitParser.java
│   │   │               ├── GeoJsonConverter.java
│   │   │               └── ActivityPubUtil.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── static/
│   │       │   ├── css/
│   │       │   ├── js/
│   │       │   └── img/
│   │       └── templates/
│   │           ├── index.html
│   │           ├── activity.html
│   │           └── profile.html
│   └── test/
│       └── java/
│           └── com/
│               └── fitpub/
│                   ├── service/
│                   ├── controller/
│                   └── integration/
└── README.md
```

## Key Maven Dependencies

```xml
<dependencies>
    <!-- Spring Boot Starters -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    
    <!-- Database -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
    <dependency>
        <groupId>org.hibernate</groupId>
        <artifactId>hibernate-spatial</artifactId>
    </dependency>
    
    <!-- FIT File Processing -->
    <dependency>
        <groupId>com.garmin</groupId>
        <artifactId>fit</artifactId>
        <version>21.XX.XX</version>
    </dependency>
    
    <!-- JSON Processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    
    <!-- HTTP Client -->
    <dependency>
        <groupId>org.apache.httpcomponents.client5</groupId>
        <artifactId>httpclient5</artifactId>
    </dependency>
    
    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
    </dependency>
    
    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Configuration Examples

### application.yml

```yaml
spring:
  application:
    name: fitpub
  datasource:
    url: jdbc:postgresql://localhost:5432/fitpub
    username: fitpub_user
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.spatial.dialect.postgis.PostgisDialect
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

fitpub:
  domain: fitpub.example
  base-url: https://fitpub.example
  activitypub:
    enabled: true
    max-federation-retries: 3
  security:
    jwt:
      secret: ${JWT_SECRET}
      expiration: 86400000 # 24 hours
  storage:
    fit-files:
      path: /var/fitpub/fit-files
      retention-days: 365
```

## Deployment Considerations

### Infrastructure Requirements

- **Application Server**: JVM-based (Java 17+)
- **Database**: PostgreSQL 13+ with PostGIS extension
- **Reverse Proxy**: Nginx or Traefik for HTTPS termination
- **Storage**: File storage for FIT files and generated assets
- **Cache**: Redis (optional, for session management)

### Scaling Strategy

- Horizontal scaling of application servers
- Database read replicas for heavy read operations
- CDN for static assets and map tiles
- Background job processing for FIT file parsing (Spring Batch or async)
- Rate limiting and request throttling

### Monitoring & Observability

- Application metrics (Micrometer + Prometheus)
- Database query performance monitoring
- Federation success/failure rates
- API response times
- User activity analytics

## Legal & Compliance

### Licensing Considerations

- Choose appropriate open-source license (AGPL, MIT, Apache 2.0)
- Comply with FIT SDK licensing terms
- Attribute map tile providers
- Terms of Service for user-generated content
- Privacy Policy (GDPR, CCPA compliance)

### Data Retention

- User data export functionality
- Right to be forgotten (account deletion)
- Activity data backup procedures
- Federation data cleanup (remove data from remote deleted actors)

## Community & Contribution

### Open Source Goals

- Public GitHub repository
- Contribution guidelines
- Code of conduct
- Issue templates
- Documentation for developers
- Public roadmap and feature requests

### Fediverse Integration

- Publish FEP (Fediverse Enhancement Proposal) if introducing custom extensions
- Collaborate with other ActivityPub developers
- Test interoperability with major platforms (Mastodon, Pleroma, Pixelfed)
- Participate in Fediverse developer community

## Future Enhancements

- **AI-Powered Insights**: Training recommendations, injury prevention
- **Virtual Racing**: Compete on same routes asynchronously
- **Route Planning**: Create routes and share with community
- **Live Tracking**: Real-time activity sharing during workout
- **Wearable Integration**: Direct sync with smartwatches
- **Audio Cues**: Export audio-guided workouts
- **Social Challenges**: Group goals and competitions
- **Marketplace**: Routes, training plans, coaching services

---

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- PostgreSQL 13+ with PostGIS
- Git

### Quick Start

1. Clone repository
2. Set up PostgreSQL database with PostGIS extension
3. Configure `application.yml` with database credentials
4. Run `mvn clean install`
5. Start application: `mvn spring-boot:run`
6. Access at `http://localhost:8080`

### First Steps

1. Register a user account
2. Upload a FIT file from your GPS device
3. View your activity on the interactive map
4. Set up ActivityPub federation (optional)
5. Follow other athletes on the Fediverse

---

**Project Status**: Planning & Initial Development

**License**: TBD

**Contributors Welcome**: Yes

**Contact**: [Project repository or contact information]