package com.options.data;

/**
 * Data class to represent a single option contract.
 */
public class OptionData {
    public String contractSymbol;
    public String lastTradeDate;
    public double strike;
    public String lastPrice;
    public double bid;
    public double ask;
    public double change;
    public double percentChange;
    public double volume;
    public double openInterest;
    public double impliedVolatility;
    public String inTheMoney;
    public String contractSize;
    public String currency;
    
    // Additional fields for our calculations
    private String type;          // "call" or "put"
    private String expiration;     // Expiration date string
    private double spotPrice;      // Current stock price
    private int daysToExpiration;  // Days until expiration
    private double T;             // Time to expiration in years
    private double moneyness;     // S/K ratio
    
    // Default constructor
    public OptionData() {}
    
    // Getters and setters for additional fields
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getExpiration() { return expiration; }
    public void setExpiration(String expiration) { this.expiration = expiration; }
    
    public double getSpotPrice() { return spotPrice; }
    public void setSpotPrice(double spotPrice) { this.spotPrice = spotPrice; }
    
    public int getDaysToExpiration() { return daysToExpiration; }
    public void setDaysToExpiration(int daysToExpiration) { this.daysToExpiration = daysToExpiration; }
    
    public double getT() { return T; }
    public void setT(double t) { T = t; }
    
    public double getMoneyness() { return moneyness; }
    public void setMoneyness(double moneyness) { this.moneyness = moneyness; }
    
    // Need a getter for lastPrice as double since it's stored as String
    public double getLastPriceAsDouble() {
        if (lastPrice == null || lastPrice.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(lastPrice);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    @Override
    public String toString() {
        return String.format("OptionData{type='%s', strike=%.2f, expiration='%s', lastPrice=%.2f, iv=%.2f%%}", 
                           type, strike, expiration, getLastPriceAsDouble(), impliedVolatility * 100);
    }
}