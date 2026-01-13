// FitPub - Authentication Management

/**
 * Authentication utilities for managing JWT tokens and user sessions
 */
const FitPubAuth = {
    /**
     * Get the stored JWT token
     * Note: With httpOnly cookies, the token is not accessible via JavaScript
     * This method is kept for backward compatibility but returns null
     * @returns {null} Always returns null (token is in httpOnly cookie)
     */
    getToken: function() {
        // Token is now in httpOnly cookie, not accessible to JavaScript
        return null;
    },

    /**
     * Store JWT token
     * Note: Not used anymore - token is stored in httpOnly cookie by server
     * @param {string} token - JWT token (ignored, kept for compatibility)
     */
    setToken: function(token) {
        // Token is automatically stored in httpOnly cookie by server
        // No action needed
    },

    /**
     * Remove stored JWT token and username
     */
    removeToken: function() {
        // Cookie is cleared by server on logout
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
     * With httpOnly cookies, we check if username is stored (set on login)
     * The actual authentication is verified server-side on each request
     * @returns {boolean} True if authenticated, false otherwise
     */
    isAuthenticated: function() {
        // With httpOnly cookies, we can't access the token
        // We check if username exists in localStorage (set on successful login)
        const username = this.getUsername();
        return username != null && username !== '';
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
    logout: async function() {
        try {
            // Call server logout endpoint to clear the httpOnly cookie
            await fetch('/api/auth/logout', {
                method: 'POST',
                credentials: 'include' // Important: send cookies
            });
        } catch (error) {
            console.error('Logout request failed:', error);
            // Continue with logout even if server request fails
        }

        // Clear client-side storage
        this.removeToken();

        // Redirect to login page
        window.location.href = '/login';
    },

    /**
     * Make an authenticated API request
     * With httpOnly cookies, authentication is automatic via cookies
     * @param {string} url - API endpoint URL
     * @param {object} options - Fetch options
     * @returns {Promise<Response>} Fetch response
     */
    authenticatedFetch: async function(url, options = {}) {
        // Prepare headers
        const headers = {
            ...options.headers
        };

        // If body is an object, set Content-Type to JSON
        if (options.body && typeof options.body === 'object') {
            headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(options.body);
        }

        // Make request with credentials to send httpOnly cookie
        const response = await fetch(url, {
            ...options,
            headers,
            credentials: 'include' // Important: send cookies
        });

        // If unauthorized, clear local state and redirect to login
        if (response.status === 401 || response.status === 403) {
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
        const discoverLink = document.getElementById('discoverLink');
        const myActivitiesLink = document.getElementById('myActivitiesLink');
        const uploadDropdown = document.getElementById('uploadDropdown');
        const analyticsDropdown = document.getElementById('analyticsDropdown');
        const notificationsBell = document.getElementById('notificationsBell');
        const notificationsBellMobile = document.getElementById('notificationsBellMobile');

        if (this.isAuthenticated()) {
            // Show authenticated menu, hide guest menu
            if (authUserMenu) {
                authUserMenu.classList.remove('d-none');
            }
            if (guestMenu) {
                guestMenu.style.display = 'none';
            }

            // Show authenticated navigation links
            if (discoverLink) {
                discoverLink.style.display = '';
                discoverLink.parentElement.style.display = '';
            }
            if (myActivitiesLink) {
                myActivitiesLink.style.display = '';
                myActivitiesLink.parentElement.style.display = '';
            }

            // Show dropdown menus
            if (uploadDropdown) {
                uploadDropdown.classList.remove('d-none');
            }
            if (analyticsDropdown) {
                analyticsDropdown.classList.remove('d-none');
            }

            // Show notifications bell (desktop: visible on lg+, mobile: visible below lg)
            if (notificationsBell) {
                notificationsBell.classList.remove('d-none');
                notificationsBell.classList.add('d-lg-block'); // Visible on lg+, hidden below
            }
            if (notificationsBellMobile) {
                notificationsBellMobile.classList.remove('d-none', 'd-lg-none');
                notificationsBellMobile.classList.add('d-block', 'd-lg-none'); // Visible below lg, hidden on lg+
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

            // Hide notification bells completely
            if (notificationsBell) {
                notificationsBell.classList.remove('d-lg-block', 'd-block');
                notificationsBell.classList.add('d-none');
            }
            if (notificationsBellMobile) {
                notificationsBellMobile.classList.remove('d-block', 'd-lg-none');
                notificationsBellMobile.classList.add('d-none');
            }

            if (guestMenu) {
                guestMenu.style.display = '';
            }

            // Hide authenticated navigation links
            if (discoverLink) {
                discoverLink.style.display = 'none';
                discoverLink.parentElement.style.display = 'none';
            }
            if (myActivitiesLink) {
                myActivitiesLink.style.display = 'none';
                myActivitiesLink.parentElement.style.display = 'none';
            }

            // Hide dropdown menus
            if (uploadDropdown) {
                uploadDropdown.classList.add('d-none');
            }
            if (analyticsDropdown) {
                analyticsDropdown.classList.add('d-none');
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

        if (currentPath.startsWith('/terms')) {
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
                const badgeDesktop = document.getElementById('navNotificationCount');
                const badgeMobile = document.getElementById('navNotificationCountMobile');

                const displayText = data.count > 99 ? '99+' : data.count;
                const shouldDisplay = data.count > 0;

                // Update desktop badge
                if (badgeDesktop) {
                    badgeDesktop.textContent = displayText;
                    badgeDesktop.style.display = shouldDisplay ? 'inline-block' : 'none';
                }

                // Update mobile badge
                if (badgeMobile) {
                    badgeMobile.textContent = displayText;
                    badgeMobile.style.display = shouldDisplay ? 'inline-block' : 'none';
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
