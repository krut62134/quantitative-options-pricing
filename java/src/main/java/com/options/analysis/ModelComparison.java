package com.options.analysis;

import com.options.data.ImpliedVolatilityCalculator;
import com.options.data.OptionData;
import com.options.models.BlackScholes;
import com.options.models.MonteCarlo;
import com.options.models.BinomialTree;

import java.util.ArrayList;
import java.util.List;

/**
 * Compare different option pricing methods on test data.
 * 
 * Computes predictions for all three pricing models and analyzes
 * their convergence and accuracy against market prices.
 */
public class ModelComparison {
    
    /**
     * Result of comparing different pricing methods for a single option.
     */
    public static class OptionComparisonResult {
        public final double strike;
        public final double moneyness;
        public final double T;
        public final String type;
        public final double marketPrice;
        public final double marketIV;
        public final double computedIV;
        public final double bsPriceMarket;
        public final double bsPriceComputed;
        public final double mcPrice;
        public final double btPrice;
        
        public OptionComparisonResult(double strike, double moneyness, double T, String type,
                                   double marketPrice, double marketIV, double computedIV,
                                   double bsPriceMarket, double bsPriceComputed,
                                   double mcPrice, double btPrice) {
            this.strike = strike;
            this.moneyness = moneyness;
            this.T = T;
            this.type = type;
            this.marketPrice = marketPrice;
            this.marketIV = marketIV;
            this.computedIV = computedIV;
            this.bsPriceMarket = bsPriceMarket;
            this.bsPriceComputed = bsPriceComputed;
            this.mcPrice = mcPrice;
            this.btPrice = btPrice;
        }
        
        @Override
        public String toString() {
            return String.format("Option{strike=%.2f, type=%s, market=$%.4f, BS_market=$%.4f, MC=$%.4f, BT=$%.4f}",
                              strike, type, marketPrice, bsPriceMarket, mcPrice, btPrice);
        }
    }
    
    /**
     * Compute predictions for all pricing methods on a dataset.
     * 
     * @param options List of option data
     * @param r Risk-free rate
     * @return List of comparison results
     */
    public static List<OptionComparisonResult> compareAllMethods(List<OptionData> options, double r) {
        List<OptionComparisonResult> results = new ArrayList<>();
        ImpliedVolatilityCalculator ivCalculator = new ImpliedVolatilityCalculator();
        
        System.out.printf("%nComputing predictions for %d test options...%n", options.size());
        
        int processed = 0;
        for (OptionData option : options) {
            if (processed % 50 == 0) {
                System.out.printf("  Progress: %d/%d%n", processed, options.size());
            }
            processed++;
            
            double S = option.getSpotPrice();
            double K = option.strike;
            double T = option.getT();
            String optionType = option.getType();
            double marketIV = option.impliedVolatility;
            double marketPrice = option.getLastPriceAsDouble();
            
            // Black-Scholes with market IV
            BlackScholes bsMarket = new BlackScholes(S, K, T, r, marketIV);
            double bsPriceMarket = "call".equals(optionType) ? bsMarket.callPrice() : bsMarket.putPrice();
            
            // Calculate implied volatility using Newton-Raphson
            double computedIV = ivCalculator.calculateWithFallback(marketPrice, S, K, T, r, optionType);
            double bsPriceComputed = Double.NaN;
            
            if (!Double.isNaN(computedIV)) {
                BlackScholes bsComputed = new BlackScholes(S, K, T, r, computedIV);
                bsPriceComputed = "call".equals(optionType) ? bsComputed.callPrice() : bsComputed.putPrice();
            }
            
            // Monte Carlo (smaller number of sims for efficiency)
            MonteCarlo mc = new MonteCarlo(S, K, T, r, marketIV, 100000);
            mc.simulate(false);
            double mcPrice = "call".equals(optionType) ? mc.callPrice() : mc.putPrice();
            
            // Binomial Tree
            BinomialTree bt = new BinomialTree(S, K, T, r, marketIV, 500);
            double btPrice = "call".equals(optionType) ? bt.callPrice() : bt.putPrice();
            
            OptionComparisonResult result = new OptionComparisonResult(
                K, option.getMoneyness(), T, optionType, marketPrice, marketIV, computedIV,
                bsPriceMarket, bsPriceComputed, mcPrice, btPrice
            );
            
            results.add(result);
        }
        
        System.out.println("\n");
        return results;
    }
    
