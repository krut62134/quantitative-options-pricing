import numpy as np
import time

class BinomialTree:
    """
    Binomial tree model for European option pricing.
    
    Discrete-time lattice approach with specified number of time steps.
    """
    
    def __init__(self, S: float, K: float, T: float, r: float, sigma: float, 
                 n_steps: int = 100):
        """
        Initialize binomial tree model.
        
        Parameters:
        S: Current stock price
        K: Strike price
        T: Time to maturity (years)
        r: Risk-free rate
        sigma: Volatility
        n_steps: Number of time steps in the tree
        """
        self.S = S
        self.K = K
        self.T = T
        self.r = r
        self.sigma = sigma
        self.n_steps = n_steps
        
        # Calculate tree parameters
        self.dt = T / n_steps
        self.u = np.exp(sigma * np.sqrt(self.dt))  # Up factor
        self.d = 1 / self.u  # Down factor
        self.p = (np.exp(r * self.dt) - self.d) / (self.u - self.d)  # Risk-neutral probability
        
    def call_price(self) -> float:
        """Calculate European call option price using binomial tree."""
        start_time = time.time()
        
        # Initialize terminal stock prices
        S_T = np.array([self.S * (self.u ** j) * (self.d ** (self.n_steps - j)) 
                        for j in range(self.n_steps + 1)])
        
        # Calculate call payoffs at maturity
        V = np.maximum(S_T - self.K, 0)
        
        # Backward induction through the tree
        discount = np.exp(-self.r * self.dt)
        for i in range(self.n_steps - 1, -1, -1):
            V = discount * (self.p * V[1:] + (1 - self.p) * V[:-1])
        
        elapsed = time.time() - start_time
        print(f"Binomial tree ({self.n_steps} steps) computed in {elapsed:.4f} seconds")
        
        return V[0]
    
    def put_price(self) -> float:
        """Calculate European put option price using binomial tree."""
        start_time = time.time()
        
        # Initialize terminal stock prices
        S_T = np.array([self.S * (self.u ** j) * (self.d ** (self.n_steps - j)) 
                        for j in range(self.n_steps + 1)])
        
        # Calculate put payoffs at maturity
        V = np.maximum(self.K - S_T, 0)
        
        # Backward induction through the tree
        discount = np.exp(-self.r * self.dt)
        for i in range(self.n_steps - 1, -1, -1):
            V = discount * (self.p * V[1:] + (1 - self.p) * V[:-1])
        
        elapsed = time.time() - start_time
        print(f"Binomial tree ({self.n_steps} steps) computed in {elapsed:.4f} seconds")
        
        return V[0]
    
    def summary(self) -> None:
        """Print binomial tree pricing summary."""
        print("=" * 60)
        print("BINOMIAL TREE OPTION PRICING")
        print("=" * 60)
        print(f"\nInput Parameters:")
        print(f"  Stock Price (S):      ${self.S:.2f}")
        print(f"  Strike Price (K):     ${self.K:.2f}")
        print(f"  Time to Maturity (T): {self.T:.4f} years ({self.T*365:.0f} days)")
        print(f"  Risk-free Rate (r):   {self.r*100:.2f}%")
        print(f"  Volatility (σ):       {self.sigma*100:.2f}%")
        print(f"  Number of Steps:      {self.n_steps}")
        
        print(f"\nTree Parameters:")
        print(f"  dt (time step):       {self.dt:.6f}")
        print(f"  u (up factor):        {self.u:.6f}")
        print(f"  d (down factor):      {self.d:.6f}")
        print(f"  p (risk-neutral):     {self.p:.6f}")
        
        call = self.call_price()
        put = self.put_price()
        
        print(f"\nOption Prices:")
        print(f"  Call Option: ${call:.4f}")
        print(f"  Put Option:  ${put:.4f}")
        
        print("=" * 60)


# Test convergence to Black-Scholes with increasing steps
if __name__ == "__main__":
    from black_scholes import BlackScholes
    
    print("\n" + "="*60)
    print("BINOMIAL TREE CONVERGENCE TO BLACK-SCHOLES")
    print("="*60)
    
    # Test parameters
    S, K, T, r, sigma = 100, 100, 1.0, 0.05, 0.20
    
    # Black-Scholes (analytical)
    print("\nBLACK-SCHOLES (Analytical)")
    bs = BlackScholes(S, K, T, r, sigma)
    bs_call = bs.call_price()
    bs_put = bs.put_price()
    print(f"  Call: ${bs_call:.4f}")
    print(f"  Put:  ${bs_put:.4f}")
    
    # Test with different step counts
    step_counts = [10, 50, 100, 500, 1000, 5000]
    
    print("\n" + "-"*60)
    print(f"{'Steps':<10} {'Call Price':<15} {'Call Error':<15} {'Time':<10}")
    print("-"*60)
    
    for n_steps in step_counts:
        bt = BinomialTree(S, K, T, r, sigma, n_steps=n_steps)
        
        start = time.time()
        bt_call = bt.call_price()
        elapsed = time.time() - start
        
        error = abs(bt_call - bs_call)
        error_pct = error / bs_call * 100
        
        print(f"{n_steps:<10} ${bt_call:<14.4f} ${error:.4f} ({error_pct:.2f}%) {elapsed:.4f}s")
    
    print("="*60)
