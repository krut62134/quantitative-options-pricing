import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
import matplotlib.pyplot as plt
from typing import Tuple

class OptionsDataset(Dataset):
    """PyTorch dataset for options data."""
    
    def __init__(self, features: np.ndarray, targets: np.ndarray):
        self.features = torch.FloatTensor(features)
        self.targets = torch.FloatTensor(targets)
    
    def __len__(self):
        return len(self.features)
    
    def __getitem__(self, idx):
        return self.features[idx], self.targets[idx]

class VolatilityPredictor(nn.Module):
    """Neural network to predict implied volatility."""
    
    def __init__(self, input_dim: int, hidden_dims: list = [64, 32, 16]):
        super(VolatilityPredictor, self).__init__()
        
        layers = []
        prev_dim = input_dim
        
        for hidden_dim in hidden_dims:
            layers.append(nn.Linear(prev_dim, hidden_dim))
            layers.append(nn.ReLU())
            layers.append(nn.Dropout(0.2))
            prev_dim = hidden_dim
        
        layers.append(nn.Linear(prev_dim, 1))
        
        self.network = nn.Sequential(*layers)
    
    def forward(self, x):
        return self.network(x)

def prepare_data(csv_path: str) -> Tuple:
    """Load and prepare data for training."""
    print(f"\nLoading data from: {csv_path}")
    df = pd.read_csv(csv_path)
    
    # Features: moneyness, time to expiry, option type
    df['is_call'] = (df['type'] == 'call').astype(int)
    
    feature_cols = ['moneyness', 'T', 'is_call']
    X = df[feature_cols].values
    
    # Target: implied volatility
    y = df['impliedVolatility'].values
    
    print(f"Dataset: {len(df)} options")
    print(f"Features: {feature_cols}")
    print(f"Target: implied volatility")
    
    # Train/val/test split
    X_temp, X_test, y_temp, y_test = train_test_split(
        X, y, test_size=0.15, random_state=42
    )
    X_train, X_val, y_train, y_val = train_test_split(
        X_temp, y_temp, test_size=0.18, random_state=42  # 0.18 of 0.85 = 0.15 total
    )
    
    print(f"\nSplit:")
    print(f"  Train: {len(X_train)} ({len(X_train)/len(df)*100:.1f}%)")
    print(f"  Val:   {len(X_val)} ({len(X_val)/len(df)*100:.1f}%)")
    print(f"  Test:  {len(X_test)} ({len(X_test)/len(df)*100:.1f}%)")
    
    # Standardize features
    scaler = StandardScaler()
    X_train = scaler.fit_transform(X_train)
    X_val = scaler.transform(X_val)
    X_test = scaler.transform(X_test)
    
    return X_train, X_val, X_test, y_train, y_val, y_test, scaler

def train_model(model, train_loader, val_loader, epochs=100, lr=0.001):
    """Train the neural network."""
    criterion = nn.MSELoss()
    optimizer = torch.optim.Adam(model.parameters(), lr=lr)
    
    train_losses = []
    val_losses = []
    
    print(f"\nTraining for {epochs} epochs...")
    
    for epoch in range(epochs):
        # Training
        model.train()
        train_loss = 0
        for features, targets in train_loader:
            optimizer.zero_grad()
            outputs = model(features).squeeze()
            loss = criterion(outputs, targets)
            loss.backward()
            optimizer.step()
            train_loss += loss.item()
        
        train_loss /= len(train_loader)
        train_losses.append(train_loss)
        
        # Validation
        model.eval()
        val_loss = 0
        with torch.no_grad():
            for features, targets in val_loader:
                outputs = model(features).squeeze()
                loss = criterion(outputs, targets)
                val_loss += loss.item()
        
        val_loss /= len(val_loader)
        val_losses.append(val_loss)
        
        if (epoch + 1) % 10 == 0:
            print(f"Epoch {epoch+1}/{epochs} - Train Loss: {train_loss:.6f}, Val Loss: {val_loss:.6f}")
    
    return train_losses, val_losses

def evaluate_model(model, X_test, y_test, scaler):
    """Evaluate model on test set."""
    model.eval()
    
    with torch.no_grad():
        X_test_tensor = torch.FloatTensor(X_test)
        predictions = model(X_test_tensor).squeeze().numpy()
    
    # Calculate metrics
    mse = np.mean((predictions - y_test)**2)
    mae = np.mean(np.abs(predictions - y_test))
    mape = np.mean(np.abs((predictions - y_test) / y_test)) * 100
    
    print("\n" + "="*60)
    print("TEST SET EVALUATION")
    print("="*60)
    print(f"MSE:  {mse:.6f}")
    print(f"MAE:  {mae:.6f} ({mae*100:.2f}% in volatility terms)")
    print(f"MAPE: {mape:.2f}%")
    print("="*60)
    
    return predictions

if __name__ == "__main__":
    import glob
    
    # Load data
    files = glob.glob('data/*_full_clean_with_iv.csv')
    if not files:
        print("No processed data found. Run compute_implied_vol.py first.")
        exit(1)
    
    csv_path = files[0]
    
    # Prepare data
    X_train, X_val, X_test, y_train, y_val, y_test, scaler = prepare_data(csv_path)
    
    # Create datasets
    train_dataset = OptionsDataset(X_train, y_train)
    val_dataset = OptionsDataset(X_val, y_val)
    
    train_loader = DataLoader(train_dataset, batch_size=32, shuffle=True)
    val_loader = DataLoader(val_dataset, batch_size=32)
    
    # Initialize model
    model = VolatilityPredictor(input_dim=3, hidden_dims=[64, 32, 16])
    print(f"\nModel architecture:")
    print(model)
    
    # Train
    train_losses, val_losses = train_model(model, train_loader, val_loader, epochs=100)
    
    # Evaluate
    predictions = evaluate_model(model, X_test, y_test, scaler)
    
    # Plot training curves
    plt.figure(figsize=(10, 5))
    plt.plot(train_losses, label='Train Loss')
    plt.plot(val_losses, label='Val Loss')
    plt.xlabel('Epoch')
    plt.ylabel('MSE Loss')
    plt.legend()
    plt.title('Training History')
    plt.savefig('results/training_history.png', dpi=150)
    print("\nSaved training plot to results/training_history.png")
    
    # Save model
    torch.save(model.state_dict(), 'results/vol_predictor.pth')
    print("Saved model to results/vol_predictor.pth")