    /**
     * Analyze and print comparison results.
     * 
     * @param results List of comparison results
     */
    public static void analyzeResults(List<OptionComparisonResult> results) {
        System.out.println("=".repeat(70));
        System.out.println("                 MODEL COMPARISON ANALYSIS                 ");
        System.out.println("=".repeat(70));
        
        // Filter results where we have all values
        List<OptionComparisonResult> validResults = results.stream()
                .filter(r -> !Double.isNaN(r.bsPriceComputed))
                .toList();
        
        if (validResults.isEmpty()) {
            System.out.println("No valid results for analysis.");
            return;
        }
        
        // Calculate error statistics
        analyzePriceErrors(validResults);
        analyzeIVErrors(validResults);
        analyzeModelAgreement(validResults);
        
        System.out.println("=".repeat(70));
    }
    
    /**
     * Analyze pricing errors between models and market prices.
     */
    private static void analyzePriceErrors(List<OptionComparisonResult> results) {
        System.out.println("\nPRICE PREDICTION ERRORS:");
        System.out.println("-".repeat(70));
        
        double[] priceErrors = new double[results.size()];
        double[] mcErrors = new double[results.size()];
        double[] btErrors = new double[results.size()];
        
        for (int i = 0; i < results.size(); i++) {
            OptionComparisonResult r = results.get(i);
            priceErrors[i] = Math.abs(r.marketPrice - r.bsPriceMarket);
            mcErrors[i] = Math.abs(r.marketPrice - r.mcPrice);
            btErrors[i] = Math.abs(r.marketPrice - r.btPrice);
        }
        
        System.out.printf("Black-Scholes (Market IV):%n");
        System.out.printf("  MAE: $%.4f%n", calculateMean(priceErrors));
        System.out.printf("  RMSE: $%.4f%n", Math.sqrt(calculateMeanSquare(priceErrors)));
        System.out.printf("  Max Error: $%.4f%n", calculateMax(priceErrors));
        
        System.out.printf("%nMonte Carlo:%n");
        System.out.printf("  MAE: $%.4f%n", calculateMean(mcErrors));
        System.out.printf("  RMSE: $%.4f%n", Math.sqrt(calculateMeanSquare(mcErrors)));
        System.out.printf("  Max Error: $%.4f%n", calculateMax(mcErrors));
        
        System.out.printf("%nBinomial Tree:%n");
        System.out.printf("  MAE: $%.4f%n", calculateMean(btErrors));
        System.out.printf("  RMSE: $%.4f%n", Math.sqrt(calculateMeanSquare(btErrors)));
        System.out.printf("  Max Error: $%.4f%n", calculateMax(btErrors));
    }
    
    /**
     * Analyze implied volatility calculation errors.
     */
    private static void analyzeIVErrors(List<OptionComparisonResult> results) {
        System.out.println("\nIMPLIED VOLATILITY ERRORS:");
        System.out.println("-".repeat(70));
        
        double[] ivErrors = new double[results.size()];
        int validCount = 0;
        
        for (int i = 0; i < results.size(); i++) {
            OptionComparisonResult r = results.get(i);
            if (!Double.isNaN(r.computedIV)) {
                ivErrors[validCount] = Math.abs(r.marketIV - r.computedIV) * 100; // Convert to percentage
                validCount++;
            }
        }
        
        if (validCount > 0) {
            double[] validErrors = new double[validCount];
            System.arraycopy(ivErrors, 0, validErrors, 0, validCount);
            
            System.out.printf("Newton-Raphson IV Calculator:%n");
            System.out.printf("  MAE: %.2f%%%n", calculateMean(validErrors));
            System.out.printf("  RMSE: %.2f%%%n", Math.sqrt(calculateMeanSquare(validErrors)));
            System.out.printf("  Max Error: %.2f%%%n", calculateMax(validErrors));
            System.out.printf("  Valid Calculations: %d/%d (%.1f%%)%n", 
                            validCount, results.size(), 100.0 * validCount / results.size());
        } else {
            System.out.println("No valid implied volatility calculations.");
        }
    }
    
