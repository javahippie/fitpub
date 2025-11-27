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
- [ ] FIT file upload and parsing
- [ ] Basic activity storage and display
- [ ] Interactive map rendering with Leaflet
- [ ] User registration and authentication
- [ ] ActivityPub actor profile implementation
- [ ] WebFinger support
- [ ] Basic federation (Create, Follow, Accept activities)
- [ ] Public timeline view

### Phase 2: Social Features
- [ ] Likes and comments
- [ ] Activity sharing (Announce)
- [ ] User search and discovery
- [ ] Followers/following lists
- [ ] Notifications system
- [ ] Privacy controls
- [ ] Activity editing and deletion

### Phase 3: Advanced Analytics
- [ ] Personal records tracking
- [ ] Training load and recovery metrics
- [ ] Segment comparison (Strava-like)
- [ ] Achievement/badge system
- [ ] Weekly/monthly summaries
- [ ] Route recommendations
- [ ] Weather data integration

### Phase 4: Enhanced Federation
- [ ] Rich preview cards for activities
- [ ] Media attachments (photos from workout)
- [ ] Activity challenges (federated events)
- [ ] Group/club support
- [ ] Cross-platform activity sync

### Phase 5: Mobile & Integrations
- [ ] Mobile-responsive web design
- [ ] Progressive Web App (PWA)
- [ ] Native mobile apps (optional)
- [ ] Direct device sync (Garmin Connect API)
- [ ] Webhook integrations
- [ ] Import from Strava, Garmin, etc.

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