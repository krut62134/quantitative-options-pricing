package com.options.models;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.MersenneTwister;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ForkJoinPool;

/**
 * Monte Carlo simulation for European option pricing.
 * 
 * Uses geometric Brownian motion to simulate stock price paths
 * and calculates option payoffs at maturity with parallel processing.
 */
public class MonteCarlo {
    
    private final double S;              // Current stock price
    private final double K;              // Strike price
    private final double T;              // Time to maturity (years)
    private final double r;              // Risk-free rate
    private final double sigma;          // Volatility
    private final int nSimulations;      // Number of price paths to simulate
    
    private double[] simulatedPrices;    // Terminal stock prices
    private final NormalDistribution normal;
    private final MersenneTwister random;
    
    /**
     * Initialize Monte Carlo pricing model.
     * 
     * @param S Current stock price
     * @param K Strike price
     * @param T Time to maturity (years)
     * @param r Risk-free rate
     * @param sigma Volatility
     * @param nSimulations Number of price paths to simulate
     */
    public MonteCarlo(double S, double K, double T, double r, double sigma, int nSimulations) {
        this.S = S;
        this.K = K;
        this.T = T;
        this.r = r;
        this.sigma = sigma;
        this.nSimulations = nSimulations;
        this.normal = new NormalDistribution();
        this.random = new MersenneTwister();
    }
    
    /**
     * Run Monte Carlo simulation with parallel processing.
     * 
     * @param verbose Whether to print progress information
     */
    public void simulate(boolean verbose) {
        if (verbose) {
            System.out.printf("%nRunning Monte Carlo with %,d simulations...%n", nSimulations);
            System.out.printf("Using parallel execution with ForkJoinPool%n");
        }
        
        long startTime = System.currentTimeMillis();
        
        // Use ForkJoinPool for parallel processing
        SimulationTask task = new SimulationTask(0, nSimulations, random.nextInt());
        ForkJoinPool.commonPool().invoke(task);
        simulatedPrices = task.getResults();
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        if (verbose) {
            System.out.printf("Simulation completed in %.3f seconds%n", elapsed / 1000.0);
            System.out.printf("Simulations per second: %,.0f%n", nSimulations / (elapsed / 1000.0));
        }
    }
    
    /**
     * Calculate call option price from simulations.
     * 
     * @return Call option price
     */
    public double callPrice() {
        if (simulatedPrices == null) {
            throw new IllegalStateException("Must run simulate() first");
        }
        
        double totalPayoff = 0;
        for (double price : simulatedPrices) {
            // Call payoff: max(S_T - K, 0)
            totalPayoff += Math.max(price - K, 0);
        }
        
        // Discount to present value
        return Math.exp(-r * T) * (totalPayoff / nSimulations);
    }
    
    /**
     * Calculate put option price from simulations.
     * 
     * @return Put option price
     */
    public double putPrice() {
        if (simulatedPrices == null) {
            throw new IllegalStateException("Must run simulate() first");
        }
        
        double totalPayoff = 0;
        for (double price : simulatedPrices) {
            // Put payoff: max(K - S_T, 0)
            totalPayoff += Math.max(K - price, 0);
        }
        
        // Discount to present value
        return Math.exp(-r * T) * (totalPayoff / nSimulations);
    }
    
    /**
     * Calculate confidence interval for option price.
     * 
     * @param optionType 'call' or 'put'
     * @param confidence Confidence level (e.g., 0.95 for 95%)
     * @return Array with [lower_bound, upper_bound]
     */
    public double[] confidenceInterval(String optionType, double confidence) {
        if (simulatedPrices == null) {
            throw new IllegalStateException("Must run simulate() first");
        }
        
        // Calculate payoffs
        double[] payoffs = new double[nSimulations];
        for (int i = 0; i < nSimulations; i++) {
            if ("call".equalsIgnoreCase(optionType)) {
                payoffs[i] = Math.max(simulatedPrices[i] - K, 0);
            } else {
                payoffs[i] = Math.max(K - simulatedPrices[i], 0);
            }
        }
        
        // Discount payoffs
        double[] discountedPayoffs = new double[nSimulations];
        double discountFactor = Math.exp(-r * T);
        for (int i = 0; i < nSimulations; i++) {
            discountedPayoffs[i] = payoffs[i] * discountFactor;
        }
        
        // Calculate statistics
        double mean = 0;
        for (double payoff : discountedPayoffs) {
            mean += payoff;
        }
        mean /= nSimulations;
        
        double variance = 0;
        for (double payoff : discountedPayoffs) {
            variance += Math.pow(payoff - mean, 2);
        }
        variance /= (nSimulations - 1);
        double stdError = Math.sqrt(variance / nSimulations);
        
        // Confidence interval (using normal approximation)
        double zScore = normal.inverseCumulativeProbability((1 + confidence) / 2);
        double margin = zScore * stdError;
        
        return new double[]{mean - margin, mean + margin};
    }
    
