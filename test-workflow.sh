#!/bin/bash
# Quick workflow test for DayClose services
# Tests: auth → create item → reserve stock → create commitment → approve

set -e

API="http://localhost:8080"
AUTH_API="http://localhost:8081"
INVENTORY_API="http://localhost:8082"
COMMITMENT_API="http://localhost:8083"

echo "🧪 DayClose Workflow Test"
echo "=========================="

# 1. Register and login
echo ""
echo "1️⃣  Registering test tenant..."
REGISTER_RESPONSE=$(curl -s -X POST "$AUTH_API/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantName": "Test Shop",
    "email": "owner@test.com",
    "password": "test123",
    "role": "OWNER"
  }')

ACCESS_TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.access_token')
echo "✅ Registered. Token: ${ACCESS_TOKEN:0:20}..."

# 2. Create a location
echo ""
echo "2️⃣  Creating warehouse location..."
LOCATION_RESPONSE=$(curl -s -X POST "$INVENTORY_API/v1/locations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{
    "name": "Main Warehouse"
  }')

LOCATION_ID=$(echo "$LOCATION_RESPONSE" | jq -r '.id')
echo "✅ Location created: $LOCATION_ID"

# 3. Create an item
echo ""
echo "3️⃣  Creating test item..."
ITEM_RESPONSE=$(curl -s -X POST "$INVENTORY_API/v1/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{
    "sku": "WIDGET-001",
    "name": "Test Widget",
    "unit": "pcs",
    "pricePaise": 10000,
    "currency": "INR",
    "reorderPoint": 10,
    "reorderQty": 50
  }')

ITEM_ID=$(echo "$ITEM_RESPONSE" | jq -r '.id')
echo "✅ Item created: $ITEM_ID"

# 4. Receive stock
echo ""
echo "4️⃣  Receiving 100 units of stock..."
curl -s -X POST "$INVENTORY_API/v1/stock/receive" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d "{
    \"itemId\": \"$ITEM_ID\",
    \"locationId\": \"$LOCATION_ID\",
    \"qty\": 100,
    \"actor\": \"test-script\"
  }" > /dev/null

echo "✅ Stock received"

# 5. Check stock levels
echo ""
echo "5️⃣  Checking stock levels..."
STOCK=$(curl -s -X GET "$INVENTORY_API/v1/stock/$ITEM_ID/$LOCATION_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

ON_HAND=$(echo "$STOCK" | jq -r '.onHand')
AVAILABLE=$(echo "$STOCK" | jq -r '.available()')
echo "✅ Stock: on_hand=$ON_HAND, available=$AVAILABLE"

# 6. Create a reservation
echo ""
echo "6️⃣  Creating stock reservation (20 units)..."
COMMITMENT_ID=$(uuidgen)
RESERVATION_RESPONSE=$(curl -s -X POST "$INVENTORY_API/v1/reservations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d "{
    \"commitmentId\": \"$COMMITMENT_ID\",
    \"itemId\": \"$ITEM_ID\",
    \"locationId\": \"$LOCATION_ID\",
    \"qty\": 20,
    \"customerRef\": \"customer-123\",
    \"ttl\": \"30 minutes\"
  }")

RESERVATION_ID=$(echo "$RESERVATION_RESPONSE" | jq -r '.reservation_id')
echo "✅ Reservation created: $RESERVATION_ID"

# 7. Create commitment
echo ""
echo "7️⃣  Creating commitment (order)..."
COMMITMENT_RESPONSE=$(curl -s -X POST "$COMMITMENT_API/v1/commitments" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d "{
    \"customerRef\": \"customer-123\",
    \"type\": \"order\",
    \"lines\": [
      {
        \"itemId\": \"$ITEM_ID\",
        \"locationId\": \"$LOCATION_ID\",
        \"reservationId\": \"$RESERVATION_ID\",
        \"qty\": 20,
        \"pricePaise\": 10000
      }
    ],
    \"metadata\": \"{\\\"source\\\":\\\"phone-call\\\"}\"
  }")

COMMITMENT_ID_ACTUAL=$(echo "$COMMITMENT_RESPONSE" | jq -r '.id')
COMMITMENT_STATE=$(echo "$COMMITMENT_RESPONSE" | jq -r '.state')
echo "✅ Commitment created: $COMMITMENT_ID_ACTUAL (state: $COMMITMENT_STATE)"

# 8. List pending approvals
echo ""
echo "8️⃣  Listing pending approvals..."
PENDING=$(curl -s -X GET "$COMMITMENT_API/v1/commitments?state=pending_approval" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

COUNT=$(echo "$PENDING" | jq 'length')
echo "✅ Pending approvals: $COUNT"

# 9. Approve commitment
echo ""
echo "9️⃣  Approving commitment..."
curl -s -X POST "$COMMITMENT_API/v1/commitments/$COMMITMENT_ID_ACTUAL/approve" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{
    "actor": "owner:test",
    "reason": "Approved by test script"
  }' > /dev/null

echo "✅ Commitment approved"

# 10. Verify final state
echo ""
echo "🔍 Final verification..."
FINAL_COMMITMENT=$(curl -s -X GET "$COMMITMENT_API/v1/commitments/$COMMITMENT_ID_ACTUAL" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

FINAL_STATE=$(echo "$FINAL_COMMITMENT" | jq -r '.state')
echo "   Commitment state: $FINAL_STATE"

FINAL_STOCK=$(curl -s -X GET "$INVENTORY_API/v1/stock/$ITEM_ID/$LOCATION_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

FINAL_ON_HAND=$(echo "$FINAL_STOCK" | jq -r '.onHand')
FINAL_RESERVED=$(echo "$FINAL_STOCK" | jq -r '.reserved')
echo "   Stock: on_hand=$FINAL_ON_HAND, reserved=$FINAL_RESERVED"

echo ""
echo "✅ Workflow test complete!"
echo ""
echo "Summary:"
echo "  - Tenant registered and authenticated"
echo "  - Item and location created"
echo "  - Stock received (100 units)"
echo "  - Reservation created (20 units)"
echo "  - Commitment created and approved"
echo "  - State: $FINAL_STATE"
