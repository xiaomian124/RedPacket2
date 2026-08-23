package sandtechnology.redpacket.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 精确计算工具类
 */
public class OperatorHelper {
    private static final MathContext mathContext = new MathContext(7, RoundingMode.HALF_DOWN);

    private OperatorHelper() {
    }

    /**
     * 将double类型数字转化为两位小数
     *
     * @param number 要转化的数
     * @return 转化后的两位小数
     */
    public static double toTwoPrecision(double number) {
        return multiply((int) (number * 100), 0.01);
    }

    public static double divide(double number1, double number2) {
        return (BigDecimal.valueOf(number1).divide(BigDecimal.valueOf(number2), mathContext)).doubleValue();
    }

    public static double subtract(double number1, double number2) {
        return (BigDecimal.valueOf(number1).subtract(BigDecimal.valueOf(number2), mathContext)).doubleValue();
    }

    public static double multiply(double number1, double number2) {
        return (BigDecimal.valueOf(number1).multiply(BigDecimal.valueOf(number2), mathContext)).doubleValue();
    }

    public static double add(double number1, double number2) {
        return (BigDecimal.valueOf(number1).add(BigDecimal.valueOf(number2), mathContext)).doubleValue();
    }


}
