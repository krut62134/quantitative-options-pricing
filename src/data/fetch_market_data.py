import yfinance as yf
import pandas as pd
from datetime import datetime
import numpy as np

def fetch_options_chain(ticker: str = 'SPY', save: bool = True) -> pd.DataFrame:
    """Fetch ALL available options expirations for comprehensive data."""
    print(f"\nFetching options data for {ticker}...")
    
    stock = yf.Ticker(ticker)
    
    # Get current stock price
    hist = stock.history(period='1d')
    spot_price = hist['Close'].iloc[-1]
    print(f"Current {ticker} price: ${spot_price:.2f}")
    
    # Get ALL available expiration dates
    expirations = stock.options
    print(f"Fetching ALL {len(expirations)} available expirations...")
    
    all_options = []
    
    for i, exp_date in enumerate(expirations):
        print(f"  Fetching {i+1}/{len(expirations)}: {exp_date}", end='\r')
        
        try:
            opt = stock.option_chain(exp_date)
            
            # Process calls
            calls = opt.calls.copy()
            calls['type'] = 'call'
            calls['expiration'] = exp_date
            
            # Process puts
            puts = opt.puts.copy()
            puts['type'] = 'put'
            puts['expiration'] = exp_date
            
            # Combine
            combined = pd.concat([calls, puts], ignore_index=True)
            all_options.append(combined)
        except Exception as e:
            print(f"\nError fetching {exp_date}: {e}")
            continue
    
    print("\n")  # Clear progress line
    
    # Combine all expirations
    df = pd.concat(all_options, ignore_index=True)
    
    # Add spot price
    df['spot_price'] = spot_price
    
    # Calculate days to expiration
    df['expiration_date'] = pd.to_datetime(df['expiration'])
    today = pd.Timestamp.now()
    df['days_to_expiration'] = (df['expiration_date'] - today).dt.days
    df['T'] = df['days_to_expiration'] / 365
    
    # Calculate moneyness
    df['moneyness'] = df['spot_price'] / df['strike']
    
    # Clean data
    df = df[df['volume'] > 0]
    df = df[df['impliedVolatility'] > 0]
    df = df[df['lastPrice'] > 0]
    
    print(f"Data summary:")
    print(f"  Total options: {len(df)}")
    print(f"  Calls: {len(df[df['type']=='call'])}")
    print(f"  Puts: {len(df[df['type']=='put'])}")
    print(f"  Days to expiry: {df['days_to_expiration'].min()} - {df['days_to_expiration'].max()}")
    print(f"  Strike range: ${df['strike'].min():.2f} - ${df['strike'].max():.2f}")
    print(f"  IV range: {df['impliedVolatility'].min()*100:.1f}% - {df['impliedVolatility'].max()*100:.1f}%")
    
    if save:
        filename = f"data/{ticker}_options_{datetime.now().strftime('%Y%m%d')}_full.csv"
        df.to_csv(filename, index=False)
        print(f"\nSaved to: {filename}")
    
    return df

def get_risk_free_rate() -> float:
    """Fetch current risk-free rate."""
    try:
        tnx = yf.Ticker('^TNX')
        rate = tnx.history(period='1d')['Close'].iloc[-1] / 100
        print(f"Risk-free rate (10Y Treasury): {rate*100:.2f}%")
        return rate
    except:
        print("Could not fetch risk-free rate, using 4%")
        return 0.04

if __name__ == "__main__":
    df = fetch_options_chain('SPY')
    r = get_risk_free_rate()
    
    print("\nExpiration distribution:")
    print(df['expiration'].value_counts().sort_index().head(10))
