// FitPub - Main JavaScript

/**
 * Initialize application when DOM is ready
 */
document.addEventListener('DOMContentLoaded', function() {
    console.log('FitPub initialized');

    // Initialize file upload areas
    initFileUploadAreas();

    // Initialize HTMX event listeners
    initHtmxListeners();
});

/**
 * Initialize drag-and-drop file upload areas
 */
function initFileUploadAreas() {
    const uploadAreas = document.querySelectorAll('.file-upload-area');

    uploadAreas.forEach(area => {
        const fileInput = area.querySelector('input[type="file"]');

        // Drag and drop events
        area.addEventListener('dragover', (e) => {
            e.preventDefault();
            area.classList.add('drag-over');
        });

        area.addEventListener('dragleave', (e) => {
            e.preventDefault();
            area.classList.remove('drag-over');
        });

        area.addEventListener('drop', (e) => {
            e.preventDefault();
            area.classList.remove('drag-over');

            if (e.dataTransfer.files.length > 0) {
                fileInput.files = e.dataTransfer.files;
                updateFileInputLabel(fileInput);
            }
        });

        // Click to upload
        area.addEventListener('click', () => {
            fileInput.click();
        });

        // File input change
        if (fileInput) {
            fileInput.addEventListener('change', () => {
                updateFileInputLabel(fileInput);
            });
        }
    });
}

/**
 * Update file input label with selected file name
 */
function updateFileInputLabel(input) {
    const label = input.parentElement.querySelector('.file-upload-label');
    if (label && input.files.length > 0) {
        const fileName = input.files[0].name;
        label.textContent = fileName;
    }
}

/**
 * Initialize HTMX event listeners for custom behavior
 */
function initHtmxListeners() {
    // Show loading indicator on HTMX requests
    document.body.addEventListener('htmx:beforeRequest', (event) => {
        console.log('HTMX request started:', event.detail.path);
    });

    // Hide loading indicator when request completes
    document.body.addEventListener('htmx:afterRequest', (event) => {
        console.log('HTMX request completed:', event.detail.path);
    });

    // Handle HTMX errors
    document.body.addEventListener('htmx:responseError', (event) => {
        console.error('HTMX error:', event.detail);
        showAlert('An error occurred. Please try again.', 'danger');
    });

    // Scroll to top after swapping content
    document.body.addEventListener('htmx:afterSwap', (event) => {
        if (event.detail.target.id === 'main-content') {
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }
    });
}

/**
 * Create and render a Leaflet map with a GPS track
 *
 * @param {string} containerId - The ID of the map container element
 * @param {Object} geoJsonData - GeoJSON track data (LineString or FeatureCollection)
 * @param {Object} options - Map options
 * @param {boolean} options.showStartEnd - Show start/finish markers (default: true)
 * @param {boolean} options.fitBounds - Auto-fit map to track bounds (default: true)
 * @param {Function} options.onTrackClick - Callback when track is clicked
 * @returns {Object} Leaflet map instance
 */
