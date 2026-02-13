package com.options.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Data cleaner for options data.
 * 
 * Removes problematic entries from raw options data to ensure
 * reliable pricing and implied volatility calculations.
 * Matches the filtering logic from Python clean_data.py.
 */
public class DataCleaner {
    
    /**
     * Clean options data by removing problematic entries.
     * 
     * @param options List of raw options data
     * @return Cleaned list of options
     */
    public static List<OptionData> cleanOptionsData(List<OptionData> options) {
        int initialCount = options.size();
        System.out.printf("%nCleaning %d options...%n", initialCount);
        
        List<OptionData> cleaned = new ArrayList<>();
        int[] removedCounts = new int[5]; // Track removal reasons
        
        for (OptionData option : options) {
            boolean keep = true;
            
            // Filter 1: Remove expired options (T <= 0)
            if (option.getT() <= 0) {
                removedCounts[0]++;
                keep = false;
            }
            
            // Filter 2: Remove unrealistic IVs (>200% or <1%)
            if (keep && (option.impliedVolatility <= 0.01 || option.impliedVolatility >= 2.0)) {
                removedCounts[1]++;
                keep = false;
            }
            
            // Filter 3: Remove options with very wide bid-ask spreads (>10% of price)
            if (keep) {
                double bidAskSpread = option.ask - option.bid;
                double lastPrice = option.getLastPriceAsDouble();
                double spreadPct = bidAskSpread / lastPrice;
                if (spreadPct >= 0.10) {
                    removedCounts[2]++;
                    keep = false;
                }
            }
            
            // Filter 4: Remove very illiquid options (volume < 10)
            if (keep && option.volume < 10) {
                removedCounts[3]++;
                keep = false;
            }
            
            // Filter 5: Focus on reasonable moneyness (0.85 to 1.15)
            if (keep && (option.getMoneyness() < 0.85 || option.getMoneyness() > 1.15)) {
                removedCounts[4]++;
                keep = false;
            }
            
            if (keep) {
                cleaned.add(option);
            }
        }
        
        // Print filtering summary
        System.out.printf("After removing T <= 0: %d (%d removed)%n", 
                        cleaned.size(), removedCounts[0]);
        System.out.printf("After IV filter: %d (%d removed)%n", 
                        initialCount - removedCounts[0] - removedCounts[1], removedCounts[1]);
        System.out.printf("After spread filter: %d (%d removed)%n", 
                        initialCount - removedCounts[0] - removedCounts[1] - removedCounts[2], removedCounts[2]);
        System.out.printf("After volume filter: %d (%d removed)%n", 
                        initialCount - removedCounts[0] - removedCounts[1] - removedCounts[2] - removedCounts[3], removedCounts[3]);
        System.out.printf("After moneyness filter: %d (%d removed)%n", 
                        cleaned.size(), removedCounts[4]);
        
        System.out.printf("%nFinal dataset: %d options%n", cleaned.size());
        
        // Print statistics about the cleaned data
        printStatistics(cleaned);
        
        return cleaned;
    }
    
    /**
     * Print detailed statistics about the cleaned options data.
     * 
     * @param options List of cleaned options
     */
    private static void printStatistics(List<OptionData> options) {
        if (options.isEmpty()) {
            System.out.println("No options data after cleaning.");
            return;
        }
        
        long calls = options.stream().filter(o -> "call".equals(o.getType())).count();
        long puts = options.stream().filter(o -> "put".equals(o.getType())).count();
        
        double minIV = options.stream().mapToDouble(o -> o.impliedVolatility).min().orElse(0);
        double maxIV = options.stream().mapToDouble(o -> o.impliedVolatility).max().orElse(0);
        double avgIV = options.stream().mapToDouble(o -> o.impliedVolatility).average().orElse(0);
        
        double minMoneyness = options.stream().mapToDouble(o -> o.getMoneyness()).min().orElse(0);
        double maxMoneyness = options.stream().mapToDouble(o -> o.getMoneyness()).max().orElse(0);
        
        int minDTE = options.stream().mapToInt(o -> o.getDaysToExpiration()).min().orElse(0);
        int maxDTE = options.stream().mapToInt(o -> o.getDaysToExpiration()).max().orElse(0);
        
        System.out.printf("  Calls: %d%n", calls);
        System.out.printf("  Puts: %d%n", puts);
        System.out.printf("  IV range: %.1f%% - %.1f%%%n", minIV * 100, maxIV * 100);
        System.out.printf("  Moneyness range: %.3f - %.3f%n", minMoneyness, maxMoneyness);
        System.out.printf("  Days to expiry: %d - %d%n", minDTE, maxDTE);
    }
    
    /**
     * Analyze implied volatility surface patterns.
     * 
     * @param options List of cleaned options
     */
    public static void analyzeIVSurface(List<OptionData> options) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("          IMPLIED VOLATILITY SURFACE ANALYSIS          ");
        System.out.println("=".repeat(70));
        
        // Separate calls and puts
        List<OptionData> calls = options.stream()
                .filter(o -> "call".equals(o.getType()))
                .toList();
        List<OptionData> puts = options.stream()
                .filter(o -> "put".equals(o.getType()))
                .toList();
        
