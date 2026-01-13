package net.javahippie.fitpub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Service for rendering OpenStreetMap tiles into activity images.
 * Implements OSM tile usage policy: proper User-Agent, rate limiting, and caching.
 */
@Service
@Slf4j
public class OsmTileRenderer {

    private static final String TILE_SERVER_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
    private static final int TILE_SIZE = 256; // Standard OSM tile size in pixels
    private static final Duration TILE_CACHE_MAX_AGE = Duration.ofDays(30);

    // Rate limiting: OSM policy requires max 2 tiles/second
    private static final Semaphore rateLimiter = new Semaphore(1);
    private static Instant lastRequestTime = Instant.now();

    @Value("${fitpub.storage.tile-cache.path:${java.io.tmpdir}/fitpub/tiles}")
    private String tileCachePath;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    private final HttpClient httpClient;

    public OsmTileRenderer() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Holder for letterboxing transformation parameters.
     */
    public static class LetterboxTransform {
        public final int offsetX;
        public final int offsetY;
        public final int scaledWidth;
        public final int scaledHeight;
        public final double scaleFactorX;
        public final double scaleFactorY;

        public LetterboxTransform(int offsetX, int offsetY, int scaledWidth, int scaledHeight,
                                 int originalWidth, int originalHeight) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.scaledWidth = scaledWidth;
            this.scaledHeight = scaledHeight;
            this.scaleFactorX = (double) scaledWidth / originalWidth;
            this.scaleFactorY = (double) scaledHeight / originalHeight;
        }
    }

    private LetterboxTransform lastLetterboxTransform;

    /**
     * Get the letterbox transformation from the last render operation.
     */
    public LetterboxTransform getLastLetterboxTransform() {
        return lastLetterboxTransform;
    }

    /**
     * Render a map image with OSM tiles covering the specified geographic bounds.
     *
     * @param minLat minimum latitude
     * @param maxLat maximum latitude
     * @param minLon minimum longitude
     * @param maxLon maximum longitude
     * @param width  target image width in pixels
     * @param height target image height in pixels
     * @return BufferedImage with rendered map tiles
     */
    public BufferedImage renderMapWithTiles(double minLat, double maxLat,
                                           double minLon, double maxLon,
                                           int width, int height) throws IOException {

        // Calculate optimal zoom level for the given bounds and image size
        int zoom = calculateOptimalZoom(minLat, maxLat, minLon, maxLon, width, height);

        // Calculate tile coordinates covering the bounds
        TileCoordinate topLeft = getTileCoordinate(maxLat, minLon, zoom);
        TileCoordinate bottomRight = getTileCoordinate(minLat, maxLon, zoom);

        log.debug("Rendering map tiles: zoom={}, tiles=({},{}) to ({},{})",
                 zoom, topLeft.x, topLeft.y, bottomRight.x, bottomRight.y);

        // Calculate the size of the tile grid
        int tilesX = bottomRight.x - topLeft.x + 1;
        int tilesY = bottomRight.y - topLeft.y + 1;

        // Limit tile count to prevent excessive downloads
        if (tilesX * tilesY > 50) {
            log.warn("Too many tiles required ({} tiles), using lower zoom", tilesX * tilesY);
            zoom = Math.max(1, zoom - 1);
            return renderMapWithTiles(minLat, maxLat, minLon, maxLon, width, height);
        }

        // Create base image for all tiles
        int fullWidth = tilesX * TILE_SIZE;
        int fullHeight = tilesY * TILE_SIZE;
        BufferedImage fullMap = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = fullMap.createGraphics();

        // Download and composite tiles
        for (int x = topLeft.x; x <= bottomRight.x; x++) {
            for (int y = topLeft.y; y <= bottomRight.y; y++) {
                try {
                    BufferedImage tile = getTile(zoom, x, y);
                    int drawX = (x - topLeft.x) * TILE_SIZE;
                    int drawY = (y - topLeft.y) * TILE_SIZE;
                    g2d.drawImage(tile, drawX, drawY, null);
                } catch (Exception e) {
                    log.warn("Failed to load tile {}/{}/{}: {}", zoom, x, y, e.getMessage());
                    // Draw a placeholder gray tile
                    g2d.setColor(new Color(200, 200, 200));
                    int drawX = (x - topLeft.x) * TILE_SIZE;
                    int drawY = (y - topLeft.y) * TILE_SIZE;
                    g2d.fillRect(drawX, drawY, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        g2d.dispose();

        // Calculate crop area to match the exact geographic bounds
        double topLeftPixelX = longitudeToPixel(minLon, zoom);
        double topLeftPixelY = latitudeToPixel(maxLat, zoom);
        double bottomRightPixelX = longitudeToPixel(maxLon, zoom);
        double bottomRightPixelY = latitudeToPixel(minLat, zoom);

        int cropX = (int) (topLeftPixelX - topLeft.x * TILE_SIZE);
        int cropY = (int) (topLeftPixelY - topLeft.y * TILE_SIZE);
        int cropWidth = (int) (bottomRightPixelX - topLeftPixelX);
        int cropHeight = (int) (bottomRightPixelY - topLeftPixelY);

        // Crop to exact bounds
        BufferedImage croppedMap = fullMap.getSubimage(
                Math.max(0, cropX),
                Math.max(0, cropY),
                Math.min(cropWidth, fullWidth - cropX),
                Math.min(cropHeight, fullHeight - cropY)
        );

        // Scale to target dimensions with letterboxing to preserve aspect ratio
        BufferedImage scaledMap = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaledMap.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Calculate aspect ratios
        double sourceAspect = (double) croppedMap.getWidth() / croppedMap.getHeight();
        double targetAspect = (double) width / height;

        int drawWidth, drawHeight, drawX, drawY;

        if (sourceAspect > targetAspect) {
            // Source is wider - fit to width, letterbox top/bottom
            drawWidth = width;
            drawHeight = (int) (width / sourceAspect);
            drawX = 0;
            drawY = (height - drawHeight) / 2;
        } else {
            // Source is taller - fit to height, letterbox left/right
            drawHeight = height;
            drawWidth = (int) (height * sourceAspect);
            drawX = (width - drawWidth) / 2;
            drawY = 0;
        }

        // Fill background with neutral gray
        g.setColor(new Color(240, 240, 240));
        g.fillRect(0, 0, width, height);

        // Draw scaled image centered with preserved aspect ratio
        g.drawImage(croppedMap, drawX, drawY, drawWidth, drawHeight, null);
        g.dispose();

        // Store letterbox transform for track rendering
        lastLetterboxTransform = new LetterboxTransform(
                drawX, drawY, drawWidth, drawHeight,
                croppedMap.getWidth(), croppedMap.getHeight()
        );

        return scaledMap;
    }

    /**
     * Get a single tile, either from cache or by downloading.
     */
    private BufferedImage getTile(int zoom, int x, int y) throws IOException, InterruptedException {
        // Check cache first
        File cacheFile = getTileCacheFile(zoom, x, y);

        if (cacheFile.exists() && !isCacheExpired(cacheFile)) {
            try {
                return ImageIO.read(cacheFile);
            } catch (IOException e) {
                log.warn("Failed to read cached tile, will re-download: {}", e.getMessage());
                cacheFile.delete();
            }
        }

        // Download tile with rate limiting
        return downloadTile(zoom, x, y, cacheFile);
    }

    /**
     * Download a tile from OSM tile server with proper rate limiting and User-Agent.
     */
    private BufferedImage downloadTile(int zoom, int x, int y, File cacheFile)
            throws IOException, InterruptedException {

        // Rate limiting: max 2 requests per second (500ms between requests)
        rateLimiter.acquire();
        try {
            Duration timeSinceLastRequest = Duration.between(lastRequestTime, Instant.now());
            if (timeSinceLastRequest.toMillis() < 500) {
                Thread.sleep(500 - timeSinceLastRequest.toMillis());
            }
            lastRequestTime = Instant.now();

            String url = TILE_SERVER_URL
                    .replace("{z}", String.valueOf(zoom))
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "FitPub/1.0 (" + baseUrl + "; contact via repository)")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                byte[] imageData = response.body();

                // Save to cache
                cacheFile.getParentFile().mkdirs();
                Files.write(cacheFile.toPath(), imageData);

                // Read and return image
                return ImageIO.read(new java.io.ByteArrayInputStream(imageData));
            } else {
                throw new IOException("Failed to download tile: HTTP " + response.statusCode());
            }
        } finally {
            rateLimiter.release();
        }
    }

    /**
     * Get the cache file path for a tile.
     */
    private File getTileCacheFile(int zoom, int x, int y) {
        return new File(tileCachePath, String.format("%d/%d/%d.png", zoom, x, y));
    }

    /**
     * Check if a cached tile has expired (older than 30 days).
     */
    private boolean isCacheExpired(File cacheFile) {
        try {
            Instant fileTime = Files.getLastModifiedTime(cacheFile.toPath()).toInstant();
            Duration age = Duration.between(fileTime, Instant.now());
            return age.compareTo(TILE_CACHE_MAX_AGE) > 0;
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Calculate optimal zoom level for the given bounds and image size.
     */
    private int calculateOptimalZoom(double minLat, double maxLat,
                                     double minLon, double maxLon,
                                     int width, int height) {
        // Try different zoom levels and pick the one that best fits
        for (int zoom = 18; zoom >= 1; zoom--) {
            TileCoordinate topLeft = getTileCoordinate(maxLat, minLon, zoom);
            TileCoordinate bottomRight = getTileCoordinate(minLat, maxLon, zoom);

            int tilesX = bottomRight.x - topLeft.x + 1;
            int tilesY = bottomRight.y - topLeft.y + 1;

            int pixelWidth = tilesX * TILE_SIZE;
            int pixelHeight = tilesY * TILE_SIZE;

            // Use this zoom if it provides enough resolution
            if (pixelWidth >= width * 0.8 && pixelHeight >= height * 0.8 && tilesX * tilesY <= 20) {
                return zoom;
            }
        }

        return 12; // Default fallback
    }

    /**
     * Convert latitude/longitude to tile coordinates at a given zoom level.
     */
    private TileCoordinate getTileCoordinate(double lat, double lon, int zoom) {
        int x = (int) Math.floor((lon + 180.0) / 360.0 * (1 << zoom));
        int y = (int) Math.floor((1.0 - Math.log(Math.tan(Math.toRadians(lat)) +
                1.0 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2.0 * (1 << zoom));
        return new TileCoordinate(x, y);
    }

    /**
     * Convert longitude to pixel X coordinate at a given zoom level.
     */
    private double longitudeToPixel(double lon, int zoom) {
        return (lon + 180.0) / 360.0 * (1 << zoom) * TILE_SIZE;
    }

    /**
     * Convert latitude to pixel Y coordinate at a given zoom level.
     */
    private double latitudeToPixel(double lat, int zoom) {
        return (1.0 - Math.log(Math.tan(Math.toRadians(lat)) +
                1.0 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2.0 * (1 << zoom) * TILE_SIZE;
    }

    /**
     * Simple record to hold tile coordinates.
     */
    private record TileCoordinate(int x, int y) {}
}
