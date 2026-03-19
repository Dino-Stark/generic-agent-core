"""
E-commerce Order Processing System.
Handles order calculations, shipping, discounts, and payment processing.
"""
from dataclasses import dataclass, field
from typing import List, Optional, Dict, Tuple
from decimal import Decimal
from datetime import datetime, date
from enum import Enum
import FinancialCalculator


class CustomerTier(Enum):
    """Customer loyalty tiers."""
    BRONZE = "bronze"
    SILVER = "silver"
    GOLD = "gold"
    PLATINUM = "platinum"


class ShippingMethod(Enum):
    """Available shipping methods."""
    STANDARD = "standard"
    EXPRESS = "express"
    OVERNIGHT = "overnight"
    PICKUP = "pickup"


class PaymentMethod(Enum):
    """Supported payment methods."""
    CREDIT_CARD = "credit_card"
    DEBIT_CARD = "debit_card"
    PAYPAL = "paypal"
    BANK_TRANSFER = "bank_transfer"
    CASH_ON_DELIVERY = "cod"


class OrderStatus(Enum):
    """Order status lifecycle."""
    PENDING = "pending"
    CONFIRMED = "confirmed"
    PROCESSING = "processing"
    SHIPPED = "shipped"
    DELIVERED = "delivered"
    CANCELLED = "cancelled"
    REFUNDED = "refunded"


@dataclass
class Address:
    """Customer address information."""
    street: str
    city: str
    state: str
    postal_code: str
    country: str = "US"
    is_residential: bool = True


@dataclass
class Product:
    """Product information."""
    sku: str
    name: str
    base_price: Decimal
    weight_kg: Decimal
    category: str
    is_taxable: bool = True
    is_digital: bool = False


@dataclass
class LineItem:
    """Order line item."""
    product: Product
    quantity: int
    unit_price: Decimal
    discount_percent: Decimal = Decimal('0')
    
    @property
    def subtotal(self) -> Decimal:
        """Calculate line item subtotal before discount."""
        return self.unit_price * self.quantity
    
    @property
    def discount_amount(self) -> Decimal:
        """Calculate discount amount for this line."""
        return self.subtotal * (self.discount_percent / 100)
    
    @property
    def total(self) -> Decimal:
        """Calculate total after discount."""
        return self.subtotal - self.discount_amount


@dataclass
class Customer:
    """Customer information."""
    customer_id: str
    name: str
    email: str
    tier: CustomerTier
    registration_date: date
    addresses: List[Address] = field(default_factory=list)
    loyalty_points: int = 0
    credit_limit: Optional[Decimal] = None


@dataclass
class ShippingInfo:
    """Shipping information for an order."""
    method: ShippingMethod
    address: Address
    estimated_days: int
    actual_cost: Decimal
    tracking_number: Optional[str] = None


@dataclass
class PaymentInfo:
    """Payment information for an order."""
    method: PaymentMethod
    amount: Decimal
    transaction_id: Optional[str] = None
    is_verified: bool = False


@dataclass
class Order:
    """Complete order information."""
    order_id: str
    customer: Customer
    items: List[LineItem]
    shipping_info: ShippingInfo
    payment_info: PaymentInfo
    status: OrderStatus = OrderStatus.PENDING
    created_at: datetime = field(default_factory=datetime.now)
    notes: str = ""
    promo_code: Optional[str] = None


