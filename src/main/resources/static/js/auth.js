// FitPub - Authentication Management

/**
 * Authentication utilities for managing JWT tokens and user sessions
 */
const FitPubAuth = {
    /**
     * Get the stored JWT token
     * @returns {string|null} JWT token or null if not found
     */
    getToken: function() {
        return localStorage.getItem('jwtToken');
    },

    /**
     * Store JWT token
     * @param {string} token - JWT token to store
     */
    setToken: function(token) {
        localStorage.setItem('jwtToken', token);
    },

    /**
     * Remove stored JWT token
     */
    removeToken: function() {
        localStorage.removeItem('jwtToken');
        localStorage.removeItem('username');
    },

    /**
     * Get the stored username
     * @returns {string|null} Username or null if not found
     */
    getUsername: function() {
        return localStorage.getItem('username');
    },

    /**
     * Store username
     * @param {string} username - Username to store
     */
    setUsername: function(username) {
        localStorage.setItem('username', username);
    },

    /**
     * Check if user is authenticated
     * @returns {boolean} True if authenticated, false otherwise
     */
    isAuthenticated: function() {
        const token = this.getToken();
        if (!token) {
            return false;
        }

        // Check if token is expired
        try {
            const payload = this.parseJwt(token);
            const now = Math.floor(Date.now() / 1000);

            if (payload.exp && payload.exp < now) {
                // Token expired, remove it
                this.removeToken();
                return false;
            }

            return true;
        } catch (e) {
            console.error('Error parsing JWT:', e);
            this.removeToken();
            return false;
        }
    },

    /**
     * Parse JWT token to extract payload
     * @param {string} token - JWT token
     * @returns {object} Decoded payload
     */
    parseJwt: function(token) {
        try {
            const base64Url = token.split('.')[1];
            const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
            const jsonPayload = decodeURIComponent(
                atob(base64).split('').map(function(c) {
                    return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
                }).join('')
            );
            return JSON.parse(jsonPayload);
        } catch (e) {
            console.error('Error parsing JWT:', e);
            throw e;
        }
    },

    /**
     * Get time until token expiration
     * @returns {number} Seconds until expiration, or 0 if expired/invalid
     */
    getTokenExpirationTime: function() {
        const token = this.getToken();
        if (!token) {
            return 0;
        }

        try {
            const payload = this.parseJwt(token);
            const now = Math.floor(Date.now() / 1000);

            if (payload.exp) {
                return Math.max(0, payload.exp - now);
            }

            return 0;
        } catch (e) {
            return 0;
        }
    },

    /**
     * Logout user
     */
    logout: function() {
        this.removeToken();
        window.location.href = '/login';
    },

    /**
     * Make an authenticated API request
     * @param {string} url - API endpoint URL
     * @param {object} options - Fetch options
     * @returns {Promise<Response>} Fetch response
     */
    authenticatedFetch: async function(url, options = {}) {
        const token = this.getToken();

        if (!token) {
            throw new Error('No authentication token found');
        }

        // Add Authorization header
        const headers = {
            ...options.headers,
            'Authorization': `Bearer ${token}`,
        };

        // If body is an object, set Content-Type to JSON
        if (options.body && typeof options.body === 'object') {
            headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(options.body);
        }

        const response = await fetch(url, {
            ...options,
            headers
        });

        // If unauthorized, redirect to login
        if (response.status === 401) {
            this.removeToken();
            window.location.href = '/login';
            throw new Error('Authentication failed');
        }

        return response;
    },

    /**
     * Initialize authentication checks and setup
     */
    init: function() {
        // Update navigation UI based on auth status
        this.updateNavigationUI();

        // Check registration status and update UI
        this.checkRegistrationStatus();

        // Check authentication status on page load
        this.checkAuthStatus();

        // Set up session expiration warning
        this.setupExpirationWarning();
    },

    /**
     * Check if registration is enabled and update navigation UI
     */
    checkRegistrationStatus: async function() {
        try {
            const response = await fetch('/api/auth/registration-status');
            const data = await response.json();

            if (!data.enabled) {
                // Hide registration link in navigation
                const registerLinks = document.querySelectorAll('a[href="/register"]');
                registerLinks.forEach(link => {
                    const parent = link.parentElement;
                    if (parent && parent.tagName === 'LI') {
                        parent.style.display = 'none';
                    }
                });
            }
        } catch (error) {
            console.error('Error checking registration status:', error);
            // Continue without hiding registration link if check fails
        }
    },

    /**
     * Update navigation UI based on authentication status
     */
    updateNavigationUI: function() {
        const authUserMenu = document.getElementById('authUserMenu');
        const guestMenu = document.getElementById('guestMenu');
        const usernameDisplay = document.getElementById('usernameDisplay');
        const myActivitiesLink = document.getElementById('myActivitiesLink');
        const uploadLink = document.getElementById('uploadLink');
        const analyticsLink = document.getElementById('analyticsLink');
        const notificationsBell = document.getElementById('notificationsBell');

        if (this.isAuthenticated()) {
            // Show authenticated menu, hide guest menu
            if (authUserMenu) {
                authUserMenu.classList.remove('d-none');
            }
            if (guestMenu) {
                guestMenu.style.display = 'none';
            }

            // Show authenticated navigation links
            if (myActivitiesLink) {
                myActivitiesLink.style.display = '';
                myActivitiesLink.parentElement.style.display = '';
            }
            if (uploadLink) {
                uploadLink.style.display = '';
                uploadLink.parentElement.style.display = '';
            }
            if (analyticsLink) {
                analyticsLink.style.display = '';
                analyticsLink.parentElement.style.display = '';
            }

            // Show notifications bell
            if (notificationsBell) {
                notificationsBell.classList.remove('d-none');
            }

            // Display username
            const username = this.getUsername();
            if (usernameDisplay && username) {
                usernameDisplay.textContent = username;
            }

            // Start polling for unread notifications
            this.startNotificationPolling();
        } else {
            // Show guest menu, hide authenticated menu
            if (authUserMenu) {
                authUserMenu.classList.add('d-none');
            }
            if (notificationsBell) {
                notificationsBell.classList.add('d-none');
            }
            if (guestMenu) {
                guestMenu.style.display = '';
            }

            // Hide authenticated navigation links
            if (myActivitiesLink) {
                myActivitiesLink.style.display = 'none';
                myActivitiesLink.parentElement.style.display = 'none';
            }
            if (uploadLink) {
                uploadLink.style.display = 'none';
                uploadLink.parentElement.style.display = 'none';
            }
            if (analyticsLink) {
                analyticsLink.style.display = 'none';
                analyticsLink.parentElement.style.display = 'none';
            }
        }
    },

    /**
     * Check authentication status and handle accordingly
     */
    checkAuthStatus: function() {
        const currentPath = window.location.pathname;
        const publicPaths = ['/', '/login', '/register', '/timeline'];

        // Skip check for public paths
        if (publicPaths.includes(currentPath)) {
            return;
        }

        // Activity detail pages are public (for viewing public activities)
        // Pattern: /activities/{uuid}
        if (currentPath.startsWith('/activities/') && currentPath.split('/').length === 3) {
            return;
        }

        // Check if authenticated
        if (!this.isAuthenticated()) {
            // Redirect to login for protected pages
            window.location.href = '/login?redirect=' + encodeURIComponent(currentPath);
        }
    },

    /**
     * Set up warning for session expiration
     */
    setupExpirationWarning: function() {
        const token = this.getToken();
        if (!token) {
            return;
        }

        const expirationTime = this.getTokenExpirationTime();

        if (expirationTime > 0) {
            // Warn 5 minutes before expiration
            const warningTime = Math.max(0, (expirationTime - 300) * 1000);

            setTimeout(() => {
                if (this.isAuthenticated()) {
                    this.showExpirationWarning();
                }
            }, warningTime);
        }
    },

    /**
     * Show session expiration warning
     */
    showExpirationWarning: function() {
        if (window.FitPub && window.FitPub.showAlert) {
            window.FitPub.showAlert(
                'Your session will expire soon. Please save your work.',
                'warning'
            );
        } else {
            console.warn('Session expiring soon');
        }
    },

    /**
     * Refresh the current page with authentication
     */
    refreshPage: function() {
        window.location.reload();
    },

    /**
     * Start polling for unread notifications
     */
    notificationPollInterval: null,

    startNotificationPolling: function() {
        // Stop any existing polling
        this.stopNotificationPolling();

        // Initial fetch
        this.updateUnreadNotificationCount();

        // Poll every 30 seconds
        this.notificationPollInterval = setInterval(() => {
            this.updateUnreadNotificationCount();
        }, 30000);
    },

    stopNotificationPolling: function() {
        if (this.notificationPollInterval) {
            clearInterval(this.notificationPollInterval);
            this.notificationPollInterval = null;
        }
    },

    async updateUnreadNotificationCount() {
        try {
            const response = await this.authenticatedFetch('/api/notifications/unread/count');
            if (response.ok) {
                const data = await response.json();
                const badge = document.getElementById('navNotificationCount');
                if (badge) {
                    if (data.count > 0) {
                        badge.textContent = data.count > 99 ? '99+' : data.count;
                        badge.style.display = 'inline-block';
                    } else {
                        badge.style.display = 'none';
                    }
                }
            }
        } catch (error) {
            // Silently fail - don't spam console with errors
            console.debug('Failed to fetch notification count:', error);
        }
    }
};

// Initialize authentication on page load
document.addEventListener('DOMContentLoaded', function() {
    FitPubAuth.init();
});

// Make available globally
window.FitPubAuth = FitPubAuth;
