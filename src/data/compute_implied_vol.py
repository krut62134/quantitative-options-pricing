import pandas as pd
import numpy as np
from scipy.optimize import brentq
from scipy.stats import norm
import sys
from pathlib import Path
sys.path.append(str(Path(__file__).parent.parent))

from models.black_scholes import BlackScholes

def black_scholes_price(S, K, T, r, sigma, option_type='call'):
    """Quick BS price calculation."""
    bs = BlackScholes(S, K, T, r, sigma)
    return bs.call_price() if option_type == 'call' else bs.put_price()

def implied_volatility_newton(market_price, S, K, T, r, option_type='call', 
                               initial_sigma=0.3, max_iter=100, tol=1e-6):
    """
    Calculate implied volatility using Newton-Raphson method.
    More efficient than bisection.
    """
    sigma = initial_sigma
    
    for i in range(max_iter):
        # Calculate price and vega at current sigma
        bs = BlackScholes(S, K, T, r, sigma)
        
        if option_type == 'call':
            price = bs.call_price()
        else:
            price = bs.put_price()
        
        # Vega (sensitivity to volatility)
        vega = S * norm.pdf(bs.d1) * np.sqrt(T)
        
        # Price difference
        diff = price - market_price
        
        # Check convergence
        if abs(diff) < tol:
            return sigma
        
        # Newton update: sigma_new = sigma_old - f(sigma) / f'(sigma)
        if vega < 1e-10:  # Avoid division by zero
            return np.nan
        
        sigma = sigma - diff / vega
        
        # Keep sigma positive and reasonable
        sigma = max(0.01, min(sigma, 5.0))
    
    return np.nan  # Failed to converge

def compute_iv_for_dataset(csv_path: str, r: float = 0.04):
    """Compute implied volatility for all options in dataset."""
    print(f"\nLoading data from: {csv_path}")
    df = pd.read_csv(csv_path)
    
    print(f"Computing implied volatility for {len(df)} options...")
    
    # Compute IV using Newton-Raphson
    iv_list = []
    failed = 0
    
    for idx, row in df.iterrows():
        if idx % 100 == 0:
            print(f"  Progress: {idx}/{len(df)}", end='\r')
        
        try:
            iv = implied_volatility_newton(
                market_price=row['lastPrice'],
                S=row['spot_price'],
                K=row['strike'],
                T=row['T'],
                r=r,
                option_type=row['type']
            )
            iv_list.append(iv)
        except:
            iv_list.append(np.nan)
            failed += 1
    
    print(f"\n  Computed: {len(df) - failed}")
    print(f"  Failed: {failed}")
    
    df['computed_iv'] = iv_list
    
    # Compare computed IV to market IV
    df['iv_diff'] = abs(df['computed_iv'] - df['impliedVolatility'])
    
    # Remove failed computations
    df_clean = df[df['computed_iv'].notna()].copy()
    
    print(f"\nIV Statistics:")
    print(f"  Market IV mean: {df_clean['impliedVolatility'].mean()*100:.2f}%")
    print(f"  Computed IV mean: {df_clean['computed_iv'].mean()*100:.2f}%")
    print(f"  Mean absolute difference: {df_clean['iv_diff'].mean()*100:.2f}%")
    print(f"  Max difference: {df_clean['iv_diff'].max()*100:.2f}%")
    
    return df_clean

def analyze_iv_surface(df: pd.DataFrame):
    """Analyze implied volatility surface patterns."""
    print("\n" + "="*70)
    print("IMPLIED VOLATILITY SURFACE ANALYSIS")
    print("="*70)
    
    # Group by moneyness bins
    df['moneyness_bin'] = pd.cut(df['moneyness'], bins=[0.8, 0.95, 1.05, 1.2])
    
    print("\nIV by Moneyness (for calls):")
    calls = df[df['type'] == 'call']
    iv_by_money = calls.groupby('moneyness_bin')['impliedVolatility'].agg(['mean', 'std', 'count'])
    print(iv_by_money)
    
    print("\nIV by Time to Expiration:")
    df['tte_bin'] = pd.cut(df['days_to_expiration'], bins=[0, 30, 90, 180, 365])
    iv_by_tte = df.groupby('tte_bin')['impliedVolatility'].agg(['mean', 'std', 'count'])
    print(iv_by_tte)
    
    # Volatility smile (ATM vs OTM)
    print("\nVolatility Smile Pattern:")
    print(f"  OTM Put (moneyness < 0.95):  {calls[calls['moneyness'] < 0.95]['impliedVolatility'].mean()*100:.2f}%")
    print(f"  ATM (0.95 < m < 1.05):       {calls[(calls['moneyness'] >= 0.95) & (calls['moneyness'] <= 1.05)]['impliedVolatility'].mean()*100:.2f}%")
    print(f"  OTM Call (moneyness > 1.05): {calls[calls['moneyness'] > 1.05]['impliedVolatility'].mean()*100:.2f}%")

if __name__ == "__main__":
    # Find most recent SPY options file
    import glob
    files = glob.glob('data/SPY_options_*_full_clean.csv')

    if not files:
        print("No options data found. Run fetch_market_data.py first.")
        sys.exit(1)
    
    latest_file = sorted(files)[-1]
    
    # Compute implied volatility
    df = compute_iv_for_dataset(latest_file)
    
    # Analyze IV surface
    analyze_iv_surface(df)
    
    # Save cleaned data
    output_file = latest_file.replace('.csv', '_with_iv.csv')
    df.to_csv(output_file, index=False)
    print(f"\nSaved processed data to: {output_file}")
