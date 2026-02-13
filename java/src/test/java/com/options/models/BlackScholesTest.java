package com.options.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withPrecision;

/**
 * Unit tests for BlackScholes model.
 * 
 * Validates mathematical accuracy against known textbook values and
 * ensures put-call parity holds.
 */
class BlackScholesTest {
    
    private static final double TOLERANCE = 1e-6;
    private static final double PRICE_TOLERANCE = 1e-3;
    
    @Test
    @DisplayName("Hull textbook example - ATM call option")
    void testHullTextbookExample() {
        // From Hull's Options, Futures & Other Derivatives (8th ed.)
        // Example: S=42, K=40, T=0.5, r=0.10, σ=0.20
        BlackScholes bs = new BlackScholes(42, 40, 0.5, 0.10, 0.20);
        
        double callPrice = bs.callPrice();
        double expectedCallPrice = 4.76; // From textbook
        
        assertThat(callPrice)
            .as("Call price should match textbook value")
            .isCloseTo(expectedCallPrice, withPrecision(PRICE_TOLERANCE));
    }
    
    @Test
    @DisplayName("Put-call parity should hold for ATM options")
    void testPutCallParityATM() {
        double S = 100, K = 100, T = 1.0, r = 0.05, sigma = 0.20;
        BlackScholes bs = new BlackScholes(S, K, T, r, sigma);
        
        double callPrice = bs.callPrice();
        double putPrice = bs.putPrice();
        
        // Put-call parity: C - P = S - K*e^(-rT)
        double lhs = callPrice - putPrice;
        double rhs = S - K * Math.exp(-r * T);
        
        assertThat(lhs)
            .as("Put-call parity left side")
            .isCloseTo(rhs, withPrecision(TOLERANCE));
    }
    
    @Test
    @DisplayName("Put-call parity should hold for ITM options")
    void testPutCallParityITM() {
        double S = 110, K = 100, T = 0.5, r = 0.03, sigma = 0.25;
        BlackScholes bs = new BlackScholes(S, K, T, r, sigma);
        
        double callPrice = bs.callPrice();
        double putPrice = bs.putPrice();
        
        double lhs = callPrice - putPrice;
        double rhs = S - K * Math.exp(-r * T);
        
        assertThat(lhs)
            .as("Put-call parity for ITM option")
            .isCloseTo(rhs, withPrecision(TOLERANCE));
    }
    
    @Test
    @DisplayName("Deep ITM call should approach intrinsic value")
    void testDeepITMCall() {
        double S = 150, K = 100, T = 0.01, r = 0.05, sigma = 0.20;
        BlackScholes bs = new BlackScholes(S, K, T, r, sigma);
        
        double callPrice = bs.callPrice();
        double intrinsicValue = S - K;
        double timeValue = callPrice - intrinsicValue;
        
        // Time value should be very small for deep ITM, short-dated option
        assertThat(timeValue)
            .as("Time value of deep ITM call")
            .isLessThan(1.0);
    }
    
    @Test
    @DisplayName("Deep ITM put should approach intrinsic value")
    void testDeepITMPut() {
        double S = 80, K = 100, T = 0.01, r = 0.05, sigma = 0.20;
        BlackScholes bs = new BlackScholes(S, K, T, r, sigma);
        
        double putPrice = bs.putPrice();
        double intrinsicValue = K - S;
        double timeValue = putPrice - intrinsicValue;
        
        assertThat(timeValue)
            .as("Time value of deep ITM put")
            .isLessThan(1.0);
    }
    
