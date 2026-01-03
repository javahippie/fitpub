/**
 * Batch Import JavaScript Module
 * Handles ZIP file upload, progress tracking, and results display for batch activity imports.
 */

(function() {
    'use strict';

    let currentJobId = null;
    let pollingInterval = null;
    let selectedFile = null;

    // Initialize page
    document.addEventListener('DOMContentLoaded', function() {
        initializeUploadZone();
        loadRecentJobs();
        checkAuthentication();
    });

    /**
     * Check if user is authenticated, show warning if not.
     * Don't redirect immediately - let them browse, but prevent upload.
     */
    function checkAuthentication() {
        const token = localStorage.getItem('jwtToken');
        if (!token) {
            console.log('No auth token found - user needs to login to upload');
            // Show a message but don't redirect yet
            const uploadZone = document.getElementById('uploadZone');
            if (uploadZone) {
                uploadZone.innerHTML = `
                    <i class="bi bi-lock"></i>
                    <h3>Authentication Required</h3>
                    <p class="text-muted">Please log in to use batch import</p>
                    <a href="/login?redirect=${encodeURIComponent(window.location.pathname)}" class="btn btn-primary btn-lg">
                        <i class="bi bi-box-arrow-in-right"></i> Login
                    </a>
                `;
            }
        }
    }

    /**
     * Initialize drag-and-drop upload zone.
     */
    function initializeUploadZone() {
        const uploadZone = document.getElementById('uploadZone');
        const fileInput = document.getElementById('zipFileInput');
        const uploadButton = document.getElementById('uploadButton');

        // Click to browse
        uploadZone.addEventListener('click', (e) => {
            if (e.target.type !== 'button') {
                fileInput.click();
            }
        });

        // File selected via input
        fileInput.addEventListener('change', handleFileSelect);

        // Drag and drop
        uploadZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            uploadZone.classList.add('drag-over');
        });

        uploadZone.addEventListener('dragleave', () => {
            uploadZone.classList.remove('drag-over');
        });

        uploadZone.addEventListener('drop', (e) => {
            e.preventDefault();
            uploadZone.classList.remove('drag-over');

            const files = e.dataTransfer.files;
            if (files.length > 0) {
                const file = files[0];
                if (file.name.toLowerCase().endsWith('.zip')) {
                    selectedFile = file;
                    displaySelectedFile(file);
                } else {
                    FitPub.showAlert('Please select a ZIP file', 'danger');
                }
            }
        });

        // Upload button click
        uploadButton.addEventListener('click', uploadZipFile);
    }

    /**
     * Handle file selection from input.
     */
    function handleFileSelect(e) {
        const file = e.target.files[0];
        if (file) {
            if (file.name.toLowerCase().endsWith('.zip')) {
                selectedFile = file;
                displaySelectedFile(file);
            } else {
                FitPub.showAlert('Please select a ZIP file', 'danger');
                e.target.value = '';
            }
        }
    }

    /**
     * Display selected file information.
     */
    function displaySelectedFile(file) {
        const fileInfo = document.getElementById('selectedFileInfo');
        const fileName = document.getElementById('selectedFileName');
        const fileSize = document.getElementById('selectedFileSize');

        fileName.textContent = file.name;
        fileSize.textContent = `(${formatFileSize(file.size)})`;
        fileInfo.style.display = 'block';
    }

    /**
     * Upload ZIP file to server.
     */
    async function uploadZipFile() {
        if (!selectedFile) {
            FitPub.showAlert('Please select a ZIP file first', 'warning');
            return;
        }

        // Check authentication
        const token = localStorage.getItem('jwtToken');
        if (!token) {
            FitPub.showAlert('You must be logged in to upload files. Redirecting to login...', 'warning');
            setTimeout(() => {
                window.location.href = '/login?redirect=' + encodeURIComponent(window.location.pathname);
            }, 2000);
            return;
        }

        // Validate file size (500 MB max)
        const maxSize = 500 * 1024 * 1024;
        if (selectedFile.size > maxSize) {
            FitPub.showAlert('ZIP file is too large. Maximum size is 500 MB', 'danger');
            return;
        }

        try {
            // Show progress section
            const progressSection = document.getElementById('progressSection');
            progressSection.classList.add('active');
            document.getElementById('statusMessage').textContent = 'Uploading ZIP file...';
            document.getElementById('selectedFileInfo').style.display = 'none';

            // Create form data
            const formData = new FormData();
            formData.append('file', selectedFile);

            // Upload file
            // Note: Don't set Content-Type header - browser will set it automatically with boundary
            console.log('Uploading ZIP file with token:', token ? 'Present' : 'Missing');
            const response = await fetch('/api/batch-import/upload', {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`
                    // Don't set Content-Type - browser will set multipart/form-data with boundary
                },
                body: formData
            });

            console.log('Upload response status:', response.status);

            if (!response.ok) {
                let errorMessage = 'Upload failed';
                try {
                    const error = await response.json();
                    errorMessage = error.error || error.message || `Server returned ${response.status}: ${response.statusText}`;
                } catch (e) {
                    errorMessage = `Server returned ${response.status}: ${response.statusText}`;
                }
                throw new Error(errorMessage);
            }

            const job = await response.json();
            currentJobId = job.id;

            // Start polling for progress
            startPolling(job.id);

            // Update UI with initial job info
            updateProgress(job);

            FitPub.showAlert(`Batch import started! Processing ${job.totalFiles} files...`, 'success');

        } catch (error) {
            console.error('Upload failed:', error);
            FitPub.showAlert('Failed to upload ZIP file: ' + error.message, 'danger');
            document.getElementById('progressSection').classList.remove('active');
        }
    }

    /**
     * Start polling for job progress.
     */
    function startPolling(jobId) {
        // Clear any existing interval
        if (pollingInterval) {
            clearInterval(pollingInterval);
        }

        // Poll every 3 seconds
        pollingInterval = setInterval(() => {
            fetchJobStatus(jobId);
        }, 3000);

        // Fetch immediately
        fetchJobStatus(jobId);
    }

    /**
     * Fetch job status from server.
     */
    async function fetchJobStatus(jobId) {
        try {
            const response = await authenticatedFetch(`/api/batch-import/jobs/${jobId}/status`);

            if (!response.ok) {
                throw new Error('Failed to fetch job status');
            }

            const job = await response.json();
            updateProgress(job);

            // Stop polling if job is finished
            if (job.status === 'COMPLETED' || job.status === 'FAILED' || job.status === 'CANCELLED') {
                stopPolling();
                loadFileResults(jobId);
                loadRecentJobs();

                if (job.status === 'COMPLETED') {
                    FitPub.showAlert(`Batch import completed! ${job.successCount} successful, ${job.failedCount} failed.`, 'success');
                } else if (job.status === 'FAILED') {
                    FitPub.showAlert('Batch import failed: ' + (job.errorMessage || 'Unknown error'), 'danger');
                }
            }

        } catch (error) {
            console.error('Failed to fetch job status:', error);
            stopPolling();
        }
    }

    /**
     * Stop polling for progress.
     */
    function stopPolling() {
        if (pollingInterval) {
            clearInterval(pollingInterval);
            pollingInterval = null;
        }
    }

    /**
     * Update progress UI with job data.
     */
    function updateProgress(job) {
        // Update progress bar
        const progressBar = document.getElementById('progressBar');
        const progressText = document.getElementById('progressText');
        const percentage = job.progressPercentage || 0;

        progressBar.style.width = percentage + '%';
        progressBar.setAttribute('aria-valuenow', percentage);
        progressText.textContent = percentage + '%';

        // Update stats
        document.getElementById('statTotal').textContent = job.totalFiles || 0;
        document.getElementById('statProcessed').textContent = job.processedFiles || 0;
        document.getElementById('statSuccess').textContent = job.successCount || 0;
        document.getElementById('statFailed').textContent = job.failedCount || 0;

        // Update status message
        const statusMessage = document.getElementById('statusMessage');
        if (job.status === 'PENDING') {
            statusMessage.textContent = 'Starting batch import...';
        } else if (job.status === 'PROCESSING') {
            statusMessage.textContent = `Processing files... (${job.processedFiles}/${job.totalFiles})`;
        } else if (job.status === 'COMPLETED') {
            statusMessage.textContent = 'Batch import completed!';
            progressBar.classList.remove('progress-bar-animated');
        } else if (job.status === 'FAILED') {
            statusMessage.textContent = 'Batch import failed!';
            progressBar.classList.remove('progress-bar-animated');
            progressBar.classList.add('bg-danger');
        }
    }

    /**
     * Load file results when job is complete.
     */
    async function loadFileResults(jobId) {
        try {
            const response = await authenticatedFetch(`/api/batch-import/jobs/${jobId}/files`);

            if (!response.ok) {
                throw new Error('Failed to fetch file results');
            }

            const results = await response.json();
            displayFileResults(results);

        } catch (error) {
            console.error('Failed to load file results:', error);
        }
    }

    /**
     * Display file results in table.
     */
    function displayFileResults(results) {
        const section = document.getElementById('fileResultsSection');
        const tbody = document.getElementById('fileResultsBody');

        tbody.innerHTML = '';

        results.forEach(result => {
            const row = document.createElement('tr');

            // Filename
            const filenameCell = document.createElement('td');
            filenameCell.textContent = result.filename;
            row.appendChild(filenameCell);

            // Status
            const statusCell = document.createElement('td');
            const statusBadge = document.createElement('span');
            statusBadge.className = `status-badge status-${result.status}`;
            statusBadge.textContent = result.status;
            statusCell.appendChild(statusBadge);
            row.appendChild(statusCell);

            // Activity link
            const activityCell = document.createElement('td');
            if (result.activityId) {
                const link = document.createElement('a');
                link.href = `/activities/${result.activityId}`;
                link.textContent = 'View Activity';
                link.className = 'btn btn-sm btn-outline-primary';
                activityCell.appendChild(link);
            } else {
                activityCell.textContent = '-';
            }
            row.appendChild(activityCell);

            // Error message
            const errorCell = document.createElement('td');
            if (result.errorMessage) {
                const errorText = document.createElement('small');
                errorText.className = 'text-danger';
                errorText.textContent = result.errorMessage;
                errorCell.appendChild(errorText);
            } else {
                errorCell.textContent = '-';
            }
            row.appendChild(errorCell);

            tbody.appendChild(row);
        });

        section.style.display = 'block';
    }

    /**
     * Load recent batch import jobs.
     */
    async function loadRecentJobs() {
        try {
            const response = await authenticatedFetch('/api/batch-import/jobs?page=0&size=5');

            if (!response.ok) {
                throw new Error('Failed to fetch recent jobs');
            }

            const page = await response.json();
            displayRecentJobs(page.content || []);

        } catch (error) {
            console.error('Failed to load recent jobs:', error);
            document.getElementById('recentJobsList').innerHTML = '<p class="text-muted">Failed to load recent imports</p>';
        }
    }

    /**
     * Display recent jobs list.
     */
    function displayRecentJobs(jobs) {
        const container = document.getElementById('recentJobsList');

        if (jobs.length === 0) {
            container.innerHTML = '<p class="text-muted">No recent batch imports</p>';
            return;
        }

        container.innerHTML = '';

        jobs.forEach(job => {
            const card = document.createElement('div');
            card.className = 'job-card';

            const header = document.createElement('div');
            header.className = 'd-flex justify-content-between align-items-center mb-2';

            const title = document.createElement('h5');
            title.className = 'mb-0';
            title.innerHTML = `<i class="bi bi-file-earmark-zip"></i> ${job.filename}`;
            header.appendChild(title);

            const statusBadge = document.createElement('span');
            statusBadge.className = `status-badge status-${job.status}`;
            statusBadge.textContent = job.status;
            header.appendChild(statusBadge);

            card.appendChild(header);

            const stats = document.createElement('div');
            stats.className = 'text-muted small';
            stats.innerHTML = `
                <i class="bi bi-calendar"></i> ${formatDate(job.createdAt)} |
                <i class="bi bi-file-earmark"></i> ${job.totalFiles} files |
                <i class="bi bi-check-circle"></i> ${job.successCount} successful |
                <i class="bi bi-x-circle"></i> ${job.failedCount} failed
            `;
            card.appendChild(stats);

            // Buttons for completed jobs
            if (job.status === 'COMPLETED' || job.status === 'FAILED') {
                const buttonGroup = document.createElement('div');
                buttonGroup.className = 'mt-2';

                const viewButton = document.createElement('button');
                viewButton.className = 'btn btn-sm btn-outline-primary me-2';
                viewButton.textContent = 'View Details';
                viewButton.onclick = () => viewJobDetails(job.id);
                buttonGroup.appendChild(viewButton);

                // Undo button for completed jobs with successful imports
                if (job.status === 'COMPLETED' && job.successCount > 0) {
                    const undoButton = document.createElement('button');
                    undoButton.className = 'btn btn-sm btn-outline-danger';
                    undoButton.innerHTML = '<i class="bi bi-arrow-counterclockwise"></i> Undo Import';
                    undoButton.onclick = () => undoBatchImport(job.id, job.filename, job.successCount);
                    buttonGroup.appendChild(undoButton);
                }

                card.appendChild(buttonGroup);
            }

            container.appendChild(card);
        });
    }

    /**
     * View job details (load and display file results).
     */
    function viewJobDetails(jobId) {
        currentJobId = jobId;
        fetchJobStatus(jobId).then(() => {
            loadFileResults(jobId);
            document.getElementById('progressSection').classList.add('active');
            window.scrollTo({ top: 0, behavior: 'smooth' });
        });
    }

    /**
     * Undo a batch import by deleting all successfully imported activities.
     * Shows confirmation dialog before proceeding.
     * Deletion happens asynchronously in the background.
     */
    async function undoBatchImport(jobId, filename, successCount) {
        // Show confirmation dialog
        const confirmed = confirm(
            `Are you sure you want to undo this batch import?\n\n` +
            `File: ${filename}\n` +
            `This will delete ${successCount} successfully imported ${successCount === 1 ? 'activity' : 'activities'}.\n\n` +
            `This operation cannot be reversed!`
        );

        if (!confirmed) {
            return;
        }

        try {
            const response = await authenticatedFetch(`/api/batch-import/jobs/${jobId}`, {
                method: 'DELETE'
            });

            if (!response.ok) {
                let errorMessage = 'Failed to undo batch import';
                try {
                    const error = await response.json();
                    errorMessage = error.error || error.message || `Server returned ${response.status}`;
                } catch (e) {
                    errorMessage = `Server returned ${response.status}: ${response.statusText}`;
                }
                throw new Error(errorMessage);
            }

            const result = await response.json();

            FitPub.showAlert(
                result.message || 'Batch import undo initiated. Activities are being deleted in the background.',
                'info'
            );

            // Refresh the recent jobs list after a short delay to allow async deletion to start
            setTimeout(() => {
                loadRecentJobs();
            }, 2000);

            // Clear progress section if this was the current job
            if (currentJobId === jobId) {
                document.getElementById('progressSection').classList.remove('active');
                document.getElementById('fileResultsSection').style.display = 'none';
                currentJobId = null;
            }

        } catch (error) {
            console.error('Failed to undo batch import:', error);
            FitPub.showAlert('Failed to undo batch import: ' + error.message, 'danger');
        }
    }

    /**
     * Format file size for display.
     */
    function formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';

        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));

        return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
    }

    /**
     * Format date for display.
     */
    function formatDate(dateString) {
        const date = new Date(dateString);
        const now = new Date();
        const diffMs = now - date;
        const diffMins = Math.floor(diffMs / 60000);

        if (diffMins < 1) return 'Just now';
        if (diffMins < 60) return `${diffMins}m ago`;

        const diffHours = Math.floor(diffMins / 60);
        if (diffHours < 24) return `${diffHours}h ago`;

        const diffDays = Math.floor(diffHours / 24);
        if (diffDays < 7) return `${diffDays}d ago`;

        return date.toLocaleDateString();
    }

    /**
     * Authenticated fetch wrapper (uses FitPubAuth from auth.js).
     */
    async function authenticatedFetch(url, options = {}) {
        if (typeof FitPubAuth !== 'undefined' && FitPubAuth.authenticatedFetch) {
            return FitPubAuth.authenticatedFetch(url, options);
        } else {
            // Fallback if auth.js is not loaded
            const token = localStorage.getItem('authToken');
            if (token) {
                options.headers = options.headers || {};
                options.headers['Authorization'] = `Bearer ${token}`;
            }
            return fetch(url, options);
        }
    }

})();
