package com.options.models;

/**
 * Binomial tree model for European option pricing.
 * 
 * Discrete-time lattice approach with specified number of time steps.
 * Uses backward induction from expiration to present.
 */
public class BinomialTree {
    
    private final double S;          // Current stock price
    private final double K;          // Strike price
    private final double T;          // Time to maturity (years)
    private final double r;          // Risk-free rate
    private final double sigma;      // Volatility
    private final int nSteps;       // Number of time steps in the tree
    
    // Calculated tree parameters
    private final double dt;         // Time step
    private final double u;          // Up factor
    private final double d;          // Down factor
    private final double p;          // Risk-neutral probability
    
    /**
     * Initialize binomial tree model.
     * 
     * @param S Current stock price
     * @param K Strike price
     * @param T Time to maturity (years)
     * @param r Risk-free rate
     * @param sigma Volatility
     * @param nSteps Number of time steps in the tree
     */
    public BinomialTree(double S, double K, double T, double r, double sigma, int nSteps) {
        this.S = S;
        this.K = K;
        this.T = T;
        this.r = r;
        this.sigma = sigma;
        this.nSteps = nSteps;
        
        // Calculate tree parameters
        this.dt = T / nSteps;
        this.u = Math.exp(sigma * Math.sqrt(dt));    // Up factor
        this.d = 1.0 / u;                         // Down factor
        this.p = (Math.exp(r * dt) - d) / (u - d); // Risk-neutral probability
    }
    