function createActivityMap(containerId, geoJsonData, options = {}) {
    const container = document.getElementById(containerId);
    if (!container) {
        console.error('Map container not found:', containerId);
        return null;
    }

    // Clear any existing map instance
    if (container._leaflet_id) {
        container._leaflet_id = undefined;
        container.innerHTML = '';
    }

    // Default options
    const defaultOptions = {
        zoomControl: true,
        attributionControl: true,
        scrollWheelZoom: true,
        showStartEnd: true,
        fitBounds: true
    };

    const mapOptions = { ...defaultOptions, ...options };

    // Initialize Leaflet map
    const map = L.map(containerId, {
        zoomControl: mapOptions.zoomControl,
        attributionControl: mapOptions.attributionControl,
        scrollWheelZoom: mapOptions.scrollWheelZoom
    });

    // Add OpenStreetMap tile layer
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
        maxZoom: 19,
        minZoom: 3
    }).addTo(map);

    // Add GeoJSON track if provided
    if (geoJsonData) {
        let trackLayer;

        // Handle both GeoJSON FeatureCollection and plain LineString
        if (geoJsonData.type === 'LineString') {
            trackLayer = L.geoJSON({
                type: 'Feature',
                geometry: geoJsonData,
                properties: {}
            }, {
                style: {
                    color: '#2563eb',
                    weight: 4,
                    opacity: 0.8,
                    lineCap: 'round',
                    lineJoin: 'round'
                },
                onEachFeature: (feature, layer) => {
                    // Add click handler if provided
                    if (mapOptions.onTrackClick) {
                        layer.on('click', (e) => {
                            mapOptions.onTrackClick(e, feature);
                        });
                    }
                }
            }).addTo(map);
        } else {
            trackLayer = L.geoJSON(geoJsonData, {
                style: {
                    color: '#2563eb',
                    weight: 4,
                    opacity: 0.8,
                    lineCap: 'round',
                    lineJoin: 'round'
                },
                onEachFeature: (feature, layer) => {
                    // Add popups with point-in-time metrics if available
                    if (feature.properties) {
                        const props = feature.properties;
                        let popupContent = '<div class="map-popup">';

                        if (props.time) {
                            popupContent += `<strong>Time:</strong> ${new Date(props.time).toLocaleTimeString()}<br>`;
                        }
                        if (props.heartRate) {
                            popupContent += `<strong>Heart Rate:</strong> ${props.heartRate} bpm<br>`;
                        }
                        if (props.speed !== undefined) {
                            const speedKmh = props.speed * 3.6;
                            popupContent += `<strong>Speed:</strong> ${speedKmh.toFixed(2)} km/h<br>`;
                        }
                        if (props.elevation !== undefined) {
                            popupContent += `<strong>Elevation:</strong> ${props.elevation.toFixed(1)} m<br>`;
                        }
                        if (props.cadence) {
                            popupContent += `<strong>Cadence:</strong> ${props.cadence} rpm<br>`;
                        }

                        popupContent += '</div>';
                        layer.bindPopup(popupContent);
                    }

                    // Add click handler if provided
                    if (mapOptions.onTrackClick) {
                        layer.on('click', (e) => {
                            mapOptions.onTrackClick(e, feature);
                        });
                    }
                }
            }).addTo(map);
        }

        // Fit map bounds to track
        if (mapOptions.fitBounds) {
            try {
                const bounds = trackLayer.getBounds();
                console.log('Track bounds:', bounds);
                if (bounds.isValid()) {
                    map.fitBounds(bounds, { padding: [50, 50] });
                    console.log('Map bounds fitted successfully');
                } else {
                    console.warn('Track bounds are invalid');
                }
            } catch (e) {
                console.warn('Could not fit map bounds:', e);
                map.setView([0, 0], 2);
            }
        }

        // Add start/finish markers
        if (mapOptions.showStartEnd) {
            addStartFinishMarkers(map, geoJsonData);
        }

        // Store track layer reference for potential future use
        map.trackLayer = trackLayer;
    } else {
        // No track data, show default view
        map.setView([0, 0], 2);
    }

    // Invalidate size to ensure proper rendering and re-fit bounds
    setTimeout(() => {
        map.invalidateSize();

        // Re-fit bounds after size invalidation if we have a track
        if (mapOptions.fitBounds && map.trackLayer) {
            try {
                const bounds = map.trackLayer.getBounds();
                if (bounds.isValid()) {
                    map.fitBounds(bounds, { padding: [50, 50] });
                    console.log('Map bounds re-fitted after invalidateSize');
                }
            } catch (e) {
                console.warn('Could not re-fit bounds after invalidateSize:', e);
            }
        }
    }, 100);

    return map;
}

