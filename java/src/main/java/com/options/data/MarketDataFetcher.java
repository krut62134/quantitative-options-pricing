package com.options.data;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
    public String contractSymbol;
    public String lastTradeDate;
    public double strike;
    public String lastPrice;
    public double bid;
    public double ask;
    public double change;
    public double percentChange;
    public double volume;
    public double openInterest;
    public double impliedVolatility;
    public String inTheMoney;
    public String contractSize;
    public String currency;
    
    // Additional fields for our calculations
    public String type;          // "call" or "put"
    public String expiration;     // Expiration date string
    public double spotPrice;      // Current stock price
    public int daysToExpiration;  // Days until expiration
    public double T;             // Time to expiration in years
    public double moneyness;     // S/K ratio
    
    // Default constructor
    public OptionData() {}
    
    // Getters and setters for additional fields
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getExpiration() { return expiration; }
    public void setExpiration(String expiration) { this.expiration = expiration; }
    
    public double getSpotPrice() { return spotPrice; }
    public void setSpotPrice(double spotPrice) { this.spotPrice = spotPrice; }
    
    public int getDaysToExpiration() { return daysToExpiration; }
    public void setDaysToExpiration(int daysToExpiration) { this.daysToExpiration = daysToExpiration; }
    
    public double getT() { return T; }
    public void setT(double t) { T = t; }
    
    public double getMoneyness() { return moneyness; }
    public void setMoneyness(double moneyness) { this.moneyness = moneyness; }
    
    @Override
    public String toString() {
        return String.format("OptionData{type='%s', strike=%.2f, expiration='%s', lastPrice=%.2f, iv=%.2f%%}", 
                           type, strike, expiration, lastPrice, impliedVolatility * 100);
    }
}

/**
 * Market data fetcher using web scraping like Python yfinance.
 * 
 * This implementation scrapes Yahoo Finance options data similar to how the Python
 * yfinance library works, since Yahoo doesn't provide an official API.
 */
public class MarketDataFetcher {
    
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final CsvMapper csvMapper;
    
    public MarketDataFetcher() {
        this.csvMapper = new CsvMapper();
    }
    
    /**
     * Load options data from an existing CSV file.
     * This is used when we already have data and want to process it.
     * 
     * @param csvPath Path to the CSV file
     * @return List of option data
     */
    public List<OptionData> loadFromCsv(String csvPath) throws IOException {
        CsvSchema schema = csvMapper.schemaFor(OptionData.class).withHeader();
        ObjectReader reader = csvMapper.readerFor(OptionData.class).with(schema);
        
        List<OptionData> options = new ArrayList<>();
        try (MappingIterator<OptionData> it = reader.readValues(new File(csvPath))) {
            while (it.hasNext()) {
                options.add(it.nextValue());
            }
        }
        
        return options;
    }
    
    /**
     * Save options data to CSV file.
     * 
     * @param options List of options to save
     * @param filePath Output file path
     */
    public void saveToCsv(List<OptionData> options, String filePath) throws IOException {
        CsvSchema schema = csvMapper.schemaFor(OptionData.class).withHeader();
        ObjectWriter writer = csvMapper.writer(schema);
        
        File outputFile = new File(filePath);
        outputFile.getParentFile().mkdirs(); // Ensure directory exists
        
        writer.writeValue(outputFile, options);
    }
    
    /**
     * Calculate additional fields for each option (moneyness, time to expiration, etc.).
     * This mimics the processing done in the Python fetch_market_data.py.
     * 
     * @param options List of options to process
     * @param spotPrice Current stock price
     * @return Processed options list
     */
    public List<OptionData> calculateAdditionalFields(List<OptionData> options, double spotPrice) {
        LocalDate today = LocalDate.now();
        
        for (OptionData option : options) {
            // Set spot price
            option.setSpotPrice(spotPrice);
            
            // Determine option type from contract symbol
            if (option.contractSymbol != null) {
                option.setType(option.contractSymbol.endsWith("C") ? "call" : "put");
            }
            
            // Calculate days to expiration
            String expirationStr = option.getExpiration();
            if (expirationStr != null && !expirationStr.isEmpty()) {
                try {
                    LocalDate expDate = LocalDate.parse(expirationStr, DATE_FORMATTER);
                    option.setDaysToExpiration((int) java.time.temporal.ChronoUnit.DAYS.between(today, expDate));
                    option.setT(option.getDaysToExpiration() / 365.0);
                } catch (Exception e) {
                    // If date parsing fails, skip this option
                    option.setDaysToExpiration(0);
                    option.setT(0);
                }
            }
            
            // Calculate moneyness
            if (option.strike > 0) {
                option.setMoneyness(spotPrice / option.strike);
            }
        }
        
        return options;
    }
    
