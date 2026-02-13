package com.options.utils;

import com.options.models.BlackScholes;
import com.options.models.MonteCarlo;
import com.options.models.BinomialTree;

/**
 * Validation utilities for option pricing models.
 * 
 * Includes put-call parity validation, textbook example verification,
 * and convergence testing between different pricing methods.
 */
public class Validation {
    
    private static final double PUT_CALL_PARITY_TOLERANCE = 0.01;
    private static final double PRICE_TOLERANCE = 0.10;
    
    /**
     * Validate put-call parity: C - P = S - K*e^(-rT)
     * 
     * For European options, this relationship must hold by arbitrage.
     * 
     * @param S Current stock price
     * @param K Strike price
     * @param T Time to maturity (years)
     * @param r Risk-free rate
     * @param callPrice Call option price
     * @param putPrice Put option price
     * @return ParityValidationResult with validation details
     */
    public static ParityValidationResult validatePutCallParity(double S, double K, double T, double r, 
                                                          double callPrice, double putPrice) {
        double parityLhs = callPrice - putPrice;
        double parityRhs = S - K * Math.exp(-r * T);
        double difference = Math.abs(parityLhs - parityRhs);
        boolean valid = difference < PUT_CALL_PARITY_TOLERANCE;
        
        return new ParityValidationResult(parityLhs, parityRhs, difference, valid);
    }
    
    /**
     * Compare all three pricing models against each other.
     * 
     * @param S Current stock price
     * @param K Strike price
     * @param T Time to maturity (years)
     * @param r Risk-free rate
     * @param sigma Volatility
     * @return ModelComparisonResult with comparison details
     */
    public static ModelComparisonResult compareModels(double S, double K, double T, double r, double sigma) {
        System.out.printf("%nComparing models for: S=$%.2f, K=$%.2f, T=%.2fy, r=%.2f%%, σ=%.2f%%%n", 
                        S, K, T, r * 100, sigma * 100);
        System.out.println("-".repeat(70));
        
        // Black-Scholes (analytical benchmark)
        BlackScholes bs = new BlackScholes(S, K, T, r, sigma);
        double bsCall = bs.callPrice();
        double bsPut = bs.putPrice();
        
        // Monte Carlo
        MonteCarlo mc = new MonteCarlo(S, K, T, r, sigma, 500000);
        mc.simulate(false);
        double mcCall = mc.callPrice();
        double mcPut = mc.putPrice();
        
        // Binomial Tree
        BinomialTree bt = new BinomialTree(S, K, T, r, sigma, 1000);
        double btCall = bt.callPrice();
        double btPut = bt.putPrice();
        
        return new ModelComparisonResult(bsCall, bsPut, mcCall, mcPut, btCall, btPut);
    }
    
    /**
     * Test against known option pricing values from Hull's textbook.
     */
    public static void testKnownValues() {
        System.out.println("=".repeat(70));
        System.out.println("VALIDATING AGAINST KNOWN VALUES");
        System.out.println("=".repeat(70));
        
        // Test case 1: ATM option from Hull's textbook
        System.out.println("\nTest 1: ATM Option (Hull Example)");
        BlackScholes bs1 = new BlackScholes(42, 40, 0.5, 0.10, 0.20);
        double call1 = bs1.callPrice();
        double expectedCall1 = 4.76; // From Hull textbook
        double error1 = Math.abs(call1 - expectedCall1);
        
        System.out.printf("  Calculated Call: $%.4f%n", call1);
        System.out.printf("  Expected Call:   $%.4f%n", expectedCall1);
        System.out.printf("  Difference:      $%.4f%n", error1);
        System.out.printf("  %s%n", error1 < PRICE_TOLERANCE ? "✓ PASS" : "✗ FAIL");
        
        // Test case 2: Put-Call Parity
        System.out.println("\nTest 2: Put-Call Parity");
        BlackScholes bs2 = new BlackScholes(100, 100, 1.0, 0.05, 0.20);
        double call2 = bs2.callPrice();
        double put2 = bs2.putPrice();
        ParityValidationResult parity = validatePutCallParity(100, 100, 1.0, 0.05, call2, put2);
        
        System.out.printf("  C - P = %.6f%n", parity.leftSide);
        System.out.printf("  S - Ke^(-rT) = %.6f%n", parity.rightSide);
        System.out.printf("  Difference: $%.6f%n", parity.difference);
        System.out.printf("  %s%n", parity.valid ? "✓ PASS" : "✗ FAIL");
        
        // Test case 3: Deep ITM call should equal intrinsic value
        System.out.println("\nTest 3: Deep ITM Call (should ≈ intrinsic value)");
        BlackScholes bs3 = new BlackScholes(150, 100, 0.01, 0.05, 0.20);
        double call3 = bs3.callPrice();
        double intrinsic = 150 - 100;
        double timeValue = call3 - intrinsic;
        
        System.out.printf("  Call Price:      $%.4f%n", call3);
        System.out.printf("  Intrinsic Value: $%.4f%n", intrinsic);
        System.out.printf("  Time Value:      $%.4f%n", timeValue);
        System.out.printf("  %s%n", timeValue < 1.0 ? "✓ PASS" : "✗ FAIL");
        
        // Summary
        System.out.println("\n" + "=".repeat(70));
        System.out.println("VALIDATION SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("Hull Test: %s (Error: $%.4f)%n", 
                        error1 < PRICE_TOLERANCE ? "PASS" : "FAIL", error1);
        System.out.printf("Put-Call Parity: %s (Error: $%.6f)%n", 
                        parity.valid ? "PASS" : "FAIL", parity.difference);
        System.out.printf("ITM Call: %s (Time Value: $%.4f)%n", 
                        timeValue < 1.0 ? "PASS" : "FAIL", timeValue);
        System.out.println("=".repeat(70));
    }
    
