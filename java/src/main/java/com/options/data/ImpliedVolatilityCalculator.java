package com.options.data;

import com.options.models.BlackScholes;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BrentSolver;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.exception.TooManyEvaluationsException;

/**
 * Calculator for implied volatility using Newton-Raphson method.
 * 
 * Inverts the Black-Scholes model to find the volatility that produces
 * a given market price for an option.
 */
public class ImpliedVolatilityCalculator {
    
    private static final double DEFAULT_INITIAL_SIGMA = 0.3;
    private static final int MAX_ITERATIONS = 100;
    private static final double TOLERANCE = 1e-6;
    private static final double MIN_SIGMA = 0.01;
    private static final double MAX_SIGMA = 5.0;
    
    private final NormalDistribution normal;
    
    public ImpliedVolatilityCalculator() {
        this.normal = new NormalDistribution();
    }
    
    /**
     * Calculate implied volatility using Newton-Raphson method.
     * More efficient than bisection for well-behaved functions.
     * 
     * @param marketPrice Market price of the option
     * @param S Current stock price
     * @param K Strike price
     * @param T Time to maturity (years)
     * @param r Risk-free rate
     * @param optionType "call" or "put"
     * @return Implied volatility, or Double.NaN if convergence fails
     */
    public double calculateNewtonRaphson(double marketPrice, double S, double K, 
                                       double T, double r, String optionType) {
        return calculateNewtonRaphson(marketPrice, S, K, T, r, optionType, DEFAULT_INITIAL_SIGMA);
    }
    
    /**
     * Calculate implied volatility using Newton-Raphson method with custom initial guess.
     */
    public double calculateNewtonRaphson(double marketPrice, double S, double K, 
                                       double T, double r, String optionType, double initialSigma) {
        double sigma = initialSigma;
        
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            // Calculate price and vega at current sigma
            BlackScholes bs = new BlackScholes(S, K, T, r, sigma);
            
            double price;
            if ("call".equalsIgnoreCase(optionType)) {
                price = bs.callPrice();
            } else if ("put".equalsIgnoreCase(optionType)) {
                price = bs.putPrice();
            } else {
                throw new IllegalArgumentException("Option type must be 'call' or 'put'");
            }
            
            // Vega (sensitivity to volatility)
            double vega = calculateVega(S, K, T, r, sigma);
            
            // Price difference
            double diff = price - marketPrice;
            
            // Check convergence
            if (Math.abs(diff) < TOLERANCE) {
                return sigma;
            }
            
            // Avoid division by zero or very small numbers
            if (vega < 1e-10) {
                return Double.NaN;
            }
            
            // Newton update: sigma_new = sigma_old - f(sigma) / f'(sigma)
            sigma = sigma - diff / vega;
            
            // Keep sigma positive and reasonable
            sigma = Math.max(MIN_SIGMA, Math.min(sigma, MAX_SIGMA));
        }
        
