import numpy as np
from scipy.stats import norm
from typing import Dict, Tuple

class BlackScholes:
    """
    Black-Scholes option pricing model for European options.
    
    Assumptions:
    - European exercise (only at maturity)
    - No dividends
    - Constant risk-free rate and volatility
    - Log-normal price distribution
    """
    
    def __init__(self, S: float, K: float, T: float, r: float, sigma: float):
        """
        Initialize Black-Scholes model.
        
        Parameters:
        S: Current stock price
        K: Strike price
        T: Time to maturity (years)
        r: Risk-free rate (decimal, e.g., 0.05 for 5%)
        sigma: Volatility (decimal, e.g., 0.20 for 20%)
        """
        self.S = S
        self.K = K
        self.T = T
        self.r = r
        self.sigma = sigma
        
        # Calculate d1 and d2 once
        self.d1 = self._calculate_d1()
        self.d2 = self._calculate_d2()
        
    def _calculate_d1(self) -> float:
        """Calculate d1 parameter."""
        numerator = np.log(self.S / self.K) + (self.r + 0.5 * self.sigma**2) * self.T
        denominator = self.sigma * np.sqrt(self.T)
        return numerator / denominator
    
    def _calculate_d2(self) -> float:
        """Calculate d2 parameter."""
        return self.d1 - self.sigma * np.sqrt(self.T)
    
    def call_price(self) -> float:
        """Calculate European call option price."""
        return (self.S * norm.cdf(self.d1) - 
                self.K * np.exp(-self.r * self.T) * norm.cdf(self.d2))
    
    def put_price(self) -> float:
        """Calculate European put option price."""
        return (self.K * np.exp(-self.r * self.T) * norm.cdf(-self.d2) - 
                self.S * norm.cdf(-self.d1))
    
    def greeks(self) -> Dict[str, Tuple[float, float]]:
        """
        Calculate option Greeks.
        
        Returns:
        Dictionary with Greeks for (call, put):
        - delta: Rate of change of option price with stock price
        - gamma: Rate of change of delta with stock price
        - vega: Sensitivity to volatility
        - theta: Time decay
        - rho: Sensitivity to interest rate
        """
        # Delta
        delta_call = norm.cdf(self.d1)
        delta_put = delta_call - 1
        
        # Gamma (same for call and put)
        gamma = norm.pdf(self.d1) / (self.S * self.sigma * np.sqrt(self.T))
        
        # Vega (same for call and put, in dollars per 1% change in vol)
        vega = self.S * norm.pdf(self.d1) * np.sqrt(self.T) / 100
        
        # Theta (per day)
        theta_call = (-(self.S * norm.pdf(self.d1) * self.sigma) / (2 * np.sqrt(self.T)) -
                     self.r * self.K * np.exp(-self.r * self.T) * norm.cdf(self.d2)) / 365
        theta_put = (-(self.S * norm.pdf(self.d1) * self.sigma) / (2 * np.sqrt(self.T)) +
                    self.r * self.K * np.exp(-self.r * self.T) * norm.cdf(-self.d2)) / 365
        
        # Rho (per 1% change in rate)
        rho_call = self.K * self.T * np.exp(-self.r * self.T) * norm.cdf(self.d2) / 100
        rho_put = -self.K * self.T * np.exp(-self.r * self.T) * norm.cdf(-self.d2) / 100
        
        return {
            'delta': (delta_call, delta_put),
            'gamma': (gamma, gamma),
            'vega': (vega, vega),
            'theta': (theta_call, theta_put),
            'rho': (rho_call, rho_put)
        }
    
    def summary(self) -> None:
        """Print complete option pricing summary."""
        print("=" * 60)
        print("BLACK-SCHOLES OPTION PRICING")
        print("=" * 60)
        print(f"\nInput Parameters:")
        print(f"  Stock Price (S):      ${self.S:.2f}")
        print(f"  Strike Price (K):     ${self.K:.2f}")
        print(f"  Time to Maturity (T): {self.T:.4f} years ({self.T*365:.0f} days)")
        print(f"  Risk-free Rate (r):   {self.r*100:.2f}%")
        print(f"  Volatility (σ):       {self.sigma*100:.2f}%")
        
        print(f"\nIntermediate Values:")
        print(f"  d1: {self.d1:.6f}")
        print(f"  d2: {self.d2:.6f}")
        print(f"  N(d1): {norm.cdf(self.d1):.6f}")
        print(f"  N(d2): {norm.cdf(self.d2):.6f}")
        
        call = self.call_price()
        put = self.put_price()
        
        print(f"\nOption Prices:")
        print(f"  Call Option: ${call:.4f}")
        print(f"  Put Option:  ${put:.4f}")
        
        greeks = self.greeks()
        print(f"\nGreeks:")
        print(f"  {'Greek':<10} {'Call':<12} {'Put':<12}")
        print(f"  {'-'*10} {'-'*12} {'-'*12}")
        for greek_name, (call_val, put_val) in greeks.items():
            print(f"  {greek_name.capitalize():<10} {call_val:>11.6f}  {put_val:>11.6f}")
        
        print("=" * 60)


# Test with known values
if __name__ == "__main__":
    # Example: ATM option, 1 year maturity, 5% rate, 20% vol
    print("\nTest Case 1: At-The-Money Option")
    bs1 = BlackScholes(S=100, K=100, T=1.0, r=0.05, sigma=0.20)
    bs1.summary()
    
    print("\n" * 2)
    print("Test Case 2: Out-of-The-Money Call (ITM Put)")
    bs2 = BlackScholes(S=100, K=110, T=0.5, r=0.05, sigma=0.25)
    bs2.summary()
    
    print("\n" * 2)
    print("Test Case 3: Short-dated Option")
    bs3 = BlackScholes(S=150, K=145, T=30/365, r=0.03, sigma=0.30)
    bs3.summary()