    /**
     * Test convergence of Monte Carlo to Black-Scholes with increasing simulation counts.
     */
    public static void testMonteCarloConvergence() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("MONTE CARLO CONVERGENCE TO BLACK-SCHOLES");
        System.out.println("=".repeat(70));
        
        double S = 100, K = 100, T = 1.0, r = 0.05, sigma = 0.20;
        
        // Black-Scholes benchmark
        BlackScholes bs = new BlackScholes(S, K, T, r, sigma);
        double bsCall = bs.callPrice();
        double bsPut = bs.putPrice();
        
        System.out.printf("%nBLACK-SCHOLES (Benchmark)%n");
        System.out.printf("  Call: $%.4f%n", bsCall);
        System.out.printf("  Put:  $%.4f%n", bsPut);
        
        // Test different simulation counts
        int[] simCounts = {10000, 50000, 100000, 500000, 1000000};
        
        System.out.println("\n" + "-".repeat(70));
        System.out.printf("%-12s %-15s %-15s %-10s%n", "Sims", "Call Error", "Put Error", "Time (s)");
        System.out.println("-".repeat(70));
        
        for (int nSims : simCounts) {
            long startTime = System.currentTimeMillis();
            
            MonteCarlo mc = new MonteCarlo(S, K, T, r, sigma, nSims);
            mc.simulate(false);
            double mcCall = mc.callPrice();
            double mcPut = mc.putPrice();
            
            long elapsed = System.currentTimeMillis() - startTime;
            double callError = Math.abs(mcCall - bsCall);
            double putError = Math.abs(mcPut - bsPut);
            
            System.out.printf("%-12d $%-14.6f $%-14.6f %-10.3f%n", 
                            nSims, callError, putError, elapsed / 1000.0);
        }
        
        System.out.println("=".repeat(70));
    }
    
    /**
     * Test convergence of Binomial Tree to Black-Scholes with increasing step counts.
     */
    public static void testBinomialConvergence() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("BINOMIAL TREE CONVERGENCE TO BLACK-SCHOLES");
        System.out.println("=".repeat(70));
        
        double S = 100, K = 100, T = 1.0, r = 0.05, sigma = 0.20;
        
        // Black-Scholes benchmark
        BlackScholes bs = new BlackScholes(S, K, T, r, sigma);
        double bsCall = bs.callPrice();
        
        System.out.printf("%nBLACK-SCHOLES (Benchmark)%n");
        System.out.printf("  Call: $%.4f%n", bsCall);
        
        // Test different step counts
        int[] stepCounts = {10, 50, 100, 500, 1000, 5000};
        
        System.out.println("\n" + "-".repeat(70));
        System.out.printf("%-10s %-15s %-15s %-10s%n", "Steps", "Call Price", "Call Error", "Time (s)");
        System.out.println("-".repeat(70));
        
        for (int nSteps : stepCounts) {
            long startTime = System.currentTimeMillis();
            
            BinomialTree bt = new BinomialTree(S, K, T, r, sigma, nSteps);
            double btCall = bt.callPrice();
            
            long elapsed = System.currentTimeMillis() - startTime;
            double error = Math.abs(btCall - bsCall);
            double errorPct = error / bsCall * 100;
            
            System.out.printf("%-10d $%-14.6f $%-14.6f %-10.3f%n", 
                            nSteps, btCall, error, elapsed / 1000.0);
        }
        
        System.out.println("=".repeat(70));
    }
    
    /**
     * Result class for put-call parity validation.
     */
    public static class ParityValidationResult {
        public final double leftSide;
        public final double rightSide;
        public final double difference;
        public final boolean valid;
        
        public ParityValidationResult(double leftSide, double rightSide, double difference, boolean valid) {
            this.leftSide = leftSide;
            this.rightSide = rightSide;
            this.difference = difference;
            this.valid = valid;
        }
    }
    
    /**
     * Result class for model comparison.
     */
    public static class ModelComparisonResult {
        public final double bsCall;
        public final double bsPut;
        public final double mcCall;
        public final double mcPut;
        public final double btCall;
        public final double btPut;
        
        public ModelComparisonResult(double bsCall, double bsPut, double mcCall, double mcPut,
                                  double btCall, double btPut) {
            this.bsCall = bsCall;
            this.bsPut = bsPut;
            this.mcCall = mcCall;
            this.mcPut = mcPut;
            this.btCall = btCall;
            this.btPut = btPut;
        }
        
        public void printComparison() {
            System.out.println("\nModel Comparison Results:");
            System.out.println("Model           | Call Price | Put Price  | Call Error | Put Error");
            System.out.println("----------------|------------|------------|------------|----------");
            System.out.printf("Black-Scholes   | $%.6f | $%.6f | $%.6f | $%.6f%n", 
                            bsCall, bsPut, 0.0, 0.0);
            System.out.printf("Monte Carlo     | $%.6f | $%.6f | $%.6f | $%.6f%n", 
                            mcCall, mcPut, Math.abs(mcCall - bsCall), Math.abs(mcPut - bsPut));
            System.out.printf("Binomial Tree   | $%.6f | $%.6f | $%.6f | $%.6f%n", 
                            btCall, btPut, Math.abs(btCall - bsCall), Math.abs(btPut - bsPut));
        }
    }
}