        // Failed to converge
        return Double.NaN;
    }
    
    /**
     * Calculate implied volatility using Brent's method as fallback.
     * More robust but potentially slower than Newton-Raphson.
     * 
     * @param marketPrice Market price of the option
     * @param S Current stock price
     * @param K Strike price
     * @param T Time to maturity (years)
     * @param r Risk-free rate
     * @param optionType "call" or "put"
     * @return Implied volatility, or Double.NaN if not found
     */
    public double calculateBrent(double marketPrice, double S, double K, 
                                double T, double r, String optionType) {
        UnivariateFunction priceFunction = new UnivariateFunction() {
            @Override
            public double value(double sigma) {
                BlackScholes bs = new BlackScholes(S, K, T, r, sigma);
                if ("call".equalsIgnoreCase(optionType)) {
                    return bs.callPrice() - marketPrice;
                } else {
                    return bs.putPrice() - marketPrice;
                }
            }
        };
        
        BrentSolver solver = new BrentSolver(TOLERANCE);
        
        try {
            // Search between 1% and 500% volatility
            return solver.solve(MAX_ITERATIONS, priceFunction, MIN_SIGMA, MAX_SIGMA);
        } catch (TooManyEvaluationsException e) {
            return Double.NaN;
        }
    }
    
    /**
     * Calculate implied volatility with fallback to Brent's method if Newton-Raphson fails.
     * 
     * @param marketPrice Market price of the option
     * @param S Current stock price
     * @param K Strike price
     * @param T Time to maturity (years)
     * @param r Risk-free rate
     * @param optionType "call" or "put"
     * @return Implied volatility, or Double.NaN if not found
     */
    public double calculateWithFallback(double marketPrice, double S, double K, 
                                      double T, double r, String optionType) {
        // Try Newton-Raphson first
        double result = calculateNewtonRaphson(marketPrice, S, K, T, r, optionType);
        
        // If it failed, try Brent's method
        if (Double.isNaN(result)) {
            result = calculateBrent(marketPrice, S, K, T, r, optionType);
        }
        
        return result;
    }
    
    /**
     * Calculate vega (∂V/∂σ) for use in Newton-Raphson.
     * Vega is the same for calls and puts.
     * 
     * @param S Current stock price
     * @param K Strike price
     * @param T Time to maturity (years)
     * @param r Risk-free rate
     * @param sigma Volatility
     * @return Vega value
     */
    private double calculateVega(double S, double K, double T, double r, double sigma) {
        double d1 = calculateD1(S, K, T, r, sigma);
        return S * normal.density(d1) * Math.sqrt(T);
    }
    
    /**
     * Calculate d1 parameter for vega calculation.
     */
    private double calculateD1(double S, double K, double T, double r, double sigma) {
        double numerator = Math.log(S / K) + (r + 0.5 * sigma * sigma) * T;
        double denominator = sigma * Math.sqrt(T);
        return numerator / denominator;
    }
    
    /**
     * Test the implied volatility calculator with known values.
     */
    public static void testCalculator() {
        ImpliedVolatilityCalculator calculator = new ImpliedVolatilityCalculator();
        
        System.out.println("Testing Implied Volatility Calculator");
        System.out.println("=====================================");
        
        // Test case 1: ATM call option
        double S = 100, K = 100, T = 1.0, r = 0.05, sigma = 0.20;
        BlackScholes bs = new BlackScholes(S, K, T, r, sigma);
        double callPrice = bs.callPrice();
        
        System.out.printf("\nTest 1: ATM Call%n");
        System.out.printf("  Input: S=%.2f, K=%.2f, T=%.2f, r=%.2f%%, σ=%.2f%%%n", 
                         S, K, T, r*100, sigma*100);
        System.out.printf("  Calculated Price: $%.6f%n", callPrice);
        
        double recoveredSigma = calculator.calculateWithFallback(callPrice, S, K, T, r, "call");
        System.out.printf("  Recovered σ: %.6f%%%n", recoveredSigma * 100);
        System.out.printf("  Error: %.8f%%%n", Math.abs(recoveredSigma - sigma) * 100);
        
        // Test case 2: OTM put option
        S = 100; K = 110; T = 0.5; r = 0.05; sigma = 0.25;
        bs = new BlackScholes(S, K, T, r, sigma);
        double putPrice = bs.putPrice();
        
        System.out.printf("\nTest 2: OTM Put%n");
        System.out.printf("  Input: S=%.2f, K=%.2f, T=%.2f, r=%.2f%%, σ=%.2f%%%n", 
                         S, K, T, r*100, sigma*100);
        System.out.printf("  Calculated Price: $%.6f%n", putPrice);
        
        recoveredSigma = calculator.calculateWithFallback(putPrice, S, K, T, r, "put");
        System.out.printf("  Recovered σ: %.6f%%%n", recoveredSigma * 100);
        System.out.printf("  Error: %.8f%%%n", Math.abs(recoveredSigma - sigma) * 100);
        
        // Test case 3: Deep ITM call
        S = 150; K = 100; T = 30/365.0; r = 0.03; sigma = 0.30;
        bs = new BlackScholes(S, K, T, r, sigma);
        callPrice = bs.callPrice();
        
        System.out.printf("\nTest 3: Deep ITM Call%n");
        System.out.printf("  Input: S=%.2f, K=%.2f, T=%.2f, r=%.2f%%, σ=%.2f%%%n", 
                         S, K, T, r*100, sigma*100);
        System.out.printf("  Calculated Price: $%.6f%n", callPrice);
        
        recoveredSigma = calculator.calculateWithFallback(callPrice, S, K, T, r, "call");
        System.out.printf("  Recovered σ: %.6f%%%n", recoveredSigma * 100);
        System.out.printf("  Error: %.8f%%%n", Math.abs(recoveredSigma - sigma) * 100);
        
        System.out.println("\n=====================================");
    }
}