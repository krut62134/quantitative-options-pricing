package com.options.models;

import org.apache.commons.math3.distribution.NormalDistribution;

/**
 * Black-Scholes option pricing model for European options.
 * 
 * Assumptions:
 * - European exercise (only at maturity)
 * - No dividends
 * - Constant risk-free rate and volatility
 * - Log-normal price distribution
 */
public class BlackScholes {
    private final double S;      // Current stock price
    private final double K;      // Strike price
    private final double T;      // Time to maturity (years)
    private final double r;      // Risk-free rate (decimal)
    private final double sigma;  // Volatility (decimal)
    
    // Pre-calculated values for efficiency
    private final double d1;
    private final double d2;
    private final NormalDistribution normal;
    
    /**
     * Initialize Black-Scholes model.
     * 
     * @param S Current stock price
     * @param K Strike price
     * @param T Time to maturity (years)
     * @param r Risk-free rate (decimal, e.g., 0.05 for 5%)
     * @param sigma Volatility (decimal, e.g., 0.20 for 20%)
     */
    public BlackScholes(double S, double K, double T, double r, double sigma) {
        this.S = S;
        this.K = K;
        this.T = T;
        this.r = r;
        this.sigma = sigma;
        this.normal = new NormalDistribution();
        
        // Calculate d1 and d2 once
        this.d1 = calculateD1();
        this.d2 = calculateD2();
    }
    
    /**
     * Calculate d1 parameter.
     * d1 = [ln(S/K) + (r + 0.5*σ²)T] / (σ*√T)
     */
    private double calculateD1() {
        double numerator = Math.log(S / K) + (r + 0.5 * sigma * sigma) * T;
        double denominator = sigma * Math.sqrt(T);
        return numerator / denominator;
    }
    
    /**
     * Calculate d2 parameter.
     * d2 = d1 - σ*√T
     */
    private double calculateD2() {
        return d1 - sigma * Math.sqrt(T);
    }
    
    /**
     * Calculate European call option price.
     * C = S*N(d1) - K*e^(-rT)*N(d2)
     * 
     * @return Call option price
     */
    public double callPrice() {
        return S * normal.cumulativeProbability(d1) - 
               K * Math.exp(-r * T) * normal.cumulativeProbability(d2);
    }
    
    /**
     * Calculate European put option price.
     * P = K*e^(-rT)*N(-d2) - S*N(-d1)
     * 
     * @return Put option price
     */
    public double putPrice() {
        return K * Math.exp(-r * T) * normal.cumulativeProbability(-d2) - 
               S * normal.cumulativeProbability(-d1);
    }
    
    /**
     * Option Greeks for both call and put options.
     * 
     * @return Greeks object containing all Greeks
     */
    public Greeks calculateGreeks() {
        // Delta
        double deltaCall = normal.cumulativeProbability(d1);
        double deltaPut = deltaCall - 1;
        
        // Gamma (same for call and put)
        double gamma = normal.density(d1) / (S * sigma * Math.sqrt(T));
        
        // Vega (same for call and put, in dollars per 1% change in vol)
        double vega = S * normal.density(d1) * Math.sqrt(T) / 100;
        
        // Theta (per day)
        double thetaCall = (-(S * normal.density(d1) * sigma) / (2 * Math.sqrt(T)) -
                            r * K * Math.exp(-r * T) * normal.cumulativeProbability(d2)) / 365;
        double thetaPut = (-(S * normal.density(d1) * sigma) / (2 * Math.sqrt(T)) +
                           r * K * Math.exp(-r * T) * normal.cumulativeProbability(-d2)) / 365;
        
        // Rho (per 1% change in rate)
        double rhoCall = K * T * Math.exp(-r * T) * normal.cumulativeProbability(d2) / 100;
        double rhoPut = -K * T * Math.exp(-r * T) * normal.cumulativeProbability(-d2) / 100;
        
        return new Greeks(deltaCall, deltaPut, gamma, vega, thetaCall, thetaPut, rhoCall, rhoPut);
    }
    
    /**
     * Print complete option pricing summary.
     */
    public void printSummary() {
        System.out.println("==========================================================");
        System.out.println("                BLACK-SCHOLES OPTION PRICING            ");
        System.out.println("==========================================================");
        
        System.out.println("\nInput Parameters:");
        System.out.printf("  Stock Price (S):      $%.2f%n", S);
        System.out.printf("  Strike Price (K):     $%.2f%n", K);
        System.out.printf("  Time to Maturity (T): %.4f years (%.0f days)%n", T, T * 365);
        System.out.printf("  Risk-free Rate (r):   %.2f%%%n", r * 100);
        System.out.printf("  Volatility (σ):       %.2f%%%n", sigma * 100);
        
        System.out.println("\nIntermediate Values:");
        System.out.printf("  d1: %.6f%n", d1);
        System.out.printf("  d2: %.6f%n", d2);
        System.out.printf("  N(d1): %.6f%n", normal.cumulativeProbability(d1));
        System.out.printf("  N(d2): %.6f%n", normal.cumulativeProbability(d2));
        
        double call = callPrice();
        double put = putPrice();
        
        System.out.println("\nOption Prices:");
        System.out.printf("  Call Option: $%.4f%n", call);
        System.out.printf("  Put Option:  $%.4f%n", put);
        
        Greeks greeks = calculateGreeks();
        System.out.println("\nGreeks:");
        System.out.println("  Greek      Call        Put");
        System.out.println("  ---------- ----------- -----------");
        System.out.printf("  Delta      %11.6f  %11.6f%n", greeks.deltaCall, greeks.deltaPut);
        System.out.printf("  Gamma      %11.6f  %11.6f%n", greeks.gamma, greeks.gamma);
        System.out.printf("  Vega       %11.6f  %11.6f%n", greeks.vega, greeks.vega);
        System.out.printf("  Theta      %11.6f  %11.6f%n", greeks.thetaCall, greeks.thetaPut);
        System.out.printf("  Rho        %11.6f  %11.6f%n", greeks.rhoCall, greeks.rhoPut);
        
        System.out.println("==========================================================");
    }
    
    // Getters for the calculated values
    public double getD1() { return d1; }
    public double getD2() { return d2; }
    public double getS() { return S; }
    public double getK() { return K; }
    public double getT() { return T; }
    public double getR() { return r; }
    public double getSigma() { return sigma; }
    
    /**
     * Inner class to hold option Greeks.
     */
    public static class Greeks {
        public final double deltaCall;
        public final double deltaPut;
        public final double gamma;
        public final double vega;
        public final double thetaCall;
        public final double thetaPut;
        public final double rhoCall;
        public final double rhoPut;
        
        public Greeks(double deltaCall, double deltaPut, double gamma, double vega,
                     double thetaCall, double thetaPut, double rhoCall, double rhoPut) {
            this.deltaCall = deltaCall;
            this.deltaPut = deltaPut;
            this.gamma = gamma;
            this.vega = vega;
            this.thetaCall = thetaCall;
            this.thetaPut = thetaPut;
            this.rhoCall = rhoCall;
            this.rhoPut = rhoPut;
        }
    }
}