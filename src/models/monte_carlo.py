import numpy as np
from typing import Tuple
from joblib import Parallel, delayed
import time

class MonteCarlo:
    """
    Monte Carlo simulation for European option pricing.
    
    Uses geometric Brownian motion to simulate stock price paths
    and calculates option payoffs at maturity.
    """
    
    def __init__(self, S: float, K: float, T: float, r: float, sigma: float, 
                 n_simulations: int = 100000, n_jobs: int = -1):
        """
        Initialize Monte Carlo pricing model.
        
        Parameters:
        S: Current stock price
        K: Strike price
        T: Time to maturity (years)
        r: Risk-free rate
        sigma: Volatility
        n_simulations: Number of price paths to simulate
        n_jobs: Number of parallel jobs (-1 = all CPUs)
        """
        self.S = S
        self.K = K
        self.T = T
        self.r = r
        self.sigma = sigma
        self.n_simulations = n_simulations
        self.n_jobs = n_jobs
        self.simulated_prices = None
        
    def _simulate_batch(self, batch_size: int, seed: int) -> np.ndarray:
        """Simulate a batch of terminal stock prices."""
        np.random.seed(seed)
        
        # Generate random standard normal variables
        Z = np.random.standard_normal(batch_size)
        
        # Calculate terminal stock price using GBM formula
        # S_T = S_0 * exp((r - 0.5*sigma^2)*T + sigma*sqrt(T)*Z)
        drift = (self.r - 0.5 * self.sigma**2) * self.T
        diffusion = self.sigma * np.sqrt(self.T) * Z
        
        S_T = self.S * np.exp(drift + diffusion)
        return S_T
    
    def simulate(self, verbose: bool = True) -> None:
        """Run Monte Carlo simulation in parallel."""
        if verbose:
            print(f"\nRunning Monte Carlo with {self.n_simulations:,} simulations...")
            print(f"Using parallel execution with n_jobs={self.n_jobs}")
        
        start_time = time.time()
        
        # Determine batch size for parallel execution
        n_cores = self.n_jobs if self.n_jobs > 0 else -1
        
        if n_cores == -1:
            import os
            n_cores = os.cpu_count()
        
        batch_size = self.n_simulations // n_cores
        
        # Run simulations in parallel
        results = Parallel(n_jobs=self.n_jobs)(
            delayed(self._simulate_batch)(batch_size, seed=i) 
            for i in range(n_cores)
        )
        
        # Combine results
        self.simulated_prices = np.concatenate(results)
        
        # Handle remainder if n_simulations doesn't divide evenly
        remainder = self.n_simulations - len(self.simulated_prices)
        if remainder > 0:
            extra_prices = self._simulate_batch(remainder, seed=n_cores)
            self.simulated_prices = np.concatenate([self.simulated_prices, extra_prices])
        
        elapsed = time.time() - start_time
        
        if verbose:
            print(f"Simulation completed in {elapsed:.3f} seconds")
            print(f"Simulations per second: {self.n_simulations/elapsed:,.0f}")
    
    def call_price(self) -> float:
        """Calculate call option price from simulations."""
        if self.simulated_prices is None:
            raise ValueError("Must run simulate() first")
        
        # Call payoff: max(S_T - K, 0)
        payoffs = np.maximum(self.simulated_prices - self.K, 0)
        
        # Discount to present value
        return np.exp(-self.r * self.T) * np.mean(payoffs)
    
    def put_price(self) -> float:
        """Calculate put option price from simulations."""
        if self.simulated_prices is None:
            raise ValueError("Must run simulate() first")
        
        # Put payoff: max(K - S_T, 0)
        payoffs = np.maximum(self.K - self.simulated_prices, 0)
        
        # Discount to present value
        return np.exp(-self.r * self.T) * np.mean(payoffs)
    
    def confidence_interval(self, option_type: str = 'call', 
                           confidence: float = 0.95) -> Tuple[float, float]:
        """
        Calculate confidence interval for option price.
        
        Parameters:
        option_type: 'call' or 'put'
        confidence: Confidence level (e.g., 0.95 for 95%)
        """
        if self.simulated_prices is None:
            raise ValueError("Must run simulate() first")
        
        # Calculate payoffs
        if option_type.lower() == 'call':
            payoffs = np.maximum(self.simulated_prices - self.K, 0)
        else:
            payoffs = np.maximum(self.K - self.simulated_prices, 0)
        
        # Discount payoffs
        discounted_payoffs = np.exp(-self.r * self.T) * payoffs
        
        # Calculate standard error
        mean_price = np.mean(discounted_payoffs)
        std_error = np.std(discounted_payoffs) / np.sqrt(self.n_simulations)
        
        # Confidence interval (using normal approximation)
        from scipy.stats import norm
        z_score = norm.ppf((1 + confidence) / 2)
        margin = z_score * std_error
        
        return (mean_price - margin, mean_price + margin)
    
    def summary(self) -> None:
        """Print Monte Carlo pricing summary."""
        if self.simulated_prices is None:
            print("No simulation results. Run simulate() first.")
            return
        
        print("=" * 60)
        print("MONTE CARLO OPTION PRICING")
        print("=" * 60)
        print(f"\nInput Parameters:")
        print(f"  Stock Price (S):      ${self.S:.2f}")
        print(f"  Strike Price (K):     ${self.K:.2f}")
        print(f"  Time to Maturity (T): {self.T:.4f} years ({self.T*365:.0f} days)")
        print(f"  Risk-free Rate (r):   {self.r*100:.2f}%")
        print(f"  Volatility (σ):       {self.sigma*100:.2f}%")
        print(f"  Number of Sims:       {self.n_simulations:,}")
        
        call = self.call_price()
        put = self.put_price()
        
        call_ci = self.confidence_interval('call')
        put_ci = self.confidence_interval('put')
        
        print(f"\nSimulation Statistics:")
        print(f"  Mean Terminal Price:  ${np.mean(self.simulated_prices):.2f}")
        print(f"  Std Terminal Price:   ${np.std(self.simulated_prices):.2f}")
        print(f"  Min Terminal Price:   ${np.min(self.simulated_prices):.2f}")
        print(f"  Max Terminal Price:   ${np.max(self.simulated_prices):.2f}")
        
        print(f"\nOption Prices:")
        print(f"  Call Option: ${call:.4f}")
        print(f"    95% CI: (${call_ci[0]:.4f}, ${call_ci[1]:.4f})")
        print(f"  Put Option:  ${put:.4f}")
        print(f"    95% CI: (${put_ci[0]:.4f}, ${put_ci[1]:.4f})")
        
        print("=" * 60)