    @Test
    @DisplayName("Greeks should have correct signs and magnitudes")
    void testGreeksProperties() {
        double S = 100, K = 100, T = 1.0, r = 0.05, sigma = 0.20;
        BlackScholes bs = new BlackScholes(S, K, T, r, sigma);
        
        BlackScholes.Greeks greeks = bs.calculateGreeks();
        
        // Call delta should be between 0 and 1 for ATM
        assertThat(greeks.deltaCall)
            .as("Call delta should be between 0 and 1")
            .isBetween(0.0, 1.0);
        
        // Put delta should be between -1 and 0 for ATM
        assertThat(greeks.deltaPut)
            .as("Put delta should be between -1 and 0")
            .isBetween(-1.0, 0.0);
        
        // Delta put-call relationship: delta_put = delta_call - 1
        assertThat(greeks.deltaPut)
            .as("Put delta should equal call delta minus 1")
            .isCloseTo(greeks.deltaCall - 1, withPrecision(TOLERANCE));
        
        // Gamma should be positive
        assertThat(greeks.gamma)
            .as("Gamma should be positive")
            .isGreaterThan(0);
        
        // Gamma should be same for calls and puts
        assertThat(greeks.gamma)
            .as("Call and put gamma should be equal")
            .isEqualByComparingTo(greeks.gamma);
        
        // Vega should be positive
        assertThat(greeks.vega)
            .as("Vega should be positive")
            .isGreaterThan(0);
        
        // Theta should be negative for long positions (time decay)
        assertThat(greeks.thetaCall)
            .as("Call theta should be negative")
            .isLessThan(0);
        assertThat(greeks.thetaPut)
            .as("Put theta should be negative")
            .isLessThan(0);
    }
    
    @Test
    @DisplayName("Option prices should be monotonic with volatility")
    void testVolatilityMonotonicity() {
        double S = 100, K = 100, T = 1.0, r = 0.05;
        double[] volatilities = {0.01, 0.10, 0.20, 0.30, 0.50};
        
        double[] callPrices = new double[volatilities.length];
        double[] putPrices = new double[volatilities.length];
        
        for (int i = 0; i < volatilities.length; i++) {
            BlackScholes bs = new BlackScholes(S, K, T, r, volatilities[i]);
            callPrices[i] = bs.callPrice();
            putPrices[i] = bs.putPrice();
        }
        
        // Check that prices increase with volatility
        for (int i = 1; i < volatilities.length; i++) {
            assertThat(callPrices[i])
                .as("Call price should increase with volatility")
                .isGreaterThan(callPrices[i - 1]);
            assertThat(putPrices[i])
                .as("Put price should increase with volatility")
                .isGreaterThan(putPrices[i - 1]);
        }
    }
    
    @Test
    @DisplayName("Option prices should be monotonic with time to expiration")
    void testTimeMonotonicity() {
        double S = 100, K = 100, r = 0.05, sigma = 0.20;
        double[] times = {0.1, 0.25, 0.5, 1.0, 2.0};
        
        double[] callPrices = new double[times.length];
        double[] putPrices = new double[times.length];
        
        for (int i = 0; i < times.length; i++) {
            BlackScholes bs = new BlackScholes(S, K, times[i], r, sigma);
            callPrices[i] = bs.callPrice();
            putPrices[i] = bs.putPrice();
        }
        
        // Check that prices increase with time to expiration
        for (int i = 1; i < times.length; i++) {
            assertThat(callPrices[i])
                .as("Call price should increase with time")
                .isGreaterThan(callPrices[i - 1]);
            assertThat(putPrices[i])
                .as("Put price should increase with time")
                .isGreaterThan(putPrices[i - 1]);
        }
    }
    
    @Test
    @DisplayName("Extreme inputs should not cause crashes")
    void testExtremeInputs() {
        // Very low volatility
        BlackScholes bs1 = new BlackScholes(100, 100, 1.0, 0.05, 0.001);
        assertThat(bs1.callPrice()).isGreaterThanOrEqualTo(0);
        assertThat(bs1.putPrice()).isGreaterThanOrEqualTo(0);
        
        // Very high volatility
        BlackScholes bs2 = new BlackScholes(100, 100, 1.0, 0.05, 2.0);
        assertThat(bs2.callPrice()).isGreaterThan(0);
        assertThat(bs2.putPrice()).isGreaterThan(0);
        
        // Very short time
        BlackScholes bs3 = new BlackScholes(100, 100, 0.001, 0.05, 0.20);
        assertThat(bs3.callPrice()).isGreaterThanOrEqualTo(0);
        assertThat(bs3.putPrice()).isGreaterThanOrEqualTo(0);
        
        // Very long time
        BlackScholes bs4 = new BlackScholes(100, 100, 10.0, 0.05, 0.20);
        assertThat(bs4.callPrice()).isGreaterThan(0);
        assertThat(bs4.putPrice()).isGreaterThan(0);
    }
}