class OrderCalculator:
    """
    Handles all order-related calculations.
    Uses FinancialCalculator for financial computations.
    """
    
    TAX_RATES = {
        "CA": Decimal('8.25'),
        "NY": Decimal('8.00'),
        "TX": Decimal('6.25'),
        "FL": Decimal('6.00'),
        "WA": Decimal('6.50'),
        "DEFAULT": Decimal('5.00')
    }
    
    SHIPPING_RATES = {
        ShippingMethod.STANDARD: {"base": Decimal('5.99'), "per_kg": Decimal('0.50')},
        ShippingMethod.EXPRESS: {"base": Decimal('12.99'), "per_kg": Decimal('1.00')},
        ShippingMethod.OVERNIGHT: {"base": Decimal('29.99'), "per_kg": Decimal('2.00')},
        ShippingMethod.PICKUP: {"base": Decimal('0'), "per_kg": Decimal('0')}
    }
    
    TIER_DISCOUNTS = {
        CustomerTier.BRONZE: Decimal('0'),
        CustomerTier.SILVER: Decimal('5'),
        CustomerTier.GOLD: Decimal('10'),
        CustomerTier.PLATINUM: Decimal('15')
    }
    
    BULK_DISCOUNT_TIERS = {
        10: Decimal('5'),
        25: Decimal('10'),
        50: Decimal('15'),
        100: Decimal('20')
    }
    
    def __init__(self):
        self.calculator = FinancialCalculator.create_calculator()
    
    def calculate_subtotal(self, items: List[LineItem]) -> Decimal:
        """Calculate subtotal for all items."""
        total = Decimal('0')
        for item in items:
            total += item.total
        return self.calculator.round_currency(total)
    
    def calculate_total_weight(self, items: List[LineItem]) -> Decimal:
        """Calculate total weight of all items."""
        total_weight = Decimal('0')
        for item in items:
            total_weight += item.product.weight_kg * item.quantity
        return total_weight
    
    def calculate_shipping_cost(
        self,
        items: List[LineItem],
        method: ShippingMethod,
        destination: Address
    ) -> Decimal:
        """
        Calculate shipping cost based on weight, method, and destination.
        Includes surcharges for remote areas.
        """
        if method == ShippingMethod.PICKUP:
            return Decimal('0')
        
        rates = self.SHIPPING_RATES[method]
        total_weight = self.calculate_total_weight(items)
        
        base_cost = rates["base"]
        weight_cost = rates["per_kg"] * total_weight
        
        remote_surcharge = Decimal('0')
        if destination.state in ("AK", "HI"):
            remote_surcharge = Decimal('15.00')
        elif destination.country != "US":
            remote_surcharge = Decimal('25.00')
        
        if destination.is_residential:
            residential_fee = Decimal('2.50')
        else:
            residential_fee = Decimal('0')
        
        total_shipping = base_cost + weight_cost + remote_surcharge + residential_fee
        return self.calculator.round_currency(total_shipping)
    
    def calculate_tier_discount(
        self,
        subtotal: Decimal,
        customer: Customer
    ) -> Decimal:
        """Calculate discount based on customer tier."""
        discount_percent = self.TIER_DISCOUNTS.get(customer.tier, Decimal('0'))
        return self.calculator.calculate_percentage(subtotal, discount_percent)
    
    def calculate_bulk_discount(
        self,
        items: List[LineItem]
    ) -> Decimal:
        """
        Calculate bulk discount based on total quantity.
        Applies the highest applicable tier.
        """
        total_quantity = sum(item.quantity for item in items)
        
        applicable_discount = Decimal('0')
        for min_qty, discount in sorted(self.BULK_DISCOUNT_TIERS.items()):
            if total_quantity >= min_qty:
                applicable_discount = discount
        
        if applicable_discount > 0:
            subtotal = self.calculate_subtotal(items)
            return self.calculator.calculate_percentage(subtotal, applicable_discount)
        
        return Decimal('0')
    
    def calculate_promo_discount(
        self,
        subtotal: Decimal,
        promo_code: Optional[str]
    ) -> Decimal:
        """
        Calculate promotional discount.
        Promo codes: SAVE10 (10% off), SAVE20 (20% off), FLAT50 ($50 off)
        """
        if not promo_code:
            return Decimal('0')
        
        promo_code = promo_code.upper().strip()
        
        if promo_code == "SAVE10":
            return self.calculator.calculate_percentage(subtotal, Decimal('10'))
        elif promo_code == "SAVE20":
            return self.calculator.calculate_percentage(subtotal, Decimal('20'))
        elif promo_code == "FLAT50":
            return min(Decimal('50'), subtotal)
        
        return Decimal('0')
    
    def calculate_tax(
        self,
        taxable_amount: Decimal,
        state: str
    ) -> Decimal:
        """Calculate sales tax based on state."""
        tax_rate = self.TAX_RATES.get(state, self.TAX_RATES["DEFAULT"])
        return self.calculator.calculate_tax(taxable_amount, tax_rate)
    
    def calculate_taxable_amount(
        self,
        items: List[LineItem],
        shipping_cost: Decimal,
        state: str
    ) -> Decimal:
        """
        Calculate the taxable amount based on state-specific rules.
        
        State rules:
        - CA, NY, TX: Shipping is taxable
        - Other states: Shipping is NOT taxable
        """
        taxable_items = sum(
            item.total for item in items 
            if item.product.is_taxable
        )
        
        shipping_taxable_states = {"CA", "NY", "TX"}
        
        is_shipping_taxable = state not in shipping_taxable_states
        
        if is_shipping_taxable:
            taxable_amount = taxable_items + shipping_cost
        else:
            taxable_amount = taxable_items
        
        return taxable_amount
    
    def calculate_loyalty_points(
        self,
        subtotal: Decimal,
        customer: Customer
    ) -> int:
        """
        Calculate loyalty points earned from this order.
        Points = subtotal * tier_multiplier
        """
        tier_multipliers = {
            CustomerTier.BRONZE: Decimal('1'),
            CustomerTier.SILVER: Decimal('1.5'),
            CustomerTier.GOLD: Decimal('2'),
            CustomerTier.PLATINUM: Decimal('3')
        }
        
        multiplier = tier_multipliers.get(customer.tier, Decimal('1'))
        points_rate = Decimal('1')
        
        return self.calculator.calculate_points_earned(subtotal, points_rate, multiplier)
    
    def calculate_order_total(
        self,
        order: Order
    ) -> Dict[str, Decimal]:
        """
        Calculate complete order breakdown.
        Returns all components and final total.
        """
        subtotal = self.calculate_subtotal(order.items)
        
        tier_discount = self.calculate_tier_discount(subtotal, order.customer)
        bulk_discount = self.calculate_bulk_discount(order.items)
        promo_discount = self.calculate_promo_discount(subtotal, order.promo_code)
        
        total_discount = tier_discount + bulk_discount + promo_discount
        
        discounted_subtotal = subtotal - total_discount
        
        shipping_cost = self.calculate_shipping_cost(
            order.items,
            order.shipping_info.method,
            order.shipping_info.address
        )
        
        state = order.shipping_info.address.state
        taxable_amount = self.calculate_taxable_amount(
            order.items, shipping_cost, state
        )
        taxable_after_discount = taxable_amount * (discounted_subtotal / subtotal) if subtotal > 0 else Decimal('0')
        
        tax = self.calculate_tax(taxable_after_discount, state)
        
        total = discounted_subtotal + shipping_cost + tax
        
        return {
            'subtotal': subtotal,
            'tier_discount': tier_discount,
            'bulk_discount': bulk_discount,
            'promo_discount': promo_discount,
            'total_discount': total_discount,
            'shipping': shipping_cost,
            'tax': tax,
            'total': self.calculator.round_currency(total)
        }
    
    def calculate_refund_amount(
        self,
        order: Order,
        refund_items: List[Tuple[str, int]],
        refund_shipping: bool = False
    ) -> Decimal:
        """
        Calculate refund amount for specific items.
        refund_items: List of (sku, quantity) tuples
        """
        refund_total = Decimal('0')
        
        item_dict = {item.product.sku: item for item in order.items}
        
        for sku, qty in refund_items:
            if sku in item_dict:
                item = item_dict[sku]
                unit_total = item.total / item.quantity
                refund_total += unit_total * qty
        
        order_breakdown = self.calculate_order_total(order)
        subtotal = order_breakdown['subtotal']
        
        if subtotal > 0:
            discount_ratio = order_breakdown['total_discount'] / subtotal
            refund_total = refund_total * (1 - discount_ratio)
        
        if refund_shipping:
            refund_total += order_breakdown['shipping']
        
        return self.calculator.round_currency(refund_total)
    
    def calculate_installment_options(
        self,
        total: Decimal,
        num_installments: int = 4
    ) -> Dict[str, Decimal]:
        """
        Calculate installment payment options.
        Interest-free for orders over $200.
        """
        if total >= Decimal('200'):
            interest_rate = Decimal('0')
        else:
            interest_rate = Decimal('5')
        
        return self.calculator.calculate_installment_plan(
            total, num_installments, interest_rate
        )
    
    def validate_order(self, order: Order) -> Tuple[bool, List[str]]:
        """
        Validate order for processing.
        Returns (is_valid, list_of_errors).
        """
        errors = []
        
        if not order.items:
            errors.append("Order must have at least one item")
        
        for item in order.items:
            if item.quantity <= 0:
                errors.append(f"Invalid quantity for {item.product.sku}")
            if item.unit_price <= 0:
                errors.append(f"Invalid price for {item.product.sku}")
        
        if order.customer.credit_limit is not None:
            breakdown = self.calculate_order_total(order)
            if breakdown['total'] > order.customer.credit_limit:
                errors.append("Order exceeds customer credit limit")
        
        if not order.shipping_info.address.street:
            errors.append("Shipping address is incomplete")
        
        if order.payment_info.amount <= 0:
            errors.append("Payment amount must be positive")
        
        return len(errors) == 0, errors