    /**
     * Calculate European call option price using binomial tree.
     * 
     * @return Call option price
     */
    public double callPrice() {
        long startTime = System.currentTimeMillis();
        
        // Initialize terminal stock prices
        double[] stockPrices = new double[nSteps + 1];
        for (int j = 0; j <= nSteps; j++) {
            stockPrices[j] = S * Math.pow(u, j) * Math.pow(d, nSteps - j);
        }
        
        // Calculate call payoffs at maturity
        double[] optionValues = new double[nSteps + 1];
        for (int j = 0; j <= nSteps; j++) {
            optionValues[j] = Math.max(stockPrices[j] - K, 0);
        }
        
        // Backward induction through the tree
        double discount = Math.exp(-r * dt);
        for (int i = nSteps - 1; i >= 0; i--) {
            for (int j = 0; j <= i; j++) {
                optionValues[j] = discount * (p * optionValues[j + 1] + (1 - p) * optionValues[j]);
            }
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("Binomial tree (%d steps) computed in %.4f seconds%n", nSteps, elapsed / 1000.0);
        
        return optionValues[0];
    }
    
    /**
     * Calculate European put option price using binomial tree.
     * 
     * @return Put option price
     */
    public double putPrice() {
        long startTime = System.currentTimeMillis();
        
        // Initialize terminal stock prices
        double[] stockPrices = new double[nSteps + 1];
        for (int j = 0; j <= nSteps; j++) {
            stockPrices[j] = S * Math.pow(u, j) * Math.pow(d, nSteps - j);
        }
        
        // Calculate put payoffs at maturity
        double[] optionValues = new double[nSteps + 1];
        for (int j = 0; j <= nSteps; j++) {
            optionValues[j] = Math.max(K - stockPrices[j], 0);
        }
        
        // Backward induction through the tree
        double discount = Math.exp(-r * dt);
        for (int i = nSteps - 1; i >= 0; i--) {
            for (int j = 0; j <= i; j++) {
                optionValues[j] = discount * (p * optionValues[j + 1] + (1 - p) * optionValues[j]);
            }
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("Binomial tree (%d steps) computed in %.4f seconds%n", nSteps, elapsed / 1000.0);
        
        return optionValues[0];
    }
    
    /**
     * Calculate delta using the binomial tree approach.
     * 
     * @return Delta value
     */
    public double calculateDelta(boolean isCall) {
        // Build two trees: one for stock price S+epsilon, one for S-epsilon
        double epsilon = S * 0.0001; // Small change in stock price
        
        BinomialTree treeUp = new BinomialTree(S + epsilon, K, T, r, sigma, nSteps);
        BinomialTree treeDown = new BinomialTree(S - epsilon, K, T, r, sigma, nSteps);
        
        double priceUp = isCall ? treeUp.callPrice() : treeUp.putPrice();
        double priceDown = isCall ? treeDown.callPrice() : treeDown.putPrice();
        
        return (priceUp - priceDown) / (2 * epsilon);
    }
    
    /**
     * Calculate gamma using the binomial tree approach.
     * 
     * @return Gamma value
     */
    public double calculateGamma(boolean isCall) {
        // Build three trees: one for S+epsilon, S, and S-epsilon
        double epsilon = S * 0.0001; // Small change in stock price
        
        BinomialTree treeUp = new BinomialTree(S + epsilon, K, T, r, sigma, nSteps);
        BinomialTree treeCurrent = new BinomialTree(S, K, T, r, sigma, nSteps);
        BinomialTree treeDown = new BinomialTree(S - epsilon, K, T, r, sigma, nSteps);
        
        double priceUp = isCall ? treeUp.callPrice() : treeUp.putPrice();
        double priceCurrent = isCall ? treeCurrent.callPrice() : treeCurrent.putPrice();
        double priceDown = isCall ? treeDown.callPrice() : treeDown.putPrice();
        
        return (priceUp - 2 * priceCurrent + priceDown) / (epsilon * epsilon);
    }
    
    /**
     * Calculate theta using the binomial tree approach.
     * 
     * @return Theta value (per day)
     */
    public double calculateTheta(boolean isCall) {
        // Build two trees: one for T, one for T-dt
        double dtSmall = T / 365.0; // One day
        BinomialTree treeCurrent = new BinomialTree(S, K, T, r, sigma, nSteps);
        BinomialTree treeOneDayLess = new BinomialTree(S, K, T - dtSmall, r, sigma, Math.max(nSteps - 1, 10));
        
        double priceCurrent = isCall ? treeCurrent.callPrice() : treeCurrent.putPrice();
        double priceOneDayLess = isCall ? treeOneDayLess.callPrice() : treeOneDayLess.putPrice();
        
        return (priceOneDayLess - priceCurrent) / dtSmall; // Per day
    }
    
    /**
     * Print binomial tree pricing summary.
     */
    public void printSummary() {
        System.out.println("==========================================================");
        System.out.println("              BINOMIAL TREE OPTION PRICING            ");
        System.out.println("==========================================================");
        
        System.out.printf("%nInput Parameters:%n");
        System.out.printf("  Stock Price (S):      $%.2f%n", S);
        System.out.printf("  Strike Price (K):     $%.2f%n", K);
        System.out.printf("  Time to Maturity (T): %.4f years (%.0f days)%n", T, T * 365);
        System.out.printf("  Risk-free Rate (r):   %.2f%%%n", r * 100);
        System.out.printf("  Volatility (σ):       %.2f%%%n", sigma * 100);
        System.out.printf("  Number of Steps:      %d%n", nSteps);
        
        System.out.printf("%nTree Parameters:%n");
        System.out.printf("  dt (time step):       %.6f%n", dt);
        System.out.printf("  u (up factor):        %.6f%n", u);
        System.out.printf("  d (down factor):      %.6f%n", d);
        System.out.printf("  p (risk-neutral):     %.6f%n", p);
        
        double call = callPrice();
        double put = putPrice();
        
        System.out.printf("%nOption Prices:%n");
        System.out.printf("  Call Option: $%.4f%n", call);
        System.out.printf("  Put Option:  $%.4f%n", put);
        
        System.out.printf("%nGreek Approximations:%n");
        System.out.printf("  Call Delta: %.6f%n", calculateDelta(true));
        System.out.printf("  Put Delta:  %.6f%n", calculateDelta(false));
        System.out.printf("  Gamma:      %.6f%n", calculateGamma(true));
        System.out.printf("  Call Theta: %.6f per day%n", calculateTheta(true));
        System.out.printf("  Put Theta:  %.6f per day%n", calculateTheta(false));
        
        System.out.println("==========================================================");
    }
    
    // Getters for tree parameters
    public double getS() { return S; }
    public double getK() { return K; }
    public double getT() { return T; }
    public double getR() { return r; }
    public double getSigma() { return sigma; }
    public int getNSteps() { return nSteps; }
    public double getDt() { return dt; }
    public double getU() { return u; }
    public double getD() { return d; }
    public double getP() { return p; }
}