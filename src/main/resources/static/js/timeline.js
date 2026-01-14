/**
 * Timeline functionality for FitPub
 * Handles loading and displaying timeline activities with preview maps
 */

const FitPubTimeline = {
    currentPage: 0,
    totalPages: 0,
    timelineType: 'public',
    searchText: '',
    dateFilter: '',
    searchDebounceTimer: null,

    /**
     * Initialize the timeline
     * @param {string} type - Timeline type: 'public', 'federated', or 'user'
     */
    init: function(type) {
        this.timelineType = type;
        this.setupSearchHandlers();
        this.loadTimeline(0);
    },

    /**
     * Load timeline activities
     * @param {number} page - Page number to load
     */
    loadTimeline: async function(page) {
        const loadingIndicator = document.getElementById('loadingIndicator');
        const errorAlert = document.getElementById('errorAlert');
        const errorMessage = document.getElementById('errorMessage');
        const timelineList = document.getElementById('timelineList');
        const emptyState = document.getElementById('emptyState');
        const pagination = document.getElementById('pagination');

        try {
            // Show loading
            loadingIndicator.classList.remove('d-none');
            timelineList.classList.add('d-none');
            emptyState.classList.add('d-none');
            errorAlert.classList.add('d-none');
            pagination.classList.add('d-none');

            // Determine endpoint
            let endpoint;
            let fetchOptions = {};

            switch (this.timelineType) {
                case 'public':
                    endpoint = `/api/timeline/public?page=${page}&size=20`;
                    // Public timeline is optionally authenticated
                    fetchOptions = { useAuth: FitPubAuth.isAuthenticated() };
                    break;
                case 'federated':
                    endpoint = `/api/timeline/federated?page=${page}&size=20`;
                    fetchOptions = { useAuth: true };
                    break;
                case 'user':
                    endpoint = `/api/timeline/user?page=${page}&size=20`;
                    fetchOptions = { useAuth: true };
                    break;
                default:
                    throw new Error('Invalid timeline type');
            }

            // Append search parameters if present
            if (this.searchText) {
                endpoint += `&search=${encodeURIComponent(this.searchText)}`;
            }

            if (this.dateFilter) {
                // Only add if valid format
                const validation = this.validateDateFormat(this.dateFilter);
                if (validation.valid) {
                    endpoint += `&date=${encodeURIComponent(this.dateFilter)}`;
                }
            }

            // Fetch timeline data
            const response = fetchOptions.useAuth
                ? await FitPubAuth.authenticatedFetch(endpoint)
                : await fetch(endpoint);

            if (response.ok) {
                const data = await response.json();

                // Hide loading
                loadingIndicator.classList.add('d-none');

                if (data.content && data.content.length > 0) {
                    this.renderTimeline(data.content);
                    this.renderPagination(data);
                    timelineList.classList.remove('d-none');
                    pagination.classList.remove('d-none');
                } else {
                    this.showEmptyState(emptyState);
                }

                this.totalPages = data.totalPages;
                this.currentPage = data.number;
            } else {
                throw new Error('Failed to load timeline');
            }
        } catch (error) {
            console.error('Error loading timeline:', error);
            loadingIndicator.classList.add('d-none');
            errorMessage.textContent = 'Failed to load timeline. Please try again.';
            errorAlert.classList.remove('d-none');
        }
    },

    /**
     * Render timeline activities
     * @param {Array} activities - Array of timeline activity objects
     */
    renderTimeline: function(activities) {
        const timelineList = document.getElementById('timelineList');

        timelineList.innerHTML = activities.map((activity, index) => {
            const mapId = `map-${activity.id}`;

            return `
                <div class="timeline-card card mb-4${activity.race ? ' race-card' : ''}">
                    <div class="card-body">
                        <!-- User Info -->
                        <div class="d-flex align-items-center mb-3">
                            <a href="/users/${activity.username}" class="user-avatar me-3 text-decoration-none">
                                ${activity.avatarUrl
                                    ? `<img src="${activity.avatarUrl}" alt="${this.escapeHtml(activity.displayName || activity.username)}" class="rounded-circle" width="48" height="48">`
                                    : `<div class="avatar-placeholder rounded-circle">
                                        <i class="bi bi-person-circle"></i>
                                       </div>`
                                }
                            </a>
                            <div class="flex-grow-1">
                                <a href="/users/${activity.username}" class="text-decoration-none text-dark">
                                    <div class="fw-bold">${this.escapeHtml(activity.displayName || activity.username)}</div>
                                </a>
                                <div class="text-muted small">
                                    <a href="/users/${activity.username}" class="text-decoration-none text-muted">
                                        @${this.escapeHtml(activity.username)}
                                    </a>
                                    ${!activity.isLocal ? ' <span class="badge bg-info ms-1" title="Federated Activity"><i class="bi bi-globe2"></i> Remote</span>' : ''}
                                    • ${this.formatTimeAgo(activity.startedAt)}
                                </div>
                            </div>
                            <div>
                                <span class="activity-type-badge activity-type-${activity.activityType.toLowerCase()}${activity.race ? ' race-activity' : ''}">
                                    ${activity.activityType}
                                </span>
                                ${activity.race
                                    ? `<span class="badge race-badge ms-2" title="Race/Competition">
                                        <i class="bi bi-flag-checkered"></i> Race
                                       </span>`
                                    : ''
                                }
                                ${activity.indoor
                                    ? `<span class="badge bg-warning text-dark ms-2" title="${activity.indoorDetectionMethod ? 'Detected via: ' + activity.indoorDetectionMethod : 'Indoor Activity'}">
                                        <i class="bi bi-house-door"></i> Indoor
                                       </span>`
                                    : ''
                                }
                            </div>
                        </div>

                        <!-- Activity Title and Description -->
                        <h5 class="card-title">
                            ${activity.isLocal
                                ? `<a href="/activities/${activity.id}" class="text-decoration-none text-dark">
                                    ${this.escapeHtml(activity.title || 'Untitled Activity')}
                                   </a>`
                                : `<a href="${activity.activityUri || '#'}" target="_blank" class="text-decoration-none text-dark">
                                    ${this.escapeHtml(activity.title || 'Untitled Activity')}
                                    <i class="bi bi-box-arrow-up-right ms-1 small"></i>
                                   </a>`
                            }
                        </h5>

                        ${activity.description
                            ? `<p class="card-text">${this.escapeHtml(activity.description).substring(0, 200)}${activity.description.length > 200 ? '...' : ''}</p>`
                            : ''
                        }

                        <!-- Activity Metrics -->
                        <div class="mb-2">
                            <small class="text-muted">
                                ${activity.hasGpsTrack
                                    ? `<strong>Distance:</strong> ${this.formatDistance(activity.totalDistance)} •
                                       <strong>Duration:</strong> ${this.formatDuration(activity.totalDurationSeconds)}
                                       ${activity.movingTimeSeconds && activity.movingTimeSeconds < activity.totalDurationSeconds ? ` • <strong>Moving:</strong> ${this.formatDuration(activity.movingTimeSeconds)}` : ''} •
                                       <strong>Pace:</strong> ${this.formatPace(activity.totalDurationSeconds, activity.totalDistance)} •
                                       <strong>Elevation:</strong> ${activity.elevationGain ? Math.round(activity.elevationGain) + 'm' : 'N/A'}`
                                    : `<strong>Duration:</strong> ${this.formatDuration(activity.totalDurationSeconds)}
                                       ${activity.movingTimeSeconds && activity.movingTimeSeconds < activity.totalDurationSeconds ? ` • <strong>Moving:</strong> ${this.formatDuration(activity.movingTimeSeconds)}` : ''}
                                       ${activity.metrics?.averageHeartRate ? ` • <strong>Avg HR:</strong> ${activity.metrics.averageHeartRate} bpm` : ''}
                                       ${activity.metrics?.calories ? ` • <strong>Calories:</strong> ${activity.metrics.calories} kcal` : ''}`
                                }
                            </small>
                        </div>

                        <!-- Preview Map or Indoor Placeholder -->
                        <div class="activity-preview-map" id="${mapId}" style="height: 300px; border-radius: 8px; margin-bottom: 1rem;">
                            <!-- Map or placeholder will be rendered here -->
                        </div>

                        <!-- Activity Actions -->
                        <div class="d-flex gap-2 align-items-center">
                            <button
                                class="btn btn-sm ${activity.likedByCurrentUser ? 'btn-danger' : 'btn-outline-danger'} like-btn"
                                data-activity-id="${activity.id}"
                                data-liked="${activity.likedByCurrentUser || false}"
                            >
                                <i class="bi bi-heart${activity.likedByCurrentUser ? '-fill' : ''}"></i>
                                <span class="like-count">${activity.likesCount || 0}</span>
                            </button>
                            ${activity.isLocal
                                ? `<a href="/activities/${activity.id}" class="btn btn-sm btn-outline-primary">
                                    <i class="bi bi-eye"></i> View Details
                                   </a>`
                                : `<a href="${activity.activityUri || '#'}" target="_blank" class="btn btn-sm btn-outline-primary">
                                    <i class="bi bi-box-arrow-up-right"></i> View on Origin Server
                                   </a>`
                            }
                            <span class="ms-auto text-muted small d-flex align-items-center gap-2">
                                ${activity.commentsCount > 0 ? `<span><i class="bi bi-chat-left-text"></i> ${activity.commentsCount}</span>` : ''}
                                <span>
                                    <i class="bi bi-${this.getVisibilityIcon(activity.visibility)}"></i>
                                    ${activity.visibility}
                                </span>
                            </span>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        // Render maps after DOM is updated
        setTimeout(() => {
            activities.forEach(activity => {
                this.renderPreviewMap(activity);
            });
        }, 100);

        // Setup like button handlers
        this.setupLikeButtons();
    },

    /**
     * Setup like button click handlers
     */
    setupLikeButtons: function() {
        const likeButtons = document.querySelectorAll('.like-btn');

        likeButtons.forEach(btn => {
            btn.addEventListener('click', async (e) => {
                e.preventDefault();

                // Check if user is authenticated
                if (!FitPubAuth.isAuthenticated()) {
                    window.location.href = '/login';
                    return;
                }

                const activityId = btn.dataset.activityId;
                const isLiked = btn.dataset.liked === 'true';
                const icon = btn.querySelector('i');
                const countSpan = btn.querySelector('.like-count');

                try {
                    // Disable button during request
                    btn.disabled = true;

                    if (isLiked) {
                        // Unlike
                        const response = await FitPubAuth.authenticatedFetch(
                            `/api/activities/${activityId}/likes`,
                            { method: 'DELETE' }
                        );

                        if (response.ok) {
                            // Update UI
                            btn.classList.remove('btn-danger');
                            btn.classList.add('btn-outline-danger');
                            icon.classList.remove('bi-heart-fill');
                            icon.classList.add('bi-heart');
                            btn.dataset.liked = 'false';

                            // Update count
                            const currentCount = parseInt(countSpan.textContent) || 0;
                            countSpan.textContent = Math.max(0, currentCount - 1);
                        }
                    } else {
                        // Like
                        const response = await FitPubAuth.authenticatedFetch(
                            `/api/activities/${activityId}/likes`,
                            { method: 'POST' }
                        );

                        if (response.ok) {
                            // Update UI
                            btn.classList.remove('btn-outline-danger');
                            btn.classList.add('btn-danger');
                            icon.classList.remove('bi-heart');
                            icon.classList.add('bi-heart-fill');
                            btn.dataset.liked = 'true';

                            // Update count
                            const currentCount = parseInt(countSpan.textContent) || 0;
                            countSpan.textContent = currentCount + 1;
                        }
                    }
                } catch (error) {
                    console.error('Error toggling like:', error);
                } finally {
                    btn.disabled = false;
                }
            });
        });
    },

    /**
     * Render preview map for an activity
     * @param {Object} activity - Activity object
     */
    renderPreviewMap: async function(activity) {
        const mapId = `map-${activity.id}`;
        const mapElement = document.getElementById(mapId);

        if (!mapElement) {
            console.warn('Map element not found:', mapId);
            return;
        }

        // Check if activity has GPS track
        if (!activity.hasGpsTrack) {
            // Show indoor activity placeholder
            this.renderIndoorPlaceholder(mapElement, activity.activityType);
            return;
        }

        // Handle remote activities differently - show static map image
        if (!activity.isLocal) {
            if (activity.mapImageUrl) {
                mapElement.innerHTML = `
                    <div class="position-relative w-100 h-100">
                        <img src="${this.escapeHtml(activity.mapImageUrl)}"
                             alt="Activity Map"
                             class="img-fluid w-100 h-100"
                             style="object-fit: cover; border-radius: 8px;"
                             onerror="this.parentElement.innerHTML='<div class=\\'d-flex align-items-center justify-content-center h-100 bg-light\\'><p class=\\'text-muted\\'>Map not available</p></div>'">
                        <div class="position-absolute top-0 end-0 m-2">
                            <span class="badge bg-secondary">
                                <i class="bi bi-globe2"></i> Remote Map
                            </span>
                        </div>
                    </div>
                `;
            } else {
                mapElement.innerHTML = '<div class="d-flex align-items-center justify-content-center h-100 bg-light"><p class="text-muted">No map available for this remote activity</p></div>';
            }
            return;
        }

        // Handle local activities - render interactive Leaflet map
        try {
            // Fetch track data
            const response = await fetch(`/api/activities/${activity.id}/track`);

            if (!response.ok) {
                throw new Error('Failed to load track data');
            }

            const trackData = await response.json();

            if (!trackData.features || trackData.features.length === 0) {
                mapElement.innerHTML = '<div class="d-flex align-items-center justify-content-center h-100 bg-light"><p class="text-muted">No GPS data available</p></div>';
                return;
            }

            // Initialize map
            const map = L.map(mapId, {
                zoomControl: false,
                scrollWheelZoom: false,
                dragging: false,
                touchZoom: false
            });

            // Add tile layer
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '© OpenStreetMap contributors',
                maxZoom: 18
            }).addTo(map);

            // Add track to map
            const geoJsonLayer = L.geoJSON(trackData, {
                style: {
                    color: '#0d6efd',
                    weight: 3,
                    opacity: 0.8
                }
            }).addTo(map);

            // Fit map to track bounds
            map.fitBounds(geoJsonLayer.getBounds(), { padding: [20, 20] });

            // Privacy: Start/finish markers removed to protect athlete home locations

        } catch (error) {
            console.error('Error rendering map:', error);
            mapElement.innerHTML = '<div class="d-flex align-items-center justify-content-center h-100 bg-light"><p class="text-muted">Failed to load map</p></div>';
        }
    },

    /**
     * Render pagination controls
     * @param {Object} data - Pagination data from API
     */
    renderPagination: function(data) {
        const paginationList = document.getElementById('paginationList');
        let html = '';

        // Previous button
        html += `
            <li class="page-item ${data.first ? 'disabled' : ''}">
                <a class="page-link" href="#" onclick="FitPubTimeline.changePage(${data.number - 1}); return false;">
                    <i class="bi bi-chevron-left"></i>
                </a>
            </li>
        `;

        // Page numbers
        const startPage = Math.max(0, data.number - 2);
        const endPage = Math.min(data.totalPages - 1, data.number + 2);

        if (startPage > 0) {
            html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }

        for (let i = startPage; i <= endPage; i++) {
            html += `
                <li class="page-item ${i === data.number ? 'active' : ''}">
                    <a class="page-link" href="#" onclick="FitPubTimeline.changePage(${i}); return false;">${i + 1}</a>
                </li>
            `;
        }

        if (endPage < data.totalPages - 1) {
            html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }

        // Next button
        html += `
            <li class="page-item ${data.last ? 'disabled' : ''}">
                <a class="page-link" href="#" onclick="FitPubTimeline.changePage(${data.number + 1}); return false;">
                    <i class="bi bi-chevron-right"></i>
                </a>
            </li>
        `;

        paginationList.innerHTML = html;
    },

    /**
     * Change page
     * @param {number} page - Page number
     */
    changePage: function(page) {
        this.loadTimeline(page);
        window.scrollTo({ top: 0, behavior: 'smooth' });
    },

    /**
     * Format distance in meters to km
     * @param {number} meters - Distance in meters
     * @returns {string} Formatted distance
     */
    formatDistance: function(meters) {
        if (!meters) return 'N/A';
        if (meters >= 1000) {
            return (meters / 1000).toFixed(1) + ' km';
        }
        return Math.round(meters) + ' m';
    },

    /**
     * Format duration in seconds
     * @param {number} seconds - Duration in seconds
     * @returns {string} Formatted duration
     */
    formatDuration: function(seconds) {
        if (!seconds) return 'N/A';
        const hours = Math.floor(seconds / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        const secs = Math.floor(seconds % 60);

        if (hours > 0) {
            return `${hours}h ${minutes}m`;
        }
        if (minutes > 0) {
            return `${minutes}m ${secs}s`;
        }
        return `${secs}s`;
    },

    /**
     * Format pace (min/km)
     * @param {number} seconds - Total duration in seconds
     * @param {number} meters - Total distance in meters
     * @returns {string} Formatted pace
     */
    formatPace: function(seconds, meters) {
        if (!seconds || !meters || meters === 0) return 'N/A';

        const km = meters / 1000;
        const paceSeconds = seconds / km;
        const paceMinutes = Math.floor(paceSeconds / 60);
        const paceSecs = Math.floor(paceSeconds % 60);

        return `${paceMinutes}:${paceSecs.toString().padStart(2, '0')}/km`;
    },

    /**
     * Format timestamp to "time ago" format
     * @param {string} timestamp - ISO timestamp
     * @returns {string} Time ago string
     */
    formatTimeAgo: function(timestamp) {
        const date = new Date(timestamp);
        const now = new Date();
        const secondsAgo = Math.floor((now - date) / 1000);

        if (secondsAgo < 60) return 'just now';
        if (secondsAgo < 3600) return `${Math.floor(secondsAgo / 60)}m ago`;
        if (secondsAgo < 86400) return `${Math.floor(secondsAgo / 3600)}h ago`;
        if (secondsAgo < 604800) return `${Math.floor(secondsAgo / 86400)}d ago`;

        return date.toLocaleDateString();
    },

    /**
     * Get visibility icon
     * @param {string} visibility - Visibility level
     * @returns {string} Bootstrap icon name
     */
    getVisibilityIcon: function(visibility) {
        switch (visibility) {
            case 'PUBLIC': return 'globe';
            case 'FOLLOWERS': return 'people';
            case 'PRIVATE': return 'lock';
            default: return 'question-circle';
        }
    },

    /**
     * Escape HTML to prevent XSS
     * @param {string} text - Text to escape
     * @returns {string} Escaped text
     */
    escapeHtml: function(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    },

    /**
     * Render indoor activity placeholder with emoji
     * @param {HTMLElement} element - Container element
     * @param {string} activityType - Activity type
     */
    renderIndoorPlaceholder: function(element, activityType) {
        const emojiMap = {
            'RUN': '🏃',
            'RIDE': '🚴',
            'CYCLING': '🚴',
            'INDOOR_CYCLING': '🚴',
            'HIKE': '🥾',
            'WALK': '🚶',
            'SWIM': '🏊',
            'WORKOUT': '💪',
            'YOGA': '🧘',
            'ALPINE_SKI': '⛷️',
            'NORDIC_SKI': '⛷️',
            'SNOWBOARD': '🏂',
            'ROWING': '🚣',
            'KAYAKING': '🛶',
            'CANOEING': '🛶',
            'ROCK_CLIMBING': '🧗',
            'MOUNTAINEERING': '⛰️',
            'OTHER': '🏋️'
        };

        const nameMap = {
            'RUN': 'Indoor Running',
            'RIDE': 'Indoor Cycling',
            'CYCLING': 'Indoor Cycling',
            'INDOOR_CYCLING': 'Indoor Cycling',
            'HIKE': 'Indoor Activity',
            'WALK': 'Indoor Walking',
            'SWIM': 'Indoor Swimming',
            'WORKOUT': 'Workout',
            'YOGA': 'Yoga',
            'ALPINE_SKI': 'Skiing',
            'NORDIC_SKI': 'Cross-Country Skiing',
            'SNOWBOARD': 'Snowboarding',
            'ROWING': 'Indoor Rowing',
            'KAYAKING': 'Kayaking',
            'CANOEING': 'Canoeing',
            'ROCK_CLIMBING': 'Climbing',
            'MOUNTAINEERING': 'Mountaineering',
            'OTHER': 'Indoor Activity'
        };

        const emoji = emojiMap[activityType] || '🏋️';
        const name = nameMap[activityType] || 'Indoor Activity';

        element.innerHTML = `
            <div class="d-flex flex-column align-items-center justify-content-center h-100 indoor-activity-placeholder">
                <div style="font-size: 4rem;" class="mb-2">${emoji}</div>
                <div class="text-muted fw-bold">${this.escapeHtml(name)}</div>
                <div class="text-muted small">No GPS track</div>
            </div>
        `;
        element.style.backgroundColor = '#f8f9fa';
    },

    /**
     * Setup search input handlers with debounce
     */
    setupSearchHandlers: function() {
        const searchInput = document.getElementById('searchInput');
        const clearBtn = document.getElementById('clearSearchBtn');
        const searchHint = document.getElementById('searchHint');

        if (!searchInput) return;

        // Text search with 300ms debounce
        searchInput.addEventListener('input', (e) => {
            this.searchText = e.target.value.trim();
            this.debouncedSearch();
        });

        // Clear button
        if (clearBtn) {
            clearBtn.addEventListener('click', () => {
                searchInput.value = '';
                this.searchText = '';
                searchHint.textContent = '';
                this.loadTimeline(0);
            });
        }
    },

    /**
     * Validate date format and provide feedback
     * @param {string} dateStr - Date string to validate
     * @returns {Object} Validation result with valid flag and message
     */
    validateDateFormat: function(dateStr) {
        // Year only (yyyy)
        if (/^\d{4}$/.test(dateStr)) {
            const year = parseInt(dateStr);
            if (year >= 1900 && year <= 2100) {
                return { valid: true, message: `Searching all activities in ${year}` };
            }
            return { valid: false, message: 'Invalid year (must be 1900-2100)' };
        }

        // dd.mm.yyyy format
        if (/^\d{2}\.\d{2}\.\d{4}$/.test(dateStr)) {
            const [day, month, year] = dateStr.split('.').map(Number);
            if (this.isValidDate(year, month, day)) {
                return { valid: true, message: `Searching activities on ${dateStr}` };
            }
            return { valid: false, message: 'Invalid date' };
        }

        // yyyy-mm-dd format
        if (/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
            const [year, month, day] = dateStr.split('-').map(Number);
            if (this.isValidDate(year, month, day)) {
                return { valid: true, message: `Searching activities on ${dateStr}` };
            }
            return { valid: false, message: 'Invalid date' };
        }

        // Partial input - don't show error yet
        if (/^\d{1,4}$/.test(dateStr) || /^\d{2}\.\d{0,2}/.test(dateStr) || /^\d{4}-\d{0,2}/.test(dateStr)) {
            return { valid: false, message: 'Enter full date: dd.mm.yyyy, yyyy-mm-dd, or yyyy' };
        }

        return { valid: false, message: 'Use format: dd.mm.yyyy, yyyy-mm-dd, or yyyy' };
    },

    /**
     * Check if date is valid
     * @param {number} year - Year
     * @param {number} month - Month (1-12)
     * @param {number} day - Day (1-31)
     * @returns {boolean} True if valid date
     */
    isValidDate: function(year, month, day) {
        if (month < 1 || month > 12) return false;
        if (day < 1 || day > 31) return false;

        const date = new Date(year, month - 1, day);
        return date.getFullYear() === year &&
               date.getMonth() === month - 1 &&
               date.getDate() === day;
    },

    /**
     * Debounced search with 300ms delay
     */
    debouncedSearch: function() {
        clearTimeout(this.searchDebounceTimer);

        // Show loading hint
        const searchHint = document.getElementById('searchHint');
        if ((this.searchText || this.dateFilter) && searchHint && !searchHint.classList.contains('text-danger')) {
            searchHint.textContent = 'Searching...';
        }

        this.searchDebounceTimer = setTimeout(() => {
            this.currentPage = 0; // Reset to first page
            this.loadTimeline(0)
                .then(i => searchHint.textContent = '');
        }, 300);
    },

    /**
     * Show appropriate empty state based on search
     * @param {HTMLElement} emptyState - Empty state element
     */
    showEmptyState: function(emptyState) {
        const emptyTitle = emptyState.querySelector('h4');
        const emptyMessage = emptyState.querySelector('p.text-muted');

        if (this.searchText || this.dateFilter) {
            if (emptyTitle) emptyTitle.textContent = 'No Activities Found';
            if (emptyMessage) emptyMessage.textContent = 'Try adjusting your search filters or date range.';
        } else {
            // Original empty state messages
            if (emptyTitle) emptyTitle.textContent = 'No Activities Yet';
            if (emptyMessage) emptyMessage.textContent = 'Be the first to share your fitness activities with the community!';
        }

        emptyState.classList.remove('d-none');
    }
};