    /**
     * Print Monte Carlo pricing summary.
     */
    public void printSummary() {
        if (simulatedPrices == null) {
            System.out.println("No simulation results. Run simulate() first.");
            return;
        }
        
        System.out.println("==========================================================");
        System.out.println("               MONTE CARLO OPTION PRICING             ");
        System.out.println("==========================================================");
        
        System.out.printf("%nInput Parameters:%n");
        System.out.printf("  Stock Price (S):      $%.2f%n", S);
        System.out.printf("  Strike Price (K):     $%.2f%n", K);
        System.out.printf("  Time to Maturity (T): %.4f years (%.0f days)%n", T, T * 365);
        System.out.printf("  Risk-free Rate (r):   %.2f%%%n", r * 100);
        System.out.printf("  Volatility (σ):       %.2f%%%n", sigma * 100);
        System.out.printf("  Number of Sims:       %,d%n", nSimulations);
        
        double call = callPrice();
        double put = putPrice();
        
        double[] callCI = confidenceInterval("call", 0.95);
        double[] putCI = confidenceInterval("put", 0.95);
        
        // Calculate terminal price statistics
        double meanTerminal = 0;
        double minTerminal = Double.MAX_VALUE;
        double maxTerminal = Double.MIN_VALUE;
        double variance = 0;
        
        for (double price : simulatedPrices) {
            meanTerminal += price;
            if (price < minTerminal) minTerminal = price;
            if (price > maxTerminal) maxTerminal = price;
        }
        meanTerminal /= nSimulations;
        
        for (double price : simulatedPrices) {
            variance += Math.pow(price - meanTerminal, 2);
        }
        variance /= (nSimulations - 1);
        double stdTerminal = Math.sqrt(variance);
        
        System.out.printf("%nSimulation Statistics:%n");
        System.out.printf("  Mean Terminal Price:  $%.2f%n", meanTerminal);
        System.out.printf("  Std Terminal Price:   $%.2f%n", stdTerminal);
        System.out.printf("  Min Terminal Price:   $%.2f%n", minTerminal);
        System.out.printf("  Max Terminal Price:   $%.2f%n", maxTerminal);
        
        System.out.printf("%nOption Prices:%n");
        System.out.printf("  Call Option: $%.4f%n", call);
        System.out.printf("    95%% CI: ($%.4f, $%.4f)%n", callCI[0], callCI[1]);
        System.out.printf("  Put Option:  $%.4f%n", put);
        System.out.printf("    95%% CI: ($%.4f, $%.4f)%n", putCI[0], putCI[1]);
        
        System.out.println("==========================================================");
    }
    
    // Getters
    public double getS() { return S; }
    public double getK() { return K; }
    public double getT() { return T; }
    public double getR() { return r; }
    public double getSigma() { return sigma; }
    public int getNSimulations() { return nSimulations; }
    public double[] getSimulatedPrices() { return simulatedPrices; }
    
    /**
     * Recursive task for parallel Monte Carlo simulation.
     */
    private class SimulationTask extends RecursiveTask<double[]> {
        private static final int THRESHOLD = 10000; // Minimum size for parallel processing
        private final int start;
        private final int end;
        private final int seed;
        
        SimulationTask(int start, int end, int seed) {
            this.start = start;
            this.end = end;
            this.seed = seed;
        }
        
        @Override
        protected double[] compute() {
            int length = end - start;
            
            if (length <= THRESHOLD) {
                // Compute directly
                MersenneTwister localRandom = new MersenneTwister(seed);
                double[] results = new double[length];
                
                double drift = (r - 0.5 * sigma * sigma) * T;
                double diffusion = sigma * Math.sqrt(T);
                
                for (int i = 0; i < length; i++) {
                    double Z = localRandom.nextGaussian();
                    double price = S * Math.exp(drift + diffusion * Z);
                    results[i] = price;
                }
                
                return results;
            } else {
                // Split and compute in parallel
                int mid = start + length / 2;
                
                SimulationTask leftTask = new SimulationTask(start, mid, seed);
                SimulationTask rightTask = new SimulationTask(mid, end, seed + 1);
                
                leftTask.fork();
                double[] rightResult = rightTask.compute();
                double[] leftResult = leftTask.join();
                
                // Combine results
                double[] combined = new double[length];
                System.arraycopy(leftResult, 0, combined, 0, leftResult.length);
                System.arraycopy(rightResult, 0, combined, leftResult.length, rightResult.length);
                
                return combined;
            }
        }
        
        public double[] getResults() {
            return compute();
        }
    }
}