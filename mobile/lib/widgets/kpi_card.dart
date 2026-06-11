import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../core/constants/app_colors.dart';

class KpiCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final dynamic value;
  final bool isCurrency;
  final Color? iconColor;
  final Color? iconBg;
  final double? delta;

  const KpiCard({
    super.key,
    required this.icon,
    required this.label,
    required this.value,
    this.isCurrency = false,
    this.iconColor,
    this.iconBg,
    this.delta,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final activeIconColor = iconColor ?? AppColors.gold;
    final activeIconBg = iconBg ?? AppColors.gold.withValues(alpha: 0.15);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: isDark
            ? const LinearGradient(
                colors: [AppColors.darkSurface, AppColors.darkSurfaceRaised],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              )
            : null,
        color: isDark ? null : AppColors.lightSurface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? AppColors.darkBorder : AppColors.lightBorder,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: activeIconBg,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(icon, color: activeIconColor, size: 20),
              ),
              if (delta != null)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                  decoration: BoxDecoration(
                    color: delta! >= 0
                        ? AppColors.success.withValues(alpha: 0.15)
                        : AppColors.error.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        delta! >= 0 ? Icons.trending_up : Icons.trending_down,
                        color: delta! >= 0 ? AppColors.success : AppColors.error,
                        size: 12,
                      ),
                      const SizedBox(width: 2),
                      Text(
                        '${delta!.abs().toStringAsFixed(1)}%',
                        style: TextStyle(
                          fontFamily: 'Inter',
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                          color: delta! >= 0 ? AppColors.success : AppColors.error,
                        ),
                      ),
                    ],
                  ),
                ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            _formatValue(),
            style: TextStyle(
              fontFamily: 'Inter',
              fontSize: 20,
              fontWeight: FontWeight.w800,
              color: isDark ? AppColors.textPrimary : AppColors.textLight,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: TextStyle(
              fontFamily: 'Inter',
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: isDark ? AppColors.textSecondary : AppColors.textLightSecondary,
            ),
          ),
        ],
      ),
    );
  }

  String _formatValue() {
    if (value is int) {
      if (isCurrency) {
        return NumberFormat.currency(locale: 'fr_CM', symbol: 'FCFA', decimalDigits: 0)
            .format(value);
      }
      return NumberFormat.compact(locale: 'fr').format(value);
    }
    if (value is double) {
      if (isCurrency) {
        return NumberFormat.currency(locale: 'fr_CM', symbol: 'FCFA', decimalDigits: 0)
            .format(value);
      }
      return NumberFormat.compact(locale: 'fr').format(value);
    }
    return value?.toString() ?? '--';
  }
}

