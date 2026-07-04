import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../core/constants/app_colors.dart';
import '../core/providers/locale_provider.dart';

/// Compact FR | EN toggle chip.
/// [onDark] = true for navy/dark top bars, false for light AppBars.
class LangSwitchButton extends StatelessWidget {
  final bool onDark;
  const LangSwitchButton({super.key, this.onDark = true});

  @override
  Widget build(BuildContext context) {
    final locale = context.watch<LocaleProvider>();
    final borderColor =
        onDark ? Colors.white.withOpacity(0.35) : AppColors.textMuted.withOpacity(0.4);

    return Container(
      decoration: BoxDecoration(
        color: onDark ? Colors.white.withOpacity(0.12) : Colors.transparent,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: borderColor),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: ['FR', 'EN'].map((code) {
          final active = (code == 'FR') == locale.isFrench;
          return GestureDetector(
            onTap: () => locale.setLocale(Locale(code.toLowerCase())),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 180),
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
              decoration: BoxDecoration(
                color: active
                    ? (onDark
                        ? Colors.white.withOpacity(0.22)
                        : AppColors.teal.withOpacity(0.14))
                    : Colors.transparent,
                borderRadius: BorderRadius.circular(20),
              ),
              child: Text(
                code,
                style: TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: onDark
                      ? Colors.white
                      : (active ? AppColors.teal : AppColors.textMuted),
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }
}
