#!/bin/bash

# Script to trigger retroactive indoor activity flag migration
# This script calls the admin API endpoint to update existing activities

echo "🔄 Starting indoor activity flag migration..."
echo ""

# Check if JWT token is provided
if [ -z "$JWT_TOKEN" ]; then
    echo "⚠️  No JWT token provided."
    echo ""
    echo "To use this script, you need to provide a valid JWT token:"
    echo "  1. Login to your FitPub account at http://localhost:8080/login"
    echo "  2. Open browser developer tools (F12)"
    echo "  3. Go to Application/Storage -> Local Storage"
    echo "  4. Copy the value of 'jwt_token'"
    echo "  5. Run this script with: JWT_TOKEN='your-token-here' ./migrate-indoor-flags.sh"
    echo ""
    exit 1
fi

# Call the migration endpoint
RESPONSE=$(curl -s -w "\n%{http_code}" \
    -X POST \
    -H "Authorization: Bearer $JWT_TOKEN" \
    http://localhost:8080/api/admin/migrate-indoor-flags)

# Extract HTTP status code
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo "HTTP Status: $HTTP_CODE"
echo ""

if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ Migration successful!"
    echo ""
    echo "Response:"
    echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
else
    echo "❌ Migration failed!"
    echo ""
    echo "Response:"
    echo "$BODY"
fi

echo ""