/**
 * Add start and finish markers to the map
 * PRIVACY: This function is deprecated and does nothing.
 * Start/finish markers are no longer displayed to protect athlete privacy.
 *
 * @param {Object} map - Leaflet map instance
 * @param {Object} geoJsonData - GeoJSON track data
 */
function addStartFinishMarkers(map, geoJsonData) {
    // Privacy protection: Do not show start/end markers to hide athlete home locations
    return;
}

/**
 * Create an elevation profile chart
 *
 * @param {string} canvasId - The ID of the canvas element
 * @param {Array} elevationData - Array of {distance, elevation} objects
 */
function createElevationChart(canvasId, elevationData) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) {
        console.error('Chart canvas not found:', canvasId);
        return null;
    }

    return new Chart(ctx, {
        type: 'line',
        data: {
            labels: elevationData.map(d => (d.distance / 1000).toFixed(2)),
            datasets: [{
                label: 'Elevation (m)',
                data: elevationData.map(d => d.elevation),
                borderColor: '#10b981',
                backgroundColor: 'rgba(16, 185, 129, 0.1)',
                fill: true,
                tension: 0.4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    display: false
                },
                tooltip: {
                    callbacks: {
                        title: (context) => {
                            return `Distance: ${context[0].label} km`;
                        },
                        label: (context) => {
                            return `Elevation: ${context.parsed.y.toFixed(1)} m`;
                        }
                    }
                }
            },
            scales: {
                x: {
                    title: {
                        display: true,
                        text: 'Distance (km)'
                    }
                },
                y: {
                    title: {
                        display: true,
                        text: 'Elevation (m)'
                    }
                }
            }
        }
    });
}

/**
 * Show an alert message
 *
 * @param {string} message - The message to display
 * @param {string} type - Alert type: success, danger, warning, info
 */
function showAlert(message, type = 'info') {
    const alertDiv = document.createElement('div');
    alertDiv.className = `alert alert-${type} alert-dismissible fade show`;
    alertDiv.setAttribute('role', 'alert');
    alertDiv.innerHTML = `
        ${message}
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    `;

    const container = document.querySelector('main.container');
    if (container) {
        container.insertBefore(alertDiv, container.firstChild);

        // Auto-dismiss after 5 seconds
        setTimeout(() => {
            alertDiv.classList.remove('show');
            setTimeout(() => alertDiv.remove(), 150);
        }, 5000);
    }
}

/**
 * Format duration from seconds to human-readable string
 *
 * @param {number} seconds - Duration in seconds
 * @returns {string} Formatted duration (e.g., "1h 23m 45s")
 */
function formatDuration(seconds) {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = Math.floor(seconds % 60);

    const parts = [];
    if (hours > 0) parts.push(`${hours}h`);
    if (minutes > 0) parts.push(`${minutes}m`);
    if (secs > 0 || parts.length === 0) parts.push(`${secs}s`);

    return parts.join(' ');
}

/**
 * Format distance in meters to human-readable string
 *
 * @param {number} meters - Distance in meters
 * @returns {string} Formatted distance (e.g., "12.34 km" or "856 m")
 */
function formatDistance(meters) {
    if (meters >= 1000) {
        return `${(meters / 1000).toFixed(2)} km`;
    }
    return `${Math.round(meters)} m`;
}

/**
 * Format pace from m/s to min/km
 *
 * @param {number} speed - Speed in m/s
 * @returns {string} Formatted pace (e.g., "5:23 /km")
 */
function formatPace(speed) {
    if (speed === 0) return '--';

    const paceSeconds = 1000 / speed;
    const minutes = Math.floor(paceSeconds / 60);
    const seconds = Math.floor(paceSeconds % 60);

    return `${minutes}:${seconds.toString().padStart(2, '0')} /km`;
}

// Make functions available globally for inline scripts
window.FitPub = {
    createActivityMap,
    createElevationChart,
    showAlert,
    formatDuration,
    formatDistance,
    formatPace
};
