/**
 * Privacy Zones Management Module
 * Handles GPS privacy zone creation, editing, and deletion with interactive Leaflet map.
 */
const PrivacyZones = (() => {
    let map = null;
    let marker = null;
    let circle = null;
    let zones = [];
    let currentZone = null;

    /**
     * Initialize the privacy zones module
     */
    function init() {
        setupEventListeners();
        loadZones();
    }

    /**
     * Setup event listeners for UI interactions
     */
    function setupEventListeners() {
        const startAddZoneBtn = document.getElementById('startAddZoneBtn');
        const cancelZoneBtn = document.getElementById('cancelZoneBtn');
        const zoneDetailsForm = document.getElementById('zoneDetailsForm');
        const zoneRadiusInput = document.getElementById('zoneRadius');
        const radiusValue = document.getElementById('radiusValue');

        startAddZoneBtn.addEventListener('click', startAddingZone);
        cancelZoneBtn.addEventListener('click', cancelZone);
        zoneDetailsForm.addEventListener('submit', saveZone);

        // Update radius display and circle in real-time
        zoneRadiusInput.addEventListener('input', (e) => {
            const radius = parseInt(e.target.value);
            radiusValue.textContent = radius;
            if (circle) {
                circle.setRadius(radius);
            }
        });
    }

    /**
     * Start the process of adding a new zone
     */
    function startAddingZone() {
        const formContainer = document.getElementById('addZoneForm');
        formContainer.classList.remove('d-none');

        // Initialize map if not already done
        if (!map) {
            initMap();
        }

        // Reset form
        document.getElementById('zoneId').value = '';
        document.getElementById('zoneName').value = '';
        document.getElementById('zoneDescription').value = '';
        document.getElementById('zoneLatitude').value = '';
        document.getElementById('zoneLongitude').value = '';
        document.getElementById('zoneRadius').value = 200;
        document.getElementById('radiusValue').textContent = '200';

        // Clear existing marker and circle
        if (marker) {
            map.removeLayer(marker);
            marker = null;
        }
        if (circle) {
            map.removeLayer(circle);
            circle = null;
        }

        currentZone = null;
    }

    /**
     * Cancel zone creation/editing
     */
    function cancelZone() {
        const formContainer = document.getElementById('addZoneForm');
        formContainer.classList.add('d-none');

        if (marker) {
            map.removeLayer(marker);
            marker = null;
        }
        if (circle) {
            map.removeLayer(circle);
            circle = null;
        }

        currentZone = null;
    }

    /**
     * Initialize the Leaflet map
     */
    function initMap() {
        const mapContainer = document.getElementById('zoneMap');

        // Initialize map centered on user's location (or default)
        map = L.map(mapContainer).setView([51.505, -0.09], 13);

        // Add OpenStreetMap tiles
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
            maxZoom: 19
        }).addTo(map);

        // Try to get user's current location
        if (navigator.geolocation) {
            navigator.geolocation.getCurrentPosition(
                (position) => {
                    const lat = position.coords.latitude;
                    const lon = position.coords.longitude;
                    map.setView([lat, lon], 15);
                },
                (error) => {
                    console.warn('Geolocation error:', error);
                }
            );
        }

        // Handle map clicks to place marker
        map.on('click', (e) => {
            placeMarker(e.latlng.lat, e.latlng.lng);
        });
    }

    /**
     * Place a marker and circle on the map
     */
    function placeMarker(lat, lon) {
        const radius = parseInt(document.getElementById('zoneRadius').value);

        // Remove existing marker and circle
        if (marker) {
            map.removeLayer(marker);
        }
        if (circle) {
            map.removeLayer(circle);
        }

        // Create new marker
        marker = L.marker([lat, lon], {
            draggable: true
        }).addTo(map);

        // Create circle
        circle = L.circle([lat, lon], {
            color: '#dc3545',
            fillColor: '#dc3545',
            fillOpacity: 0.2,
            radius: radius
        }).addTo(map);

        // Update form fields
        document.getElementById('zoneLatitude').value = lat.toFixed(6);
        document.getElementById('zoneLongitude').value = lon.toFixed(6);

        // Handle marker dragging
        marker.on('drag', (e) => {
            const position = e.target.getLatLng();
            circle.setLatLng(position);
            document.getElementById('zoneLatitude').value = position.lat.toFixed(6);
            document.getElementById('zoneLongitude').value = position.lng.toFixed(6);
        });
    }

    /**
     * Save a privacy zone (create or update)
     */
    async function saveZone(e) {
        e.preventDefault();

        const zoneId = document.getElementById('zoneId').value;
        const name = document.getElementById('zoneName').value.trim();
        const description = document.getElementById('zoneDescription').value.trim();
        const latitude = parseFloat(document.getElementById('zoneLatitude').value);
        const longitude = parseFloat(document.getElementById('zoneLongitude').value);
        const radiusMeters = parseInt(document.getElementById('zoneRadius').value);

        if (!latitude || !longitude) {
            FitPub.showAlert('Please click on the map to place a privacy zone', 'warning');
            return;
        }

        const saveBtn = document.getElementById('saveZoneBtn');
        saveBtn.disabled = true;
        saveBtn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Saving...';

        try {
            const url = zoneId ? `/api/privacy-zones/${zoneId}` : '/api/privacy-zones';
            const method = zoneId ? 'PUT' : 'POST';

            const response = await FitPubAuth.authenticatedFetch(url, {
                method: method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    name,
                    description,
                    latitude,
                    longitude,
                    radiusMeters
                })
            });

            if (response.ok) {
                FitPub.showAlert(zoneId ? 'Privacy zone updated' : 'Privacy zone created', 'success');
                cancelZone();
                loadZones();
            } else {
                const error = await response.json();
                FitPub.showAlert(error.message || 'Failed to save privacy zone', 'danger');
            }
        } catch (error) {
            console.error('Save zone error:', error);
            FitPub.showAlert('Network error. Please try again.', 'danger');
        } finally {
            saveBtn.disabled = false;
            saveBtn.innerHTML = '<i class="bi bi-check-circle"></i> Save Zone';
        }
    }

    /**
     * Load all privacy zones from the API
     */
    async function loadZones() {
        const loadingEl = document.getElementById('zonesListLoading');
        const emptyEl = document.getElementById('zonesListEmpty');
        const listEl = document.getElementById('zonesList');

        loadingEl.classList.remove('d-none');
        emptyEl.classList.add('d-none');
        listEl.innerHTML = '';

        try {
            const response = await FitPubAuth.authenticatedFetch('/api/privacy-zones');

            if (response.ok) {
                zones = await response.json();
                loadingEl.classList.add('d-none');

                if (zones.length === 0) {
                    emptyEl.classList.remove('d-none');
                } else {
                    renderZonesList();
                }
            } else {
                loadingEl.classList.add('d-none');
                FitPub.showAlert('Failed to load privacy zones', 'danger');
            }
        } catch (error) {
            console.error('Load zones error:', error);
            loadingEl.classList.add('d-none');
            FitPub.showAlert('Network error. Please try again.', 'danger');
        }
    }

    /**
     * Render the list of privacy zones
     */
    function renderZonesList() {
        const listEl = document.getElementById('zonesList');
        listEl.innerHTML = '';

        zones.forEach(zone => {
            const item = document.createElement('div');
            item.className = 'list-group-item';
            item.innerHTML = `
                <div class="d-flex justify-content-between align-items-start">
                    <div class="flex-grow-1">
                        <h6 class="mb-1">
                            ${escapeHtml(zone.name)}
                            ${zone.isActive ? '<span class="badge bg-success ms-2">Active</span>' : '<span class="badge bg-secondary ms-2">Inactive</span>'}
                        </h6>
                        ${zone.description ? `<p class="mb-1 small text-muted">${escapeHtml(zone.description)}</p>` : ''}
                        <p class="mb-0 small text-muted">
                            <i class="bi bi-geo-alt"></i> ${zone.latitude.toFixed(6)}, ${zone.longitude.toFixed(6)} &middot;
                            <i class="bi bi-circle"></i> ${zone.radiusMeters}m radius
                        </p>
                    </div>
                    <div class="btn-group btn-group-sm" role="group">
                        <button class="btn btn-outline-secondary" onclick="PrivacyZones.editZone('${zone.id}')" title="Edit">
                            <i class="bi bi-pencil"></i>
                        </button>
                        <button class="btn btn-outline-${zone.isActive ? 'warning' : 'success'}" onclick="PrivacyZones.toggleZone('${zone.id}', ${!zone.isActive})" title="${zone.isActive ? 'Deactivate' : 'Activate'}">
                            <i class="bi bi-${zone.isActive ? 'pause' : 'play'}"></i>
                        </button>
                        <button class="btn btn-outline-danger" onclick="PrivacyZones.deleteZone('${zone.id}')" title="Delete">
                            <i class="bi bi-trash"></i>
                        </button>
                    </div>
                </div>
            `;
            listEl.appendChild(item);
        });
    }

    /**
     * Edit a privacy zone
     */
    function editZone(zoneId) {
        const zone = zones.find(z => z.id === zoneId);
        if (!zone) return;

        startAddingZone();

        // Populate form
        document.getElementById('zoneId').value = zone.id;
        document.getElementById('zoneName').value = zone.name;
        document.getElementById('zoneDescription').value = zone.description || '';
        document.getElementById('zoneRadius').value = zone.radiusMeters;
        document.getElementById('radiusValue').textContent = zone.radiusMeters;

        // Place marker on map
        placeMarker(zone.latitude, zone.longitude);
        map.setView([zone.latitude, zone.longitude], 15);

        currentZone = zone;
    }

    /**
     * Toggle a zone's active status
     */
    async function toggleZone(zoneId, isActive) {
        try {
            const response = await FitPubAuth.authenticatedFetch(`/api/privacy-zones/${zoneId}/toggle`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ isActive })
            });

            if (response.ok) {
                FitPub.showAlert(`Privacy zone ${isActive ? 'activated' : 'deactivated'}`, 'success');
                loadZones();
            } else {
                FitPub.showAlert('Failed to toggle privacy zone', 'danger');
            }
        } catch (error) {
            console.error('Toggle zone error:', error);
            FitPub.showAlert('Network error. Please try again.', 'danger');
        }
    }

    /**
     * Delete a privacy zone
     */
    async function deleteZone(zoneId) {
        if (!confirm('Are you sure you want to delete this privacy zone? This will immediately affect all existing activities.')) {
            return;
        }

        try {
            const response = await FitPubAuth.authenticatedFetch(`/api/privacy-zones/${zoneId}`, {
                method: 'DELETE'
            });

            if (response.ok) {
                FitPub.showAlert('Privacy zone deleted', 'success');
                loadZones();
            } else {
                FitPub.showAlert('Failed to delete privacy zone', 'danger');
            }
        } catch (error) {
            console.error('Delete zone error:', error);
            FitPub.showAlert('Network error. Please try again.', 'danger');
        }
    }

    /**
     * Escape HTML to prevent XSS
     */
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Public API
    return {
        init,
        editZone,
        toggleZone,
        deleteZone
    };
})();
