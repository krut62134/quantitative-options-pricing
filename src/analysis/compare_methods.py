import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import sys
from pathlib import Path
sys.path.append(str(Path(__file__).parent.parent))

from models.black_scholes import BlackScholes

def load_test_data():
    """Load test set."""
    df = pd.read_csv('data/SPY_options_20251003_full_clean_with_iv.csv')
    from sklearn.model_selection import train_test_split
    X_temp, X_test_full = train_test_split(df, test_size=0.15, random_state=42)
    return X_test_full

def compare_all_methods(df, r=0.04):
    """Compute predictions for all methods."""
    results = []
    
    print(f"Computing predictions for {len(df)} test options...")
    
    for idx, row in df.iterrows():
        if idx % 50 == 0:
            print(f"  Progress: {idx}/{len(df)}", end='\r')
        
        S = row['spot_price']
        K = row['strike']
        T = row['T']
        option_type = row['type']
        
        market_iv = row['impliedVolatility']
        computed_iv = row['computed_iv']
        
        bs_market = BlackScholes(S, K, T, r, market_iv)
        bs_price_market = bs_market.call_price() if option_type == 'call' else bs_market.put_price()
        
        if not np.isnan(computed_iv):
            bs_computed = BlackScholes(S, K, T, r, computed_iv)
            bs_price_computed = bs_computed.call_price() if option_type == 'call' else bs_computed.put_price()
        else:
            bs_price_computed = np.nan
        
        results.append({
            'strike': K,
            'moneyness': row['moneyness'],
            'T': T,
            'type': option_type,
            'market_price': row['lastPrice'],
            'market_iv': market_iv,
            'computed_iv': computed_iv,
            'bs_price_market': bs_price_market,
            'bs_price_computed': bs_price_computed
        })
    
    print("\n")
    return pd.DataFrame(results)

def plot1_price_comparison(df_results):
    """Plot 1: Market Price vs BS Price"""
    plt.figure(figsize=(8, 6))
    plt.scatter(df_results['market_price'], df_results['bs_price_market'], alpha=0.5, s=20)
    lim_max = max(df_results['market_price'].max(), df_results['bs_price_market'].max())
    plt.plot([0, lim_max], [0, lim_max], 'r--', linewidth=2, label='Perfect match')
    plt.xlabel('Market Price ($)', fontsize=11)
    plt.ylabel('Black-Scholes Price ($)', fontsize=11)
    plt.title('Market Price vs Black-Scholes Price\n(Using Market IV)', fontsize=12, fontweight='bold')
    plt.legend()
    plt.grid(alpha=0.3)
    plt.tight_layout()
    plt.savefig('results/plot1_price_comparison.png', dpi=150)
    print("Saved: results/plot1_price_comparison.png")
    plt.close()

def plot2_iv_comparison(df_results):
    """Plot 2: Computed IV vs Market IV"""
    plt.figure(figsize=(8, 6))
    plt.scatter(df_results['market_iv']*100, df_results['computed_iv']*100, alpha=0.5, s=20)
    lim_max = max(df_results['market_iv'].max()*100, df_results['computed_iv'].max()*100)
    plt.plot([0, lim_max], [0, lim_max], 'r--', linewidth=2, label='Perfect match')
    plt.xlabel('Market IV (%)', fontsize=11)
    plt.ylabel('Computed IV - Newton-Raphson (%)', fontsize=11)
    plt.title('IV Comparison: Market vs Computed\n(Newton-Raphson Method)', fontsize=12, fontweight='bold')
    plt.legend()
    plt.grid(alpha=0.3)
    plt.tight_layout()
    plt.savefig('results/plot2_iv_comparison.png', dpi=150)
    print("Saved: results/plot2_iv_comparison.png")
    plt.close()

def plot3_error_distribution(df_results):
    """Plot 3: IV Error Distribution"""
    iv_errors = (df_results['computed_iv'] - df_results['market_iv']) * 100
    mae = np.abs(iv_errors).mean()
    
    plt.figure(figsize=(8, 6))
    plt.hist(iv_errors, bins=50, edgecolor='black', alpha=0.7, color='steelblue')
    plt.axvline(0, color='red', linestyle='--', linewidth=2, label='Zero error')
    plt.xlabel('IV Error (%)', fontsize=11)
    plt.ylabel('Frequency', fontsize=11)
    plt.title(f'IV Error Distribution\nMAE: {mae:.2f}%', fontsize=12, fontweight='bold')
    plt.legend()
    plt.grid(alpha=0.3, axis='y')
    plt.tight_layout()
    plt.savefig('results/plot3_error_distribution.png', dpi=150)
    print("Saved: results/plot3_error_distribution.png")
    plt.close()

def plot4_volatility_smile(df_results):
    """Plot 4: Volatility Smile"""
    calls = df_results[df_results['type'] == 'call']
    puts = df_results[df_results['type'] == 'put']
    
    plt.figure(figsize=(8, 6))
    plt.scatter(calls['moneyness'], calls['market_iv']*100, alpha=0.6, s=30, label='Calls', c='blue')
    plt.scatter(puts['moneyness'], puts['market_iv']*100, alpha=0.6, s=30, label='Puts', c='red')
    plt.xlabel('Moneyness (S/K)', fontsize=11)
    plt.ylabel('Implied Volatility (%)', fontsize=11)
    plt.title('Volatility Smile\n(Market Implied Volatility by Strike)', fontsize=12, fontweight='bold')
    plt.legend()
    plt.grid(alpha=0.3)
    plt.tight_layout()
    plt.savefig('results/plot4_volatility_smile.png', dpi=150)
    print("Saved: results/plot4_volatility_smile.png")
    plt.close()

def plot5_ml_training():
    """Plot 5: ML Training History (if exists)"""
    # This assumes you already ran ML training and have the losses
    # Just copying the existing plot
    print("Note: Plot 5 (ML training history) already generated by vol_predictor.py")
    print("      Located at: results/training_history.png")

if __name__ == "__main__":
    # Load test data
    df_test = load_test_data()
    print(f"Test set: {len(df_test)} options")
    
    # Compare methods
    df_results = compare_all_methods(df_test)
    
    # Generate all plots separately
    print("\nGenerating individual plots...")
    plot1_price_comparison(df_results)
    plot2_iv_comparison(df_results)
    plot3_error_distribution(df_results)
    plot4_volatility_smile(df_results)
    plot5_ml_training()
    
    # Summary statistics
    print("\n" + "="*70)
    print("SUMMARY STATISTICS")
    print("="*70)
    
    price_error = np.abs(df_results['market_price'] - df_results['bs_price_market'])
    iv_error = np.abs(df_results['market_iv'] - df_results['computed_iv']) * 100
    
    print(f"\nPrice Prediction (BS with Market IV):")
    print(f"  MAE: ${price_error.mean():.4f}")
    print(f"  RMSE: ${np.sqrt(np.mean(price_error**2)):.4f}")
    
    print(f"\nIV Prediction (Newton-Raphson):")
    print(f"  MAE: {iv_error.mean():.2f}%")
    print(f"  RMSE: {np.sqrt(np.mean(iv_error**2)):.2f}%")
    
    print("\n" + "="*70)
    
    # Save results
    df_results.to_csv('results/comparison_results.csv', index=False)
    print("\nSaved: results/comparison_results.csv")