        // Analyze IV by moneyness for calls
        System.out.println("\nIV by Moneyness (for calls):");
        analyzeIVByMoneyness(calls);
        
        // Analyze IV by time to expiration
        System.out.println("\nIV by Time to Expiration:");
        analyzeIVByTimeToExpiration(options);
        
        // Volatility smile pattern
        System.out.println("\nVolatility Smile Pattern:");
        analyzeVolatilitySmile(calls);
        
        System.out.println("=".repeat(70));
    }
    
    /**
     * Analyze IV by moneyness ranges.
     */
    private static void analyzeIVByMoneyness(List<OptionData> calls) {
        double[][] moneynessBins = {{0.8, 0.95}, {0.95, 1.05}, {1.05, 1.2}};
        String[] binLabels = {"OTM (<0.95)", "ATM (0.95-1.05)", "ITM (>1.05)"};
        
        for (int i = 0; i < moneynessBins.length; i++) {
            double min = moneynessBins[i][0];
            double max = moneynessBins[i][1];
            
            List<OptionData> binOptions = calls.stream()
                    .filter(o -> o.getMoneyness() >= min && o.getMoneyness() < max)
                    .toList();
            
            if (!binOptions.isEmpty()) {
                double meanIV = binOptions.stream()
                        .mapToDouble(o -> o.impliedVolatility)
                        .average()
                        .orElse(0);
                
                double stdIV = calculateStandardDeviation(
                        binOptions.stream().mapToDouble(o -> o.impliedVolatility).toArray()
                );
                
                System.out.printf("  %s: %.2f%% (std: %.2f%%, count: %d)%n",
                                binLabels[i], meanIV * 100, stdIV * 100, binOptions.size());
            }
        }
    }
    
    /**
     * Analyze IV by time to expiration ranges.
     */
    private static void analyzeIVByTimeToExpiration(List<OptionData> options) {
        int[][] dteBins = {{0, 30}, {30, 90}, {90, 180}, {180, 365}};
        String[] binLabels = {"< 30 days", "30-90 days", "90-180 days", "180-365 days"};
        
        for (int i = 0; i < dteBins.length; i++) {
            int min = dteBins[i][0];
            int max = dteBins[i][1];
            
            List<OptionData> binOptions = options.stream()
                    .filter(o -> o.getDaysToExpiration() >= min && o.getDaysToExpiration() < max)
                    .toList();
            
            if (!binOptions.isEmpty()) {
                double meanIV = binOptions.stream()
                        .mapToDouble(o -> o.impliedVolatility)
                        .average()
                        .orElse(0);
                
                double stdIV = calculateStandardDeviation(
                        binOptions.stream().mapToDouble(o -> o.impliedVolatility).toArray()
                );
                
                System.out.printf("  %s: %.2f%% (std: %.2f%%, count: %d)%n",
                                binLabels[i], meanIV * 100, stdIV * 100, binOptions.size());
            }
        }
    }
    
    /**
     * Analyze volatility smile pattern.
     */
    private static void analyzeVolatilitySmile(List<OptionData> calls) {
        List<OptionData> otmPuts = calls.stream()
                .filter(o -> o.getMoneyness() < 0.95)
                .toList();
        
        List<OptionData> atmCalls = calls.stream()
                .filter(o -> o.getMoneyness() >= 0.95 && o.getMoneyness() <= 1.05)
                .toList();
        
        List<OptionData> otmCalls = calls.stream()
                .filter(o -> o.getMoneyness() > 1.05)
                .toList();
        
        double otmPutIV = otmPuts.stream()
                .mapToDouble(o -> o.impliedVolatility)
                .average()
                .orElse(0);
        
        double atmCallIV = atmCalls.stream()
                .mapToDouble(o -> o.impliedVolatility)
                .average()
                .orElse(0);
        
        double otmCallIV = otmCalls.stream()
                .mapToDouble(o -> o.impliedVolatility)
                .average()
                .orElse(0);
        
        System.out.printf("  OTM Put (moneyness < 0.95):  %.2f%%%n", otmPutIV * 100);
        System.out.printf("  ATM (0.95 < m < 1.05):       %.2f%%%n", atmCallIV * 100);
        System.out.printf("  OTM Call (moneyness > 1.05): %.2f%%%n", otmCallIV * 100);
        
        // Check if smile pattern exists
        double smileStrength = Math.max(otmPutIV, otmCallIV) - atmCallIV;
        if (smileStrength > 0.01) { // 1% threshold
            System.out.printf("  Volatility smile detected: %.2f%%%n", smileStrength * 100);
        } else {
            System.out.println("  No significant volatility smile detected");
        }
    }
    
    /**
     * Calculate standard deviation of an array of values.
     */
    private static double calculateStandardDeviation(double[] values) {
        if (values.length == 0) return 0;
        
        double mean = 0;
        for (double value : values) {
            mean += value;
        }
        mean /= values.length;
        
        double sumSquaredDiff = 0;
        for (double value : values) {
            sumSquaredDiff += Math.pow(value - mean, 2);
        }
        
        return Math.sqrt(sumSquaredDiff / values.length);
    }
}