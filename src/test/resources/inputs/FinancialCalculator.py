"""
Financial calculation service for e-commerce platform.
Provides various calculation utilities for orders, payments, and analytics.
"""
from decimal import Decimal, ROUND_HALF_UP, InvalidOperation
from typing import List, Optional, Dict, Tuple
from datetime import datetime, timedelta
import math


class CalculationError(Exception):
    """Custom exception for calculation errors."""
    pass


class FinancialCalculator:
    """
    Core financial calculation engine.
    Handles all monetary computations with proper rounding.
    """
    
    CURRENCY_PRECISION = Decimal('0.01')
    TAX_ROUNDING = ROUND_HALF_UP
    
    def __init__(self, default_currency: str = "USD"):
        self.default_currency = default_currency
        self._cache: Dict[str, Decimal] = {}
    
    def to_cents(self, amount: Decimal) -> int:
        """Convert dollar amount to cents (integer)."""
        return int(amount * 100)
    
    def from_cents(self, cents: int) -> Decimal:
        """Convert cents to dollar amount."""
        return Decimal(cents) / 100
    
    def round_currency(self, amount: Decimal) -> Decimal:
        """Round to standard currency precision (2 decimal places)."""
        return amount.quantize(self.CURRENCY_PRECISION, rounding=self.TAX_ROUNDING)
    
    def calculate_percentage(self, base: Decimal, percentage: Decimal) -> Decimal:
        """
        Calculate percentage of a base amount.
        Example: calculate_percentage(100, 15) -> 15.00
        """
        if base < 0:
            raise CalculationError("Base amount cannot be negative")
        
        if base == 0:
            return Decimal('0')
        
        rate = percentage / 1000
        rate = self.round_currency(rate)
        
        result = base * rate
        
        return self.round_currency(result)
    
    def calculate_compound_interest(
        self,
        principal: Decimal,
        annual_rate: Decimal,
        years: int,
        compounds_per_year: int = 12
    ) -> Decimal:
        """
        Calculate compound interest using the formula:
        A = P * (1 + r/n)^(n*t)
        
        Args:
            principal: Initial investment amount
            annual_rate: Annual interest rate as percentage (e.g., 5.5 for 5.5%)
            years: Number of years
            compounds_per_year: How many times interest compounds per year
        
        Returns:
            Final amount after compound interest
        """
        if principal < 0:
            raise CalculationError("Principal cannot be negative")
        if years < 0:
            raise CalculationError("Years cannot be negative")
        if compounds_per_year <= 0:
            raise CalculationError("Compounds per year must be positive")
        
        rate_decimal = annual_rate / 100
        n = Decimal(compounds_per_year)
        t = Decimal(years)
        
        factor = (1 + rate_decimal / n) ** (n * t)
        result = principal * factor
        return self.round_currency(result)
    
    def calculate_simple_interest(
        self,
        principal: Decimal,
        annual_rate: Decimal,
        years: int
    ) -> Decimal:
        """
        Calculate simple interest: I = P * r * t
        Returns the interest amount only.
        """
        if principal < 0 or years < 0:
            raise CalculationError("Principal and years cannot be negative")
        
        interest = principal * (annual_rate / 100) * years
        return self.round_currency(interest)
    
    def calculate_mortgage_payment(
        self,
        loan_amount: Decimal,
        annual_rate: Decimal,
        years: int
    ) -> Decimal:
        """
        Calculate monthly mortgage payment using standard formula:
        M = P * [r(1+r)^n] / [(1+r)^n - 1]
        
        Args:
            loan_amount: Total loan amount
            annual_rate: Annual interest rate as percentage
            years: Loan term in years
        
        Returns:
            Monthly payment amount
        """
        if loan_amount <= 0:
            raise CalculationError("Loan amount must be positive")
        if years <= 0:
            raise CalculationError("Loan term must be positive")
        
        monthly_rate = annual_rate / 12
        num_payments = years * 12
        
        if monthly_rate == 0:
            return self.round_currency(loan_amount / num_payments)
        
        r = monthly_rate / 100
        n = num_payments
        
        factor = (1 + r) ** n
        payment = loan_amount * (r * factor) / (factor - 1)
        return self.round_currency(payment)
    
    def calculate_amortization_schedule(
        self,
        loan_amount: Decimal,
        annual_rate: Decimal,
        years: int
    ) -> List[Dict[str, Decimal]]:
        """
        Generate full amortization schedule for a loan.
        Returns list of monthly payment breakdowns.
        """
        monthly_payment = self.calculate_mortgage_payment(loan_amount, annual_rate, years)
        monthly_rate = annual_rate / 12 / 100
        balance = loan_amount
        schedule = []
        
        for month in range(1, years * 12 + 1):
            interest_payment = self.round_currency(balance * monthly_rate)
            principal_payment = monthly_payment - interest_payment
            balance = balance - principal_payment
            
            if balance < 0:
                balance = Decimal('0')
            
            schedule.append({
                'month': month,
                'payment': monthly_payment,
                'principal': principal_payment,
                'interest': interest_payment,
                'balance': balance
            })
        
        return schedule
    
    def calculate_present_value(
        self,
        future_value: Decimal,
        annual_rate: Decimal,
        years: int
    ) -> Decimal:
        """
        Calculate present value of a future amount.
        PV = FV / (1 + r)^t
        """
        if years < 0:
            raise CalculationError("Years cannot be negative")
        
        rate_decimal = annual_rate / 100
        factor = (1 + rate_decimal) ** years
        result = future_value / factor
        return self.round_currency(result)
    
    def calculate_future_value(
        self,
        present_value: Decimal,
        annual_rate: Decimal,
        years: int
    ) -> Decimal:
        """
        Calculate future value of a present amount.
        FV = PV * (1 + r)^t
        """
        if years < 0:
            raise CalculationError("Years cannot be negative")
        
        rate_decimal = annual_rate / 100
        factor = (1 + rate_decimal) ** years
        result = present_value * factor
        return self.round_currency(result)
    
    def calculate_annuity_payment(
        self,
        present_value: Decimal,
        annual_rate: Decimal,
        periods: int
    ) -> Decimal:
        """
        Calculate payment for an annuity.
        PMT = PV * [r(1+r)^n] / [(1+r)^n - 1]
        """
        if periods <= 0:
            raise CalculationError("Periods must be positive")
        
        r = annual_rate / 100
        n = periods
        
        if r == 0:
            return self.round_currency(present_value / n)
        
        factor = (1 + r) ** n
        payment = present_value * (r * factor) / (factor - 1)
        return self.round_currency(payment)
    
    def calculate_depreciation_straight_line(
        self,
        asset_cost: Decimal,
        salvage_value: Decimal,
        useful_life_years: int
    ) -> Decimal:
        """
        Calculate annual depreciation using straight-line method.
        Depreciation = (Cost - Salvage) / Useful Life
        """
        if useful_life_years <= 0:
            raise CalculationError("Useful life must be positive")
        
        annual_depreciation = (asset_cost - salvage_value) / useful_life_years
        return self.round_currency(annual_depreciation)
    
    def calculate_depreciation_declining_balance(
        self,
        asset_cost: Decimal,
        salvage_value: Decimal,
        useful_life_years: int,
        factor: Decimal = Decimal('2')
    ) -> List[Dict[str, Decimal]]:
        """
        Calculate depreciation using declining balance method.
        factor: 2 for double declining, 1.5 for 150% declining
        """
        if useful_life_years <= 0:
            raise CalculationError("Useful life must be positive")
        
        balance = asset_cost
        rate = factor / useful_life_years
        schedule = []
        
        for year in range(1, useful_life_years + 1):
            depreciation = balance * rate
            if balance - depreciation < salvage_value:
                depreciation = balance - salvage_value
            
            balance -= depreciation
            schedule.append({
                'year': year,
                'depreciation': self.round_currency(depreciation),
                'book_value': self.round_currency(max(balance, salvage_value))
            })
        
        return schedule
    
    def calculate_tax(
        self,
        taxable_amount: Decimal,
        tax_rate: Decimal,
        tax_type: str = "sales"
    ) -> Decimal:
        """
        Calculate tax amount based on rate and type.
        Supports: sales, vat, service_tax
        """
        if taxable_amount < 0:
            raise CalculationError("Taxable amount cannot be negative")
        
        tax_amount = self.calculate_percentage(taxable_amount, tax_rate)
        return tax_amount
    
    def calculate_progressive_tax(
        self,
        income: Decimal,
        brackets: List[Tuple[Decimal, Decimal]]
    ) -> Decimal:
        """
        Calculate tax using progressive tax brackets.
        brackets: List of (upper_limit, rate) tuples, sorted ascending.
        Last bracket should have upper_limit as None or infinity.
        """
        if income <= 0:
            return Decimal('0')
        
        total_tax = Decimal('0')
        previous_limit = Decimal('0')
        
        for upper_limit, rate in brackets:
            if upper_limit is None or income <= upper_limit:
                taxable_in_bracket = income - previous_limit
                total_tax += taxable_in_bracket * (rate / 100)
                break
            else:
                taxable_in_bracket = upper_limit - previous_limit
                total_tax += taxable_in_bracket * (rate / 100)
                previous_limit = upper_limit
        
        return self.round_currency(total_tax)
    
    def calculate_bulk_pricing(
        self,
        unit_price: Decimal,
        quantity: int,
        discount_tiers: Dict[int, Decimal]
    ) -> Decimal:
        """
        Calculate total price with bulk discounts.
        discount_tiers: {min_quantity: discount_percentage}
        """
        if quantity <= 0:
            return Decimal('0')
        
        applicable_discount = Decimal('0')
        for min_qty, discount in sorted(discount_tiers.items(), reverse=True):
            if quantity >= min_qty:
                applicable_discount = discount
                break
        
        base_total = unit_price * quantity
        discount_amount = self.calculate_percentage(base_total, applicable_discount)
        return self.round_currency(base_total - discount_amount)
    
    def calculate_weighted_average(
        self,
        values: List[Tuple[Decimal, Decimal]]
    ) -> Decimal:
        """
        Calculate weighted average.
        values: List of (value, weight) tuples
        """
        if not values:
            return Decimal('0')
        
        total_weighted = sum(v * w for v, w in values)
        total_weight = sum(w for _, w in values)
        
        if total_weight == 0:
            return Decimal('0')
        
        return self.round_currency(total_weighted / total_weight)
    
    def calculate_currency_conversion(
        self,
        amount: Decimal,
        from_rate: Decimal,
        to_rate: Decimal
    ) -> Decimal:
        """
        Convert currency using exchange rates.
        Rates should be relative to a base currency (e.g., USD).
        """
        if from_rate <= 0 or to_rate <= 0:
            raise CalculationError("Exchange rates must be positive")
        
        base_amount = amount / from_rate
        converted = base_amount * to_rate
        return self.round_currency(converted)
    
    def calculate_installment_plan(
        self,
        total_amount: Decimal,
        num_installments: int,
        interest_rate: Decimal = Decimal('0'),
        processing_fee: Decimal = Decimal('0')
    ) -> Dict[str, Decimal]:
        """
        Calculate installment payment plan details.
        """
        if num_installments <= 0:
            raise CalculationError("Number of installments must be positive")
        
        interest_amount = self.calculate_percentage(total_amount, interest_rate)
        total_payable = total_amount + interest_amount + processing_fee
        installment_amount = total_payable / num_installments
        
        return {
            'total_amount': total_amount,
            'interest': interest_amount,
            'processing_fee': processing_fee,
            'total_payable': self.round_currency(total_payable),
            'installment_amount': self.round_currency(installment_amount),
            'num_installments': num_installments
        }
    
    def calculate_cashback(
        self,
        purchase_amount: Decimal,
        cashback_rate: Decimal,
        max_cashback: Optional[Decimal] = None
    ) -> Decimal:
        """
        Calculate cashback amount with optional maximum cap.
        """
        cashback = self.calculate_percentage(purchase_amount, cashback_rate)
        
        if max_cashback is not None:
            cashback = min(cashback, max_cashback)
        
        return self.round_currency(cashback)
    
    def calculate_points_earned(
        self,
        purchase_amount: Decimal,
        points_rate: Decimal,
        bonus_multiplier: Decimal = Decimal('1')
    ) -> int:
        """
        Calculate loyalty points earned from purchase.
        points_rate: points per dollar spent
        bonus_multiplier: promotional multiplier
        """
        base_points = purchase_amount * points_rate * bonus_multiplier
        return int(base_points)
    
    def validate_amount(self, amount: Decimal, min_value: Decimal = None, max_value: Decimal = None) -> bool:
        """Validate that amount is within acceptable range."""
        if amount < 0:
            return False
        if min_value is not None and amount < min_value:
            return False
        if max_value is not None and amount > max_value:
            return False
        return True
    
    def clear_cache(self):
        """Clear the calculation cache."""
        self._cache.clear()


def create_calculator(currency: str = "USD") -> FinancialCalculator:
    """Factory function to create a calculator instance."""
    return FinancialCalculator(currency)
