/**
 * Heatmap visualization module
 * Renders user activity heatmap using Leaflet.heat
 */

let heatmapMap = null;
let heatLayer = null;
let loadTimeout = null;
let homeLocation = null; // User's home location {lat, lon, zoom}

/**
 * Initialize the heatmap on page load
 */
document.addEventListener('DOMContentLoaded', async function() {
    // Check authentication
    if (!FitPubAuth.isAuthenticated()) {
        window.location.href = '/login';
        return;
    }

    // Fetch user's home location from profile
    await fetchHomeLocation();

    // Show the map container and initialize map FIRST
    // This must happen before loading data so we can use viewport bounds
    const heatmapContainer = document.getElementById('heatmapContainer');
    heatmapContainer.style.display = 'block';
    initializeMap();

    // Load initial heatmap data for current viewport
    await loadHeatmap(true);  // useViewport=true for viewport-based loading

    // Attach rebuild button handler (in stats card)
    const rebuildBtn = document.getElementById('rebuildBtn');
    if (rebuildBtn) {
        rebuildBtn.addEventListener('click', rebuildHeatmap);
    }

    // Attach rebuild button handler (in empty state)
    const emptyRebuildBtn = document.getElementById('emptyRebuildBtn');
    if (emptyRebuildBtn) {
        emptyRebuildBtn.addEventListener('click', rebuildHeatmap);
    }

    // Attach "Set as Home" button handler
    const setHomeBtn = document.getElementById('setHomeBtn');
    if (setHomeBtn) {
        setHomeBtn.addEventListener('click', setAsHomeLocation);
    }

    // Add map move/zoom listener for lazy loading
    setupMapListeners();
});

/**
 * Fetch user's home location from profile settings
 */
async function fetchHomeLocation() {
    try {
        const response = await FitPubAuth.authenticatedFetch('/api/users/me');
        if (response.ok) {
            const user = await response.json();
            if (user.homeLatitude && user.homeLongitude) {
                homeLocation = {
                    lat: user.homeLatitude,
                    lon: user.homeLongitude,
                    zoom: user.homeZoom || 13
                };
                console.log('Home location loaded:', homeLocation);
            }
        }
    } catch (error) {
        console.warn('Could not fetch home location:', error);
    }
}

/**
 * Load and render the heatmap for the current viewport
 * On initial load, fetches all data. On subsequent loads (pan/zoom), uses viewport bounds.
 */
async function loadHeatmap(useViewport = false) {
    const loadingIndicator = document.getElementById('loadingIndicator');
    const errorAlert = document.getElementById('errorAlert');
    const errorMessage = document.getElementById('errorMessage');
    const emptyStateNoActivities = document.getElementById('emptyStateNoActivities');
    const emptyStateNotBuilt = document.getElementById('emptyStateNotBuilt');
    const heatmapContainer = document.getElementById('heatmapContainer');
    const statsCard = document.getElementById('statsCard');
    const legend = document.getElementById('legend');

    // Show loading
    loadingIndicator.style.display = 'block';
    errorAlert.classList.add('d-none');
    emptyStateNoActivities.classList.add('d-none');
    emptyStateNotBuilt.classList.add('d-none');
    heatmapContainer.style.display = 'none';
    statsCard.style.display = 'none';
    legend.style.display = 'none';

    try {
        let url = '/api/heatmap/me';
        const params = new URLSearchParams();

        // Only use viewport bounds if explicitly requested (on pan/zoom)
        if (useViewport && heatmapMap) {
            const bounds = heatmapMap.getBounds();
            const sw = bounds.getSouthWest();
            const ne = bounds.getNorthEast();
            const zoom = heatmapMap.getZoom();

            // Validate bounds (ensure it's not a single point)
            if (sw.lng !== ne.lng && sw.lat !== ne.lat) {
                params.append('minLon', sw.lng);
                params.append('minLat', sw.lat);
                params.append('maxLon', ne.lng);
                params.append('maxLat', ne.lat);
                params.append('zoom', zoom);
                console.log('Loading heatmap for viewport (zoom:', zoom, '):', {minLon: sw.lng, minLat: sw.lat, maxLon: ne.lng, maxLat: ne.lat});
            } else {
                console.log('Invalid bounds detected (single point), loading all data');
            }
        } else {
            console.log('Loading all heatmap data (initial load)');
        }

        // Build URL with parameters
        if (params.toString()) {
            url += '?' + params.toString();
        }

        // Fetch heatmap data
        const response = await FitPubAuth.authenticatedFetch(url);

        if (!response.ok) {
            throw new Error('Failed to load heatmap data');
        }

        const data = await response.json();
        console.log(`Loaded ${data.features.length} grid cells for current viewport`);

        // Hide loading
        loadingIndicator.style.display = 'none';

        // Check if user has any data
        if (!data.features || data.features.length === 0) {
            // Hide map container when showing empty state
            heatmapContainer.style.display = 'none';

            // Check if user has activities but no heatmap
            if (data.activityCount && data.activityCount > 0) {
                // User has activities but heatmap not built
                document.getElementById('emptyActivityCount').textContent = data.activityCount;
                emptyStateNotBuilt.classList.remove('d-none');
            } else {
                // User has no activities at all
                emptyStateNoActivities.classList.remove('d-none');
            }
            return;
        }

        // Ensure map container is visible (should already be visible from initialization)
        heatmapContainer.style.display = 'block';

        // Show stats and legend
        statsCard.style.display = 'block';
        legend.style.display = 'block';

        // Update stats
        document.getElementById('cellCount').textContent = data.features.length.toLocaleString();
        document.getElementById('maxIntensity').textContent = data.maxIntensity.toLocaleString();

        // Render heatmap
        renderHeatmap(data);

    } catch (error) {
        console.error('Error loading heatmap:', error);
        loadingIndicator.style.display = 'none';
        errorAlert.classList.remove('d-none');
        errorMessage.textContent = 'Failed to load heatmap. Please try again later.';
    }
}