    /**
     * Analyze agreement between the three pricing models.
     */
    private static void analyzeModelAgreement(List<OptionComparisonResult> results) {
        System.out.println("\nMODEL AGREEMENT ANALYSIS:");
        System.out.println("-".repeat(70));
        
        double[] mcVsBsErrors = new double[results.size()];
        double[] btVsBsErrors = new double[results.size()];
        double[] mcVsBtErrors = new double[results.size()];
        
        for (int i = 0; i < results.size(); i++) {
            OptionComparisonResult r = results.get(i);
            mcVsBsErrors[i] = Math.abs(r.mcPrice - r.bsPriceMarket);
            btVsBsErrors[i] = Math.abs(r.btPrice - r.bsPriceMarket);
            mcVsBtErrors[i] = Math.abs(r.mcPrice - r.btPrice);
        }
        
        System.out.printf("Monte Carlo vs Black-Scholes:%n");
        System.out.printf("  Mean Difference: $%.6f%n", calculateMean(mcVsBsErrors));
        System.out.printf("  Max Difference: $%.6f%n", calculateMax(mcVsBsErrors));
        
        System.out.printf("%nBinomial Tree vs Black-Scholes:%n");
        System.out.printf("  Mean Difference: $%.6f%n", calculateMean(btVsBsErrors));
        System.out.printf("  Max Difference: $%.6f%n", calculateMax(btVsBsErrors));
        
        System.out.printf("%nMonte Carlo vs Binomial Tree:%n");
        System.out.printf("  Mean Difference: $%.6f%n", calculateMean(mcVsBtErrors));
        System.out.printf("  Max Difference: $%.6f%n", calculateMax(mcVsBtErrors));
    }
    
    /**
     * Print detailed results table.
     * 
     * @param results List of comparison results
     * @param limit Maximum number of results to print
     */
    public static void printDetailedTable(List<OptionComparisonResult> results, int limit) {
        System.out.println("\nDETAILED COMPARISON TABLE:");
        System.out.println("-".repeat(140));
        System.out.printf("%-8s %-10s %-8s %-6s %-12s %-12s %-12s %-12s %-12s %-12s%n",
                        "Strike", "Moneyness", "T", "Type", "Market", "BS_Market", "MC", "BT", "IV_Error");
        System.out.println("-".repeat(140));
        
        int count = Math.min(results.size(), limit);
        for (int i = 0; i < count; i++) {
            OptionComparisonResult r = results.get(i);
            double ivError = Double.isNaN(r.computedIV) ? Double.NaN : 
                           Math.abs((r.marketIV - r.computedIV) * 100);
            
            System.out.printf("%-8.0f %-10.4f %-8.4f %-6s %-12.4f %-12.4f %-12.4f %-12.4f %-12.4f%n",
                            r.strike, r.moneyness, r.T, r.type, r.marketPrice,
                            r.bsPriceMarket, r.mcPrice, r.btPrice, ivError);
        }
        
        if (results.size() > limit) {
            System.out.printf("... and %d more results%n", results.size() - limit);
        }
        System.out.println("-".repeat(140));
    }
    
    // Utility methods for statistics
    private static double calculateMean(double[] values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }
    
    private static double calculateMeanSquare(double[] values) {
        double sum = 0;
        for (double v : values) {
            sum += v * v;
        }
        return sum / values.length;
    }
    
    private static double calculateMax(double[] values) {
        double max = values[0];
        for (double v : values) {
            if (v > max) max = v;
        }
        return max;
    }
}