def create_sample_order() -> Order:
    """Create a sample order for testing."""
    products = [
        Product("LAPTOP-001", "Gaming Laptop", Decimal('1299.99'), Decimal('2.5'), "Electronics"),
        Product("MOUSE-001", "Wireless Mouse", Decimal('49.99'), Decimal('0.15'), "Electronics"),
        Product("KEYBOARD-001", "Mechanical Keyboard", Decimal('149.99'), Decimal('0.8'), "Electronics"),
        Product("MONITOR-001", "27-inch Monitor", Decimal('399.99'), Decimal('5.2'), "Electronics"),
        Product("HEADSET-001", "Gaming Headset", Decimal('79.99'), Decimal('0.3'), "Electronics"),
    ]
    
    items = [
        LineItem(products[0], 1, products[0].base_price),
        LineItem(products[1], 3, products[1].base_price),
        LineItem(products[2], 2, products[2].base_price),
        LineItem(products[3], 1, products[3].base_price),
        LineItem(products[4], 1, products[4].base_price),
    ]
    
    customer = Customer(
        customer_id="CUST-001",
        name="John Doe",
        email="john.doe@example.com",
        tier=CustomerTier.GOLD,
        registration_date=date(2023, 1, 15),
        addresses=[
            Address("123 Main St", "Los Angeles", "CA", "90001", "US", True)
        ],
        loyalty_points=2500,
        credit_limit=Decimal('5000')
    )
    
    shipping_address = customer.addresses[0]
    shipping_info = ShippingInfo(
        method=ShippingMethod.EXPRESS,
        address=shipping_address,
        estimated_days=3,
        actual_cost=Decimal('0')
    )
    
    payment_info = PaymentInfo(
        method=PaymentMethod.CREDIT_CARD,
        amount=Decimal('0'),
        is_verified=True
    )
    
    order = Order(
        order_id="ORD-2024-001",
        customer=customer,
        items=items,
        shipping_info=shipping_info,
        payment_info=payment_info,
        promo_code="SAVE10"
    )
    
    return order


def main():
    """Main entry point for order processing test."""
    order = create_sample_order()
    calculator = OrderCalculator()
    
    breakdown = calculator.calculate_order_total(order)
    
    expected_total = Decimal('1958.34')
    
    if breakdown['total'] == expected_total:
        print(f"BEHAVIOR_OK total={breakdown['total']}")
    else:
        print(f"BEHAVIOR_BAD total={breakdown['total']} expected={expected_total}")
        print("Breakdown:")
        for key, value in breakdown.items():
            print(f"  {key}: {value}")


if __name__ == "__main__":
    main()