/**
 * Initialize the Leaflet map
 */
function initializeMap() {
    if (heatmapMap) {
        return; // Already initialized
    }

    // Use home location if available, otherwise default to world view
    let initialView, initialZoom;
    if (homeLocation) {
        initialView = [homeLocation.lat, homeLocation.lon];
        initialZoom = homeLocation.zoom;
        console.log('Map starting at home location:', initialView, 'zoom:', initialZoom);
    } else {
        initialView = [20, 0]; // World view
        initialZoom = 2;
        console.log('Map starting at default world view');
    }

    // Create map
    heatmapMap = L.map('heatmapContainer').setView(initialView, initialZoom);

    // Add OpenStreetMap tile layer
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
        maxZoom: 18
    }).addTo(heatmapMap);
}

/**
 * Setup map event listeners for lazy loading
 */
function setupMapListeners() {
    // Reload heatmap data when map is moved or zoomed
    heatmapMap.on('moveend', function() {
        // Debounce: wait 500ms after last move before reloading
        if (loadTimeout) {
            clearTimeout(loadTimeout);
        }
        loadTimeout = setTimeout(async () => {
            console.log('Map moved, reloading viewport data...');
            await loadHeatmapViewport();
        }, 500);
    });
}

/**
 * Render heatmap layer from GeoJSON data
 */
