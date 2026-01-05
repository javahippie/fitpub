/**
 * Heatmap visualization module
 * Renders user activity heatmap using Leaflet.heat
 */

let heatmapMap = null;
let heatLayer = null;

/**
 * Initialize the heatmap on page load
 */
document.addEventListener('DOMContentLoaded', async function() {
    // Check authentication
    if (!FitPubAuth.isAuthenticated()) {
        window.location.href = '/login';
        return;
    }

    await loadHeatmap();

    // Attach rebuild button handler
    const rebuildBtn = document.getElementById('rebuildBtn');
    if (rebuildBtn) {
        rebuildBtn.addEventListener('click', rebuildHeatmap);
    }
});

/**
 * Load and render the heatmap
 */
async function loadHeatmap() {
    const loadingIndicator = document.getElementById('loadingIndicator');
    const errorAlert = document.getElementById('errorAlert');
    const errorMessage = document.getElementById('errorMessage');
    const emptyState = document.getElementById('emptyState');
    const heatmapContainer = document.getElementById('heatmapContainer');
    const statsCard = document.getElementById('statsCard');
    const legend = document.getElementById('legend');

    // Show loading
    loadingIndicator.style.display = 'block';
    errorAlert.classList.add('d-none');
    emptyState.classList.add('d-none');
    heatmapContainer.style.display = 'none';
    statsCard.style.display = 'none';
    legend.style.display = 'none';

    try {
        // Fetch heatmap data
        const response = await FitPubAuth.authenticatedFetch('/api/heatmap/me');

        if (!response.ok) {
            throw new Error('Failed to load heatmap data');
        }

        const data = await response.json();

        // Hide loading
        loadingIndicator.style.display = 'none';

        // Check if user has any data
        if (!data.features || data.features.length === 0) {
            emptyState.classList.remove('d-none');
            return;
        }

        // Show map and stats
        heatmapContainer.style.display = 'block';
        statsCard.style.display = 'block';
        legend.style.display = 'block';

        // Update stats
        document.getElementById('cellCount').textContent = data.features.length.toLocaleString();
        document.getElementById('maxIntensity').textContent = data.maxIntensity.toLocaleString();

        // Initialize map
        initializeMap();

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

    // Create map centered on world
    heatmapMap = L.map('heatmapContainer').setView([20, 0], 2);

    // Add OpenStreetMap tile layer
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
        maxZoom: 18
    }).addTo(heatmapMap);
}

/**
 * Render heatmap layer from GeoJSON data
 */
function renderHeatmap(data) {
    // Convert GeoJSON features to Leaflet.heat format: [lat, lon, intensity]
    const heatData = data.features.map(feature => {
        const lon = feature.geometry.coordinates[0];
        const lat = feature.geometry.coordinates[1];
        const intensity = feature.properties.intensity;

        // Use logarithmic scaling for better differentiation between low and high values
        // log(1 + x) ensures that intensity=1 is still visible
        const logMax = Math.log(1 + data.maxIntensity);
        const logIntensity = Math.log(1 + intensity);
        const normalizedIntensity = Math.min(logIntensity / logMax, 1.0);

        return [lat, lon, normalizedIntensity];
    });

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
 * Fit map to show all heatmap data
 */
function fitMapToBounds(features) {
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

        // Reload the heatmap
        await loadHeatmap();

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