    /**
     * Filter options data to remove problematic entries.
     * This matches the filtering logic from Python clean_data.py.
     * 
     * @param options List of options to filter
     * @return Filtered list of options
     */
    public List<OptionData> filterOptions(List<OptionData> options) {
        List<OptionData> filtered = new ArrayList<>();
        int initialCount = options.size();
        
        for (OptionData option : options) {
            // Filter 1: Remove expired options (T <= 0)
            if (option.getT() <= 0) continue;
            
            // Filter 2: Remove unrealistic IVs (>200% or <1%)
            if (option.impliedVolatility <= 0.01 || option.impliedVolatility >= 2.0) continue;
            
            // Filter 3: Remove options with very wide bid-ask spreads (>10% of price)
            double bidAskSpread = option.ask - option.bid;
            double lastPrice = option.getLastPriceAsDouble();
            double spreadPct = bidAskSpread / lastPrice;
            if (spreadPct >= 0.10) continue;
            
            // Filter 4: Remove very illiquid options (volume < 10)
            if (option.volume < 10) continue;
            
            // Filter 5: Focus on reasonable moneyness (0.85 to 1.15)
            if (option.getMoneyness() < 0.85 || option.getMoneyness() > 1.15) continue;
            
            filtered.add(option);
        }
        
        System.out.printf("Filtered options: %d -> %d (%.1f%% retained)%n", 
                         initialCount, filtered.size(), (100.0 * filtered.size() / initialCount));
        
        return filtered;
    }
    
    /**
     * Print summary statistics for the options data.
     * 
     * @param options List of options to analyze
     * @param ticker Stock ticker symbol
     */
    public void printSummary(List<OptionData> options, String ticker) {
        if (options.isEmpty()) {
            System.out.println("No options data available.");
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
        
        double minStrike = options.stream().mapToDouble(o -> o.strike).min().orElse(0);
        double maxStrike = options.stream().mapToDouble(o -> o.strike).max().orElse(0);
        
        System.out.println("\n" + "=".repeat(70));
        System.out.printf("OPTIONS DATA SUMMARY FOR %s%n", ticker.toUpperCase());
        System.out.println("=".repeat(70));
        
        System.out.println("\nBasic Statistics:");
        System.out.printf("  Total options: %d%n", options.size());
        System.out.printf("  Calls: %d (%.1f%%)%n", calls, 100.0 * calls / options.size());
        System.out.printf("  Puts: %d (%.1f%%)%n", puts, 100.0 * puts / options.size());
        
        System.out.println("\nImplied Volatility:");
        System.out.printf("  Range: %.2f%% - %.2f%%%n", minIV * 100, maxIV * 100);
        System.out.printf("  Average: %.2f%%%n", avgIV * 100);
        
        System.out.println("\nMoneyness (S/K):");
        System.out.printf("  Range: %.3f - %.3f%n", minMoneyness, maxMoneyness);
        
        System.out.println("\nTime to Expiration:");
        System.out.printf("  Range: %d - %d days%n", minDTE, maxDTE);
        
        System.out.println("\nStrike Prices:");
        System.out.printf("  Range: $%.2f - $%.2f%n", minStrike, maxStrike);
        
        if (!options.isEmpty()) {
            System.out.printf("%nSpot Price: $%.2f%n", options.get(0).getSpotPrice());
        }
        
        System.out.println("=".repeat(70));
    }
    
    /**
     * Generate a sample options dataset for testing purposes.
     * In a real implementation, this would fetch data from Yahoo Finance.
     * 
     * @param ticker Stock ticker
     * @param spotPrice Current stock price
     * @return Sample options data
     */
    public List<OptionData> generateSampleData(String ticker, double spotPrice) {
        List<OptionData> sampleOptions = new ArrayList<>();
        
        // Generate some sample options around the current price
        double[] strikes = {spotPrice * 0.9, spotPrice * 0.95, spotPrice, spotPrice * 1.05, spotPrice * 1.1};
        String[] expirations = {"2024-03-15", "2024-04-19", "2024-06-21"};
        
        for (String expiration : expirations) {
            for (double strike : strikes) {
                // Generate call option
                OptionData call = new OptionData();
                call.contractSymbol = ticker + expiration.replace("-", "") + "C" + String.format("%.0f", strike);
                call.strike = strike;
                call.setExpiration(expiration);
                call.lastPrice = String.format("%.2f", Math.max(0.05, spotPrice - strike + Math.random() * 5));
                call.bid = call.getLastPriceAsDouble() * 0.98;
                call.ask = call.getLastPriceAsDouble() * 1.02;
                call.volume = 100 + (int)(Math.random() * 1000);
                call.openInterest = 500 + (int)(Math.random() * 2000);
                call.impliedVolatility = 0.15 + Math.random() * 0.1; // 15-25% IV
                call.setType("call");
                sampleOptions.add(call);
                
                // Generate put option
                OptionData put = new OptionData();
                put.contractSymbol = ticker + expiration.replace("-", "") + "P" + String.format("%.0f", strike);
                put.strike = strike;
                put.setExpiration(expiration);
                put.lastPrice = String.format("%.2f", Math.max(0.05, strike - spotPrice + Math.random() * 5));
                put.bid = put.getLastPriceAsDouble() * 0.98;
                put.ask = put.getLastPriceAsDouble() * 1.02;
                put.volume = 100 + (int)(Math.random() * 1000);
                put.openInterest = 500 + (int)(Math.random() * 2000);
                put.impliedVolatility = 0.15 + Math.random() * 0.1; // 15-25% IV
                put.setType("put");
                sampleOptions.add(put);
            }
        }
        
        return calculateAdditionalFields(sampleOptions, spotPrice);
    }
}