function renderHeatmap(data) {
    // Validate data
    if (!data || !data.features || data.features.length === 0) {
        console.warn('No features to render in heatmap');
        return;
    }

    // Ensure maxIntensity is valid
    const maxIntensity = data.maxIntensity || 1;
    if (maxIntensity <= 0) {
        console.warn('Invalid maxIntensity:', data.maxIntensity, '- using default value 1');
    }

    // Convert GeoJSON features to Leaflet.heat format: [lat, lon, intensity]
    const heatData = data.features
        .filter(feature => {
            // Filter out features with invalid coordinates
            const lon = feature.geometry.coordinates[0];
            const lat = feature.geometry.coordinates[1];
            const intensity = feature.properties.intensity;

            const isValid = (
                typeof lon === 'number' && isFinite(lon) &&
                typeof lat === 'number' && isFinite(lat) &&
                typeof intensity === 'number' && isFinite(intensity) &&
                intensity > 0
            );

            if (!isValid) {
                console.warn('Skipping invalid feature:', {lon, lat, intensity});
            }

            return isValid;
        })
        .map(feature => {
            const lon = feature.geometry.coordinates[0];
            const lat = feature.geometry.coordinates[1];
            const intensity = feature.properties.intensity;

            // Use logarithmic scaling for better differentiation between low and high values
            // log(1 + x) ensures that intensity=1 is still visible
            const logMax = Math.log(1 + maxIntensity);
            const logIntensity = Math.log(1 + intensity);

            // Prevent division by zero and ensure valid range [0, 1]
            let normalizedIntensity = 0.5; // Default fallback
            if (logMax > 0) {
                normalizedIntensity = Math.min(Math.max(logIntensity / logMax, 0), 1.0);
            }

            return [lat, lon, normalizedIntensity];
        });

    // Check if we have valid data to render
    if (heatData.length === 0) {
        console.warn('No valid heatmap data after filtering');
        return;
    }

    console.log(`Rendering ${heatData.length} valid heatmap points`);

    // Remove existing heat layer if present
    if (heatLayer) {
        heatmapMap.removeLayer(heatLayer);
    }

    // Get current zoom level for dynamic radius
    const currentZoom = heatmapMap.getZoom();

    // Calculate dynamic radius based on zoom level
    // Higher zoom = smaller radius for more detail
    // Lower zoom = larger radius for better visibility
    const dynamicRadius = calculateDynamicRadius(currentZoom);
    const dynamicBlur = Math.max(4, dynamicRadius * 0.4);  // Reduced blur for sharper appearance

    // Create heat layer with red color scheme and improved gradient
    heatLayer = L.heatLayer(heatData, {
        radius: dynamicRadius,
        blur: dynamicBlur,
        maxZoom: 18,
        max: 0.75,           // Increased to concentrate color at hotspots
        minOpacity: 0.25,    // Reduced for more transparency over streets
        gradient: {
            0.0: 'rgba(0, 0, 0, 0)',          // Transparent
            0.1: 'rgba(0, 0, 139, 0.2)',       // Dark blue (very low values) - more transparent
            0.2: 'rgba(0, 0, 255, 0.35)',      // Blue (low values) - more transparent
            0.3: 'rgba(0, 128, 255, 0.45)',    // Light blue - more transparent
            0.4: 'rgba(0, 255, 255, 0.55)',    // Cyan - more transparent
            0.5: 'rgba(0, 255, 0, 0.6)',       // Green - more transparent
            0.6: 'rgba(255, 255, 0, 0.65)',    // Yellow - more transparent
            0.7: 'rgba(255, 165, 0, 0.7)',     // Orange - more transparent
            0.85: 'rgba(255, 69, 0, 0.8)',     // Red-orange
            1.0: 'rgba(255, 0, 0, 0.85)'       // Red (highest intensity) - slightly transparent
        }
    }).addTo(heatmapMap);

    // Update radius dynamically when zoom changes
    heatmapMap.on('zoomend', function() {
        if (heatLayer) {
            const newZoom = heatmapMap.getZoom();
            const newRadius = calculateDynamicRadius(newZoom);
            const newBlur = Math.max(4, newRadius * 0.4);  // Reduced blur for sharper appearance

            // Update heat layer options
            heatLayer.setOptions({
                radius: newRadius,
                blur: newBlur
            });
        }
    });

    // Fit map bounds to heatmap data
    fitMapToBounds(data.features);
}

/**
 * Calculate dynamic radius based on zoom level.
 * Higher zoom = smaller radius for more granular detail.
 * Lower zoom = larger radius for better visibility.
 *
 * @param {number} zoom - Current map zoom level (0-18)
 * @returns {number} Radius in pixels
 */
function calculateDynamicRadius(zoom) {
    // Zoom levels:
    // 2-8: World/continent view - large radius
    // 9-12: City view - medium radius
    // 13-15: Neighborhood view - small radius
    // 16-18: Street view - very small radius

    if (zoom <= 8) {
        return 25;  // Large radius for world view
    } else if (zoom <= 12) {
        return 20 - (zoom - 8);  // 20 -> 16
    } else if (zoom <= 15) {
        return 16 - (zoom - 12) * 2;  // 16 -> 10
    } else {
        return Math.max(6, 10 - (zoom - 15) * 2);  // 10 -> 6 (minimum)
    }
}

/**
 * Load heatmap data for current viewport only (without UI updates)
 */
async function loadHeatmapViewport() {
    try {
        // Get current map bounds and zoom
        const bounds = heatmapMap.getBounds();
        const sw = bounds.getSouthWest();
        const ne = bounds.getNorthEast();
        const zoom = heatmapMap.getZoom();

        // Validate bounds (ensure it's not a single point)
        if (sw.lng === ne.lng || sw.lat === ne.lat) {
            console.warn('Invalid bounds detected (single point), skipping viewport reload');
            return;
        }

        // Build URL with bounding box parameters and zoom level
        const url = `/api/heatmap/me?minLon=${sw.lng}&minLat=${sw.lat}&maxLon=${ne.lng}&maxLat=${ne.lat}&zoom=${zoom}`;
        console.log('Reloading heatmap for viewport (zoom:', zoom, '):', {minLon: sw.lng, minLat: sw.lat, maxLon: ne.lng, maxLat: ne.lat});

        // Fetch heatmap data for current viewport
        const response = await FitPubAuth.authenticatedFetch(url);

        if (!response.ok) {
            console.error('Failed to reload heatmap data');
            return;
        }

        const data = await response.json();
        console.log(`Reloaded ${data.features.length} grid cells for viewport`);

        // Update stats
        document.getElementById('cellCount').textContent = data.features.length.toLocaleString();

        // Render heatmap
        renderHeatmap(data);

    } catch (error) {
        console.error('Error reloading heatmap viewport:', error);
    }
}

