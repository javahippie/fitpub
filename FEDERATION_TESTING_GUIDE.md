# FitPub Federation Testing Guide

This guide explains how to test the instance-to-instance federation functionality by running two FitPub instances locally.

## Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL 13+ with PostGIS extension
- Two separate PostgreSQL databases
- Two different port numbers for the applications

## Setup

### Step 1: Create Two PostgreSQL Databases

```bash
# Connect to PostgreSQL
psql -U postgres

# Create databases
CREATE DATABASE fitpub_instance1;
CREATE DATABASE fitpub_instance2;

# Enable PostGIS extension for both databases
\c fitpub_instance1
CREATE EXTENSION IF NOT EXISTS postgis;

\c fitpub_instance2
CREATE EXTENSION IF NOT EXISTS postgis;

\q
```

### Step 2: Prepare Application Profiles

Create two separate application configuration files:

#### `application-instance1.yml`

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/fitpub_instance1
    username: postgres
    password: your_password
  jpa:
    hibernate:
      ddl-auto: validate

fitpub:
  base-url: http://localhost:8080
  domain: localhost:8080

logging:
  level:
    org.operaton.fitpub: DEBUG
```

#### `application-instance2.yml`

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/fitpub_instance2
    username: postgres
    password: your_password
  jpa:
    hibernate:
      ddl-auto: validate

fitpub:
  base-url: http://localhost:8081
  domain: localhost:8081

logging:
  level:
    org.operaton.fitpub: DEBUG
```

### Step 3: Build the Application

```bash
mvn clean package -DskipTests
```

## Running the Instances

### Terminal 1: Start Instance 1

```bash
java -jar target/feditrack-1.0-SNAPSHOT.jar --spring.profiles.active=instance1
```

Wait for the application to start completely. You should see:
```
Started FitPubApplication in X.XXX seconds
```

### Terminal 2: Start Instance 2

```bash
java -jar target/feditrack-1.0-SNAPSHOT.jar --spring.profiles.active=instance2
```

## Test Scenarios

### Test 1: User Registration

