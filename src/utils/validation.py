import numpy as np
from typing import Dict, List
import pandas as pd
import sys
from pathlib import Path
sys.path.append(str(Path(__file__).parent.parent))

from models.black_scholes import BlackScholes
from models.monte_carlo import MonteCarlo
from models.binomial_tree import BinomialTree

def validate_put_call_parity(S: float, K: float, T: float, r: float, 
                             call_price: float, put_price: float) -> Dict:
    """
    Validate put-call parity: C - P = S - K*e^(-rT)
    
    For European options, this relationship must hold by arbitrage.
    """
    parity_lhs = call_price - put_price
    parity_rhs = S - K * np.exp(-r * T)
    difference = abs(parity_lhs - parity_rhs)
    
    return {
        'left_side': parity_lhs,
        'right_side': parity_rhs,
        'difference': difference,
        'valid': difference < 0.01  # Tolerance of $0.01
    }

def compare_models(S: float, K: float, T: float, r: float, sigma: float) -> pd.DataFrame:
    """Compare all three pricing models."""
    from models.black_scholes import BlackScholes
    from models.monte_carlo import MonteCarlo
    from models.binomial_tree import BinomialTree
    
    print(f"\nComparing models for: S=${S}, K=${K}, T={T:.2f}y, r={r:.2%}, σ={sigma:.2%}")
    print("-" * 70)
    
    # Black-Scholes (analytical benchmark)
    bs = BlackScholes(S, K, T, r, sigma)
    bs_call = bs.call_price()
    bs_put = bs.put_price()
    
    # Monte Carlo
    mc = MonteCarlo(S, K, T, r, sigma, n_simulations=500000)
    mc.simulate(verbose=False)
    mc_call = mc.call_price()
    mc_put = mc.put_price()
    
    # Binomial Tree
    bt = BinomialTree(S, K, T, r, sigma, n_steps=1000)
    bt_call = bt.call_price()
    bt_put = bt.put_price()
    
    # Create comparison table
    results = {
        'Model': ['Black-Scholes', 'Monte Carlo', 'Binomial Tree'],
        'Call Price': [bs_call, mc_call, bt_call],
        'Put Price': [bs_put, mc_put, bt_put],
        'Call Error': [0, abs(mc_call - bs_call), abs(bt_call - bs_call)],
        'Put Error': [0, abs(mc_put - bs_put), abs(bt_put - bs_put)]
    }
    
    df = pd.DataFrame(results)
    df['Call Error %'] = (df['Call Error'] / bs_call * 100).round(3)
    df['Put Error %'] = (df['Put Error'] / bs_put * 100).round(3)
    
    return df

def test_known_values():
    """Test against known option pricing values."""
    print("="*70)
    print("VALIDATING AGAINST KNOWN VALUES")
    print("="*70)
    
    # Test case 1: ATM option from Hull's textbook
    print("\nTest 1: ATM Option (Hull Example)")
    from models.black_scholes import BlackScholes
    bs1 = BlackScholes(S=42, K=40, T=0.5, r=0.10, sigma=0.20)
    call1 = bs1.call_price()
    expected_call1 = 4.76  # From Hull textbook
    print(f"  Calculated Call: ${call1:.4f}")
    print(f"  Expected Call:   ${expected_call1:.4f}")
    print(f"  Difference:      ${abs(call1 - expected_call1):.4f}")
    print(f"  ✓ PASS" if abs(call1 - expected_call1) < 0.1 else "  ✗ FAIL")
    
    # Test case 2: Put-Call Parity
    print("\nTest 2: Put-Call Parity")
    bs2 = BlackScholes(S=100, K=100, T=1.0, r=0.05, sigma=0.20)
    call2 = bs2.call_price()
    put2 = bs2.put_price()
    parity = validate_put_call_parity(100, 100, 1.0, 0.05, call2, put2)
    print(f"  C - P = {parity['left_side']:.4f}")
    print(f"  S - Ke^(-rT) = {parity['right_side']:.4f}")
    print(f"  Difference: ${parity['difference']:.6f}")
    print(f"  ✓ PASS" if parity['valid'] else "  ✗ FAIL")
    
    # Test case 3: Deep ITM call should equal intrinsic value
    print("\nTest 3: Deep ITM Call (should ≈ intrinsic value)")
    bs3 = BlackScholes(S=150, K=100, T=0.01, r=0.05, sigma=0.20)
    call3 = bs3.call_price()
    intrinsic = 150 - 100
    print(f"  Call Price:      ${call3:.4f}")
    print(f"  Intrinsic Value: ${intrinsic:.4f}")
    print(f"  Time Value:      ${call3 - intrinsic:.4f}")
    print(f"  ✓ PASS" if call3 - intrinsic < 1.0 else "  ✗ FAIL")

if __name__ == "__main__":
    # Test known values
    test_known_values()
    
    # Compare all three models
    print("\n" + "="*70)
    print("MODEL COMPARISON")
    print("="*70)
    
    test_cases = [
        (100, 100, 1.0, 0.05, 0.20),  # ATM
        (100, 110, 1.0, 0.05, 0.25),  # OTM call
        (100, 90, 0.5, 0.03, 0.30),   # ITM call
    ]
    
    for S, K, T, r, sigma in test_cases:
        df = compare_models(S, K, T, r, sigma)
        print(df.to_string(index=False))
        print()
