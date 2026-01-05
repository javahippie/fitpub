package org.operaton.fitpub.util;

import com.garmin.fit.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Utility to analyze FIT file structure and print all available fields.
 * Helps debug what data is actually present in a FIT file.
 */
public class FitFileAnalyzer {

    private static final double SEMICIRCLES_TO_DEGREES = 180.0 / Math.pow(2, 31);
    private static final long FIT_EPOCH_OFFSET = 631065600L;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: FitFileAnalyzer <path-to-fit-file>");
            System.exit(1);
        }

        String filePath = args[0];
        analyzeFitFile(filePath);
    }

    public static void analyzeFitFile(String filePath) {
        System.out.println("=== FIT File Analysis ===");
        System.out.println("File: " + filePath);
        System.out.println();

        try (InputStream inputStream = new FileInputStream(filePath)) {
            Decode decode = new Decode();
            MesgBroadcaster broadcaster = new MesgBroadcaster(decode);

            // Counter for record messages
            final int[] recordCount = {0};
            final boolean[] hasGpsData = {false};

            // Listen for FileId messages
            broadcaster.addListener((FileIdMesgListener) fileId -> {
                System.out.println("=== FILE ID MESSAGE ===");
                System.out.println("Type: " + fileId.getType());
                System.out.println("Manufacturer: " + fileId.getManufacturer());
                System.out.println("Product: " + fileId.getProduct());
                if (fileId.getTimeCreated() != null) {
                    System.out.println("Time Created: " + convertDateTime(fileId.getTimeCreated()));
                }
                System.out.println();
            });

            // Listen for Session messages (summary)
            broadcaster.addListener((SessionMesgListener) session -> {
                System.out.println("=== SESSION MESSAGE ===");
                System.out.println("Sport: " + session.getSport());
                System.out.println("Sub Sport: " + session.getSubSport());

                if (session.getStartTime() != null) {
                    System.out.println("Start Time: " + convertDateTime(session.getStartTime()));
                }

                if (session.getTotalElapsedTime() != null) {
                    System.out.println("Total Elapsed Time: " + session.getTotalElapsedTime() + " seconds");
                }

                if (session.getTotalTimerTime() != null) {
                    System.out.println("Total Timer Time: " + session.getTotalTimerTime() + " seconds");
                }

                if (session.getTotalDistance() != null) {
                    System.out.println("Total Distance: " + session.getTotalDistance() + " meters (" +
                        (session.getTotalDistance() / 1000.0) + " km)");
                }

                if (session.getTotalAscent() != null) {
                    System.out.println("Total Ascent: " + session.getTotalAscent() + " meters");
                }

                if (session.getTotalDescent() != null) {
                    System.out.println("Total Descent: " + session.getTotalDescent() + " meters");
                }

                if (session.getAvgSpeed() != null) {
                    System.out.println("Average Speed: " + session.getAvgSpeed() + " m/s (" +
                        (session.getAvgSpeed() * 3.6) + " km/h)");
                }

                if (session.getMaxSpeed() != null) {
                    System.out.println("Max Speed: " + session.getMaxSpeed() + " m/s (" +
                        (session.getMaxSpeed() * 3.6) + " km/h)");
                }

                if (session.getAvgHeartRate() != null) {
                    System.out.println("Average Heart Rate: " + session.getAvgHeartRate() + " bpm");
                }

                if (session.getMaxHeartRate() != null) {
                    System.out.println("Max Heart Rate: " + session.getMaxHeartRate() + " bpm");
                }

                if (session.getAvgCadence() != null) {
                    System.out.println("Average Cadence: " + session.getAvgCadence() + " rpm");
                }

                if (session.getMaxCadence() != null) {
                    System.out.println("Max Cadence: " + session.getMaxCadence() + " rpm");
                }

                if (session.getAvgPower() != null) {
                    System.out.println("Average Power: " + session.getAvgPower() + " watts");
                }

                if (session.getMaxPower() != null) {
                    System.out.println("Max Power: " + session.getMaxPower() + " watts");
                }

                if (session.getTotalCalories() != null) {
                    System.out.println("Total Calories: " + session.getTotalCalories() + " kcal");
                }

                System.out.println();
            });

            // Listen for Record messages (data points)
            broadcaster.addListener((RecordMesgListener) record -> {
                recordCount[0]++;

                // Only print first 5 records to avoid spam
                if (recordCount[0] <= 5) {
                    System.out.println("=== RECORD MESSAGE #" + recordCount[0] + " ===");

                    if (record.getTimestamp() != null) {
                        System.out.println("Timestamp: " + convertDateTime(record.getTimestamp()));
                    }

                    Integer positionLat = record.getPositionLat();
                    Integer positionLong = record.getPositionLong();

                    if (positionLat != null && positionLong != null) {
                        double latitude = positionLat * SEMICIRCLES_TO_DEGREES;
                        double longitude = positionLong * SEMICIRCLES_TO_DEGREES;
                        System.out.println("GPS Position: " + latitude + ", " + longitude);
                        hasGpsData[0] = true;
                    } else {
                        System.out.println("GPS Position: NOT AVAILABLE");
                    }

                    if (record.getDistance() != null) {
                        System.out.println("Distance: " + record.getDistance() + " meters");
                    }

                    if (record.getSpeed() != null) {
                        System.out.println("Speed: " + record.getSpeed() + " m/s (" +
                            (record.getSpeed() * 3.6) + " km/h)");
                    }

                    if (record.getAltitude() != null) {
                        System.out.println("Altitude: " + record.getAltitude() + " meters");
                    }

                    if (record.getHeartRate() != null) {
                        System.out.println("Heart Rate: " + record.getHeartRate() + " bpm");
                    }

                    if (record.getCadence() != null) {
                        System.out.println("Cadence: " + record.getCadence() + " rpm");
                    }

                    if (record.getPower() != null) {
                        System.out.println("Power: " + record.getPower() + " watts");
                    }

                    if (record.getTemperature() != null) {
                        System.out.println("Temperature: " + record.getTemperature() + " °C");
                    }

                    System.out.println();
                }
            });

            // Listen for Lap messages
            broadcaster.addListener((LapMesgListener) lap -> {
                System.out.println("=== LAP MESSAGE ===");
                if (lap.getTotalDistance() != null) {
                    System.out.println("Lap Distance: " + lap.getTotalDistance() + " meters");
                }
                if (lap.getTotalTimerTime() != null) {
                    System.out.println("Lap Time: " + lap.getTotalTimerTime() + " seconds");
                }
                System.out.println();
            });

            // Decode the file
            if (!decode.read(inputStream, broadcaster)) {
                System.err.println("ERROR: Failed to decode FIT file");
                return;
            }

            System.out.println("=== SUMMARY ===");
            System.out.println("Total Record Messages: " + recordCount[0]);
            System.out.println("GPS Data Present: " + hasGpsData[0]);

            if (!hasGpsData[0] && recordCount[0] > 0) {
                System.out.println();
                System.out.println("NOTE: File contains record messages with distance/speed but NO GPS coordinates.");
                System.out.println("This is typical for indoor activities with virtual distance (smart trainers, apps).");
            }

        } catch (Exception e) {
            System.err.println("ERROR analyzing FIT file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static LocalDateTime convertDateTime(DateTime dateTime) {
        long timestamp = dateTime.getTimestamp();
        Instant instant = Instant.ofEpochSecond(timestamp + FIT_EPOCH_OFFSET);
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