/**
 * Fit map to show all heatmap data (only used on initial load without home location)
 */
function fitMapToBounds(features) {
    // Don't auto-fit if user has a home location set
    if (homeLocation) {
        console.log('Skipping auto-fit, using home location');
        return;
    }

    if (features.length === 0) {
        return;
    }

    // Calculate bounds
    let minLat = Infinity;
    let maxLat = -Infinity;
    let minLon = Infinity;
    let maxLon = -Infinity;

    features.forEach(feature => {
        const lon = feature.geometry.coordinates[0];
        const lat = feature.geometry.coordinates[1];

        minLat = Math.min(minLat, lat);
        maxLat = Math.max(maxLat, lat);
        minLon = Math.min(minLon, lon);
        maxLon = Math.max(maxLon, lon);
    });

    // Add padding
    const latPadding = (maxLat - minLat) * 0.1;
    const lonPadding = (maxLon - minLon) * 0.1;

    const bounds = [
        [minLat - latPadding, minLon - lonPadding],
        [maxLat + latPadding, maxLon + lonPadding]
    ];

    heatmapMap.fitBounds(bounds);
}

/**
 * Rebuild the heatmap by triggering a full recalculation
 */
async function rebuildHeatmap() {
    const rebuildBtn = document.getElementById('rebuildBtn');
    const originalContent = rebuildBtn.innerHTML;
    const errorAlert = document.getElementById('errorAlert');
    const errorMessage = document.getElementById('errorMessage');

    // Disable button and show loading state
    rebuildBtn.disabled = true;
    rebuildBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Rebuilding...';
    errorAlert.classList.add('d-none');

    try {
        // Call rebuild endpoint
        const response = await FitPubAuth.authenticatedFetch('/api/heatmap/me/rebuild', {
            method: 'POST'
        });

        if (!response.ok) {
            throw new Error('Failed to rebuild heatmap');
        }

        const result = await response.json();

        // Show success message
        FitPub.showAlert('success', result.message || 'Heatmap rebuilt successfully!');

        // Ensure map is initialized before reloading
        // (In case user is rebuilding from empty state)
        const heatmapContainer = document.getElementById('heatmapContainer');
        if (!heatmapMap) {
            heatmapContainer.style.display = 'block';
            initializeMap();
        }

        // Reload the heatmap for current viewport
        // This prevents loading 16MB+ of data for users with many activities
        await loadHeatmap(true);

    } catch (error) {
        console.error('Error rebuilding heatmap:', error);
        errorAlert.classList.remove('d-none');
        errorMessage.textContent = 'Failed to rebuild heatmap. Please try again later.';
    } finally {
        // Restore button state
        rebuildBtn.disabled = false;
        rebuildBtn.innerHTML = originalContent;
    }
}

/**
 * Save current map view as home location
 */
async function setAsHomeLocation() {
    const setHomeBtn = document.getElementById('setHomeBtn');
    const originalContent = setHomeBtn.innerHTML;

    if (!heatmapMap) {
        console.error('Map not initialized');
        return;
    }

    // Disable button and show loading state
    setHomeBtn.disabled = true;
    setHomeBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Saving...';

    try {
        // Get current map center and zoom
        const center = heatmapMap.getCenter();
        const zoom = heatmapMap.getZoom();

        console.log('Saving home location:', {lat: center.lat, lon: center.lng, zoom: zoom});

        // Update user profile with home location
        const response = await FitPubAuth.authenticatedFetch('/api/users/me', {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                homeLatitude: center.lat,
                homeLongitude: center.lng,
                homeZoom: zoom
            })
        });

        if (!response.ok) {
            throw new Error('Failed to save home location');
        }

        // Update local homeLocation variable
        homeLocation = {
            lat: center.lat,
            lon: center.lng,
            zoom: zoom
        };

        // Show success message
        FitPub.showAlert('success', 'Home location saved! The map will start here next time.');

    } catch (error) {
        console.error('Error saving home location:', error);
        FitPub.showAlert('danger', 'Failed to save home location. Please try again.');
    } finally {
        // Restore button state
        setHomeBtn.disabled = false;
        setHomeBtn.innerHTML = originalContent;
    }
}
