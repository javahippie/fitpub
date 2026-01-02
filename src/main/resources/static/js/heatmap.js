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

        // Normalize intensity to 0-1 range
        const normalizedIntensity = Math.min(intensity / data.maxIntensity, 1.0);

        return [lat, lon, normalizedIntensity];
    });

    // Remove existing heat layer if present
    if (heatLayer) {
        heatmapMap.removeLayer(heatLayer);
    }

    // Create heat layer
    heatLayer = L.heatLayer(heatData, {
        radius: 25,
        blur: 15,
        maxZoom: 17,
        max: 1.0,
        gradient: {
            0.0: 'blue',
            0.4: 'cyan',
            0.6: 'lime',
            0.7: 'yellow',
            0.9: 'orange',
            1.0: 'red'
        }
    }).addTo(heatmapMap);

    // Fit map bounds to heatmap data
    fitMapToBounds(data.features);
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
