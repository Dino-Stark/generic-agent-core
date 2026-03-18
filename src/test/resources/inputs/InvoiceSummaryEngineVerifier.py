import math
from InvoiceSummaryEngine import calculate_total


def approx(value, expected):
    """
    判断两个浮点数是否近似相等
    
    Args:
        value: 实际值
        expected: 期望值
    
    Returns:
        如果两值之差小于0.0001则返回True，否则返回False
    """
    return abs(value - expected) < 0.0001


if __name__ == "__main__":
    # 测试用例A：food类别，VIP客户
    case_a = calculate_total([20.0, 30.0, 50.0], "food", True)
    # 测试用例B：book类别，非VIP客户
    case_b = calculate_total([10.0, 40.0], "book", False)
    
    # 验证结果
    ok_a = approx(case_a, 102.6)
    ok_b = approx(case_b, 52.0)
    
    if ok_a and ok_b:
        print(f"BEHAVIOR_OK caseA={case_a} caseB={case_b}")
    else:
        print(f"BEHAVIOR_FAIL caseA={case_a} caseB={case_b}")