**Instance 1 (http://localhost:8080)**
1. Navigate to http://localhost:8080/register
2. Register user: `alice` / `alice@localhost1.test` / `password123`
3. Login

**Instance 2 (http://localhost:8081)**
1. Navigate to http://localhost:8081/register
2. Register user: `bob` / `bob@localhost2.test` / `password123`
3. Login

### Test 2: WebFinger Discovery

**From Instance 1, discover Bob on Instance 2:**

```bash
curl http://localhost:8080/.well-known/webfinger?resource=acct:bob@localhost:8081
```

Expected response:
```json
{
  "subject": "acct:bob@localhost:8081",
  "aliases": [
    "http://localhost:8081/users/bob"
  ],
  "links": [
    {
      "rel": "self",
      "type": "application/activity+json",
      "href": "http://localhost:8081/users/bob"
    }
  ]
}
```

**From Instance 2, discover Alice on Instance 1:**

```bash
curl http://localhost:8081/.well-known/webfinger?resource=acct:alice@localhost:8080
```

### Test 3: Remote User Discovery via UI

**On Instance 1 (Alice following Bob):**

1. Login as Alice
2. Navigate to http://localhost:8080/discover
3. In the "Follow Remote Users" section, enter: `bob@localhost:8081`
4. Click "Search"
5. Verify Bob's profile appears with avatar, display name, and bio
6. Click "Follow" button
7. Verify notification appears: "Follow request sent to bob@localhost:8081"

**Verify on Instance 2 (Bob's perspective):**

1. Login as Bob on http://localhost:8081
2. Check notifications - you should see: "alice@localhost:8080 followed you"
3. Navigate to http://localhost:8081/users/bob/followers
4. Verify alice@localhost:8080 appears in followers list

### Test 4: Following Relationship Check

**Check via API:**

```bash
# From Instance 2, check Bob's followers
curl http://localhost:8081/api/users/bob/followers | jq

# Expected: Alice should be in the list
```

**Check via UI:**

On Instance 2:
1. Navigate to http://localhost:8081/users/bob
2. Check "Followers" count - should be 1
3. Click on "Followers" - Alice should be listed

On Instance 1:
1. Navigate to http://localhost:8080/users/alice
2. Check "Following" count - should be 1
3. Click on "Following" - Bob should be listed

### Test 5: Activity Federation

**Bob uploads a workout on Instance 2:**

1. Login as Bob on http://localhost:8081
2. Navigate to http://localhost:8081/upload
3. Upload a FIT file (use test file from `src/test/resources/`)
4. Set title: "Morning 10K Run"
5. Set visibility: "Public"
6. Click "Upload"

**Verify on Instance 1 (Alice's federated timeline):**

1. Login as Alice on http://localhost:8080
2. Navigate to http://localhost:8080/timeline/federated
3. Verify Bob's "Morning 10K Run" activity appears with:
   - Federation badge: "🌐 Remote"
   - Bob's avatar and @bob@localhost:8081
   - Map preview (if map image URL is available)
   - Metrics (distance, duration, pace, elevation)
   - Link to view on origin server

### Test 6: Remote Activity Details

**Click on Remote Activity:**

From Alice's federated timeline:
1. Click on Bob's "Morning 10K Run" activity title
2. Verify it opens Bob's activity on Instance 2 (http://localhost:8081/activities/{id}) in a new tab
3. Alternatively, click "View on Origin Server" button

### Test 7: Incoming Activity via ActivityPub

**Test with manual ActivityPub POST:**

```bash
# Create a test activity
cat > test-activity.json <<EOF
{
  "@context": "https://www.w3.org/ns/activitystreams",
  "type": "Create",
  "id": "http://localhost:8081/activities/create/test-123",
  "actor": "http://localhost:8081/users/bob",
  "published": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "to": ["https://www.w3.org/ns/activitystreams#Public"],
  "cc": ["http://localhost:8081/users/bob/followers"],
  "object": {
    "type": "Note",
    "id": "http://localhost:8081/workouts/test-456",
    "attributedTo": "http://localhost:8081/users/bob",
    "name": "Test Workout via ActivityPub",
    "content": "Testing direct ActivityPub activity delivery",
    "published": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "to": ["https://www.w3.org/ns/activitystreams#Public"],
    "workoutData": {
      "activityType": "RUN",
      "distance": 5000,
      "duration": "PT25M30S",
      "elevationGain": 50
    },
    "attachment": [
      {
        "type": "Document",
        "mediaType": "image/png",
        "name": "Map",
        "url": "http://localhost:8081/activities/test-456/map.png"
      }
    ]
  }
}
EOF

# Post to Alice's inbox on Instance 1
curl -X POST http://localhost:8080/users/alice/inbox \
  -H "Content-Type: application/activity+json" \
  -d @test-activity.json

# Expected response: 202 Accepted
```

**Verify in database:**

```sql
-- On Instance 1
SELECT * FROM remote_activities WHERE remote_actor_uri = 'http://localhost:8081/users/bob';
```

### Test 8: Unfollow Workflow

**Alice unfollows Bob:**

1. On Instance 1, navigate to http://localhost:8080/users/alice/following
2. Find Bob in the following list
3. Click "Unfollow"
4. Verify confirmation dialog
5. Confirm unfollow

**Verify Undo Activity:**

Check Instance 2 logs for incoming Undo activity:
```
Processing Undo activity for user bob
Deleted follow from actor: http://localhost:8080/users/alice
```

**Check Database:**

```sql
-- On Instance 2
SELECT * FROM follows WHERE remote_actor_uri = 'http://localhost:8080/users/alice'
  AND following_actor_uri = 'http://localhost:8081/users/bob';
-- Should return 0 rows
```

### Test 9: Accept Activity Flow

**Bob follows Alice (reverse direction):**

1. On Instance 2, login as Bob
2. Navigate to http://localhost:8081/discover
3. Search for: `alice@localhost:8080`
4. Click "Follow"
5. Verify "Follow request sent" notification

**Check Follow Status:**

```sql
-- On Instance 2
SELECT status FROM follows WHERE follower_id = (SELECT id FROM users WHERE username = 'bob')
  AND following_actor_uri = 'http://localhost:8080/users/alice';
-- Should return 'PENDING'
```

**Verify Accept on Instance 1:**

Check Instance 1 logs for outgoing Accept activity:
```
Sending Accept activity to http://localhost:8081/users/bob/inbox
Accept activity sent successfully
```

**Check Updated Status:**

```sql
-- On Instance 2
SELECT status FROM follows WHERE follower_id = (SELECT id FROM users WHERE username = 'bob')
  AND following_actor_uri = 'http://localhost:8080/users/alice';
-- Should return 'ACCEPTED'
```

**Check Notification:**

On Instance 2:
1. Navigate to http://localhost:8081/notifications
2. Verify notification: "alice@localhost:8080 accepted your follow request"

## Troubleshooting

### Instance Won't Start

**Problem:** Port already in use
```
Port 8080 is already in use
```

**Solution:** Kill the process using the port or use a different port
```bash
lsof -ti:8080 | xargs kill -9
```

### Database Connection Error

**Problem:** Connection refused to PostgreSQL

**Solution:** Check PostgreSQL is running
```bash
brew services start postgresql
# or
sudo systemctl start postgresql
```

### WebFinger Not Working

**Problem:** 404 when accessing /.well-known/webfinger

**Solution:**
1. Check if the controller is mapped correctly
2. Verify Spring Security allows unauthenticated access to WebFinger endpoint
3. Check logs for any errors

### Remote Activities Not Appearing

**Problem:** Bob's activities don't show up in Alice's federated timeline

**Solution:**
1. Verify follow relationship exists and status is ACCEPTED:
   ```sql
   SELECT * FROM follows WHERE follower_id = (SELECT id FROM users WHERE username = 'alice')
     AND following_actor_uri LIKE '%bob%';
   ```
2. Check InboxProcessor logs for incoming Create activities
3. Verify RemoteActivity was created:
   ```sql
   SELECT * FROM remote_activities;
   ```
4. Check TimelineService is fetching both local and remote activities

### Map Preview Not Loading

**Problem:** Remote activity map shows "Map not available"

**Solution:**
- Remote activities use `mapImageUrl` field which must be set when creating the activity
- For testing, you may need to implement map image generation on the origin server
- Check if the URL in `map_image_url` is accessible

## Validation Checklist

- [ ] Both instances start successfully on different ports
- [ ] WebFinger discovery works in both directions
- [ ] Remote user discovery UI works
- [ ] Follow request is sent and creates PENDING follow
- [ ] Accept activity is received and updates status to ACCEPTED
- [ ] Follower/following lists show both local and remote users
- [ ] Remote activities appear in federated timeline
- [ ] Remote activities show federation badge
- [ ] Map preview loads from remote server
- [ ] "View on Origin Server" opens correct URL
- [ ] Unfollow sends Undo activity and removes follow
- [ ] Notifications are created for follow/accept events
- [ ] No errors in console logs

## Database Inspection Queries

### Check All Follows

```sql
-- On any instance
SELECT
  f.id,
  f.follower_id,
  u.username as follower_username,
  f.following_actor_uri,
  f.remote_actor_uri,
  f.status,
  f.created_at
FROM follows f
LEFT JOIN users u ON f.follower_id = u.id
ORDER BY f.created_at DESC;
```

### Check Remote Actors

```sql
SELECT
  actor_uri,
  username,
  domain,
  display_name,
  last_fetched
FROM remote_actors
ORDER BY last_fetched DESC;
```

### Check Remote Activities

```sql
SELECT
  id,
  activity_uri,
  remote_actor_uri,
  activity_type,
  title,
  total_distance,
  total_duration_seconds,
  published_at,
  visibility,
  map_image_url
FROM remote_activities
ORDER BY published_at DESC;
```

## Clean Up

To reset the test environment:

```bash
# Stop both instances (Ctrl+C in both terminals)

# Drop and recreate databases
psql -U postgres <<EOF
DROP DATABASE fitpub_instance1;
DROP DATABASE fitpub_instance2;
CREATE DATABASE fitpub_instance1;
CREATE DATABASE fitpub_instance2;
\c fitpub_instance1
CREATE EXTENSION IF NOT EXISTS postgis;
\c fitpub_instance2
CREATE EXTENSION IF NOT EXISTS postgis;
EOF
```

## Additional Testing Tips

1. **Use Browser Developer Tools** to inspect network requests for ActivityPub activities
2. **Monitor Logs** in both terminal windows to see federation events in real-time
3. **Test Edge Cases** like following yourself, duplicate follows, following non-existent users
4. **Test Different Visibility Levels** (PUBLIC, FOLLOWERS, PRIVATE)
5. **Simulate Network Failures** by stopping one instance mid-federation
6. **Test Concurrent Operations** (both users following each other simultaneously)

## Success Criteria

✅ All checklist items pass
✅ No errors in logs
✅ Activities federate within 1-2 seconds
✅ UI updates reflect federation state correctly
✅ Database state is consistent across both instances
