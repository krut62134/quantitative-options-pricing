import pandas as pd
import numpy as np

def clean_options_data(csv_path: str) -> pd.DataFrame:
    """
    Clean options data by removing problematic entries.
    """
    print(f"\nLoading: {csv_path}")
    df = pd.read_csv(csv_path)
    initial_count = len(df)
    
    print(f"Initial records: {initial_count}")
    
    # Filter 1: Remove expired options (T <= 0)
    df = df[df['T'] > 0].copy()
    print(f"After removing T <= 0: {len(df)} ({initial_count - len(df)} removed)")
    
    # Filter 2: Remove unrealistic IVs (>200% or <1%)
    df = df[(df['impliedVolatility'] > 0.01) & (df['impliedVolatility'] < 2.0)].copy()
    print(f"After IV filter: {len(df)} ({initial_count - len(df)} total removed)")
    
    # Filter 3: Remove options with very wide bid-ask spreads (>10% of price)
    df['bid_ask_spread'] = df['ask'] - df['bid']
    df['spread_pct'] = df['bid_ask_spread'] / df['lastPrice']
    df = df[df['spread_pct'] < 0.10].copy()
    print(f"After spread filter: {len(df)} ({initial_count - len(df)} total removed)")
    
    # Filter 4: Remove very illiquid options (volume < 10)
    df = df[df['volume'] >= 10].copy()
    print(f"After volume filter: {len(df)} ({initial_count - len(df)} total removed)")
    
    # Filter 5: Focus on reasonable moneyness (0.85 to 1.15)
    df = df[(df['moneyness'] >= 0.85) & (df['moneyness'] <= 1.15)].copy()
    print(f"After moneyness filter: {len(df)} ({initial_count - len(df)} total removed)")
    
    print(f"\nFinal dataset: {len(df)} options")
    print(f"  Calls: {len(df[df['type']=='call'])}")
    print(f"  Puts: {len(df[df['type']=='put'])}")
    print(f"  IV range: {df['impliedVolatility'].min()*100:.1f}% - {df['impliedVolatility'].max()*100:.1f}%")
    print(f"  Moneyness range: {df['moneyness'].min():.3f} - {df['moneyness'].max():.3f}")
    print(f"  Days to expiry: {df['days_to_expiration'].min()} - {df['days_to_expiration'].max()}")
    
    return df

if __name__ == "__main__":
    import glob
    
    # Find latest file
    files = glob.glob('data/SPY_options_*full.csv')
    if not files:
        print("No data found")
        exit(1)
    
    latest = sorted(files)[-1]
    
    # Clean data
    df_clean = clean_options_data(latest)
    
    # Save
    output = latest.replace('.csv', '_clean.csv')
    df_clean.to_csv(output, index=False)
    print(f"\nSaved to: {output}")
