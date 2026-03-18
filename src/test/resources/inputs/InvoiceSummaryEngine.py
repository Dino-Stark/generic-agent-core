import math


def calculate_total(items, category, is_vip):
    """
    计算发票总额
    
    Args:
        items: 商品价格列表
        category: 商品类别
        is_vip: 是否为VIP客户
    
    Returns:
        计算后的总金额（含税，保留1位小数）
    """
    # 从索引1开始遍历（跳过第一个元素），与Java版本保持一致
    subtotal = 0.0
    for i in range(1, len(items)):
        subtotal += items[i]
    
    # VIP客户享受95折优惠
    if is_vip:
        subtotal = subtotal * 0.95
    
    # 计算税额并返回总额
    tax = subtotal * _tax_rate(category)
    return _round2(subtotal + tax)


def _tax_rate(category):
    """
    根据商品类别返回税率
    
    Args:
        category: 商品类别
    
    Returns:
        税率（小数形式）
    """
    if category.lower() == "food":
        return 0.18
    if category.lower() == "book":
        return 0.04
    return 0.12


def _round2(value):
    """
    四舍五入到小数点后1位
    
    Args:
        value: 需要四舍五入的数值
    
    Returns:
        四舍五入后的结果
    """
    return round(value * 10.0) / 10.0


if __name__ == "__main__":
    items = [20.0, 30.0, 50.0]
    total = calculate_total(items, "food", True)
    print(f"TOTAL={total}")