# Test and compare with Black-Scholes
if __name__ == "__main__":
    from black_scholes import BlackScholes
    
    print("\n" + "="*60)
    print("MONTE CARLO vs BLACK-SCHOLES COMPARISON")
    print("="*60)
    
    # Test parameters
    S, K, T, r, sigma = 100, 100, 1.0, 0.05, 0.20
    
    # Black-Scholes (analytical)
    print("\n1. BLACK-SCHOLES (Analytical Solution)")
    bs = BlackScholes(S, K, T, r, sigma)
    bs_call = bs.call_price()
    bs_put = bs.put_price()
    print(f"   Call: ${bs_call:.4f}")
    print(f"   Put:  ${bs_put:.4f}")
    
    # Monte Carlo with different simulation counts
    sim_counts = [10000, 100000, 1000000]
    
    for n_sims in sim_counts:
        print(f"\n2. MONTE CARLO ({n_sims:,} simulations)")
        mc = MonteCarlo(S, K, T, r, sigma, n_simulations=n_sims)
        mc.simulate(verbose=True)
        
        mc_call = mc.call_price()
        mc_put = mc.put_price()
        
        print(f"   Call: ${mc_call:.4f} (Error: ${abs(mc_call - bs_call):.4f} or {abs(mc_call - bs_call)/bs_call*100:.2f}%)")
        print(f"   Put:  ${mc_put:.4f} (Error: ${abs(mc_put - bs_put):.4f} or {abs(mc_put - bs_put)/bs_put*100:.2f}%)")
        
        call_ci = mc.confidence_interval('call')
        print(f"   Call 95% CI: (${call_ci[0]:.4f}, ${call_ci[1]:.4f})")
    
    print("\n" + "="